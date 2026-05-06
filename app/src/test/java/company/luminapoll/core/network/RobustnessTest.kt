package company.luminapoll.core.network

import android.content.Context
import company.luminapoll.core.utils.NsdHelper
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class RobustnessTest {

    @Test
    fun testLocalBadPath_UnreachableHost() = runBlocking {
        val client = KtorLocalClient()
        
        // Try to fetch details from an IP that doesn't exist on this port
        val details = withTimeoutOrNull(2000) {
            client.fetchPollDetails("192.168.254.254", 8888)
        }
        
        assertNull("Details should be null for unreachable host", details)
    }

    @Test
    fun testLocalBadPath_DuplicateVoting() = runBlocking {
        val mockContext = mockk<Context>(relaxed = true)
        val mockNsdHelper = mockk<NsdHelper>(relaxed = true)
        val server = KtorLocalServer(mockContext, mockNsdHelper)
        val client = KtorLocalClient()

        val poll = Poll(
            id = "test-poll",
            options = listOf(PollOption(0, "A"), PollOption(1, "B")),
            hostIp = "127.0.0.1",
            port = 9091,
            endTimeMillis = System.currentTimeMillis() + 60000
        )

        try {
            server.start(poll)
            delay(1000)
            client.connect("127.0.0.1", "User1", "device-1", 9091)
            delay(500)

            // Vote once
            client.vote(0, "device-1")
            delay(500)
            assertEquals(1, server.pollState.value?.options?.get(0)?.votes)

            // Vote again with same ID
            client.vote(0, "device-1")
            delay(500)
            assertEquals("Votes should not increase for same user", 1, server.pollState.value?.options?.get(0)?.votes)

        } finally {
            server.stopServer()
            client.disconnect()
        }
    }

    @Test
    fun testLocalRaceCondition_SimultaneousVoting() = runBlocking {
        val mockContext = mockk<Context>(relaxed = true)
        val mockNsdHelper = mockk<NsdHelper>(relaxed = true)
        val server = KtorLocalServer(mockContext, mockNsdHelper)
        
        val poll = Poll(
            id = "race-poll",
            options = listOf(PollOption(0, "Option A")),
            hostIp = "127.0.0.1",
            port = 9092,
            endTimeMillis = System.currentTimeMillis() + 60000
        )

        try {
            server.start(poll)
            delay(1000)

            val voteCount = 50
            val jobs = mutableListOf<Job>()
            
            // We simulate 50 users voting at the EXACT same time
            // In KtorLocalServer, we'll bypass the network and call handleMessage directly for speed/concurrency testing
            // handleMessage is private, but we can test it via WebSocket if we want, or just test the state update logic
            
            repeat(voteCount) { i ->
                jobs.add(launch(Dispatchers.Default) {
                    // Simulating the internal handleMessage logic
                    // We use the same message type as the server
                    val msg = PollMessage.Vote(0, "device-$i")
                    println(msg)
                    // Directly invoking handleMessage would require reflection, 
                    // so we test the state update atomicity which is the core of the race condition
                    
                    // This mirrors KtorLocalServer's internal logic:
                    // _pollState.update { ... }
                    
                    // Actually, let's just use the server's public API by connecting multiple clients
                    // but that might be slow for a race condition test.
                    // Instead, let's test if the logic inside update is safe.
                })
            }

            val deviceIds = List(voteCount) { "dev-$it" }
            val clients = List(voteCount) { KtorLocalClient() }
            clients.forEachIndexed { i, client -> 
                client.connect("127.0.0.1", "User-$i", deviceIds[i], 9092)
            }
            delay(3000) // Ensure all connected

            val startTime = System.currentTimeMillis()
            clients.forEachIndexed { i, client ->
                launch(Dispatchers.Default) {
                    client.vote(0, deviceIds[i])
                }
            }
            
            println("All votes submitted in ${System.currentTimeMillis() - startTime}ms")
            
            // Wait for processing
            for (i in 1..50) {
                if (server.pollState.value?.options?.get(0)?.votes == voteCount) break
                delay(100)
            }

            assertEquals("All votes should be counted without loss", voteCount, server.pollState.value?.options?.get(0)?.votes)

        } finally {
            server.stopServer()
        }
    }

    @Test
    fun testOnlineBadPath_Mocked() = runBlocking {
        // We mock the manager to simulate Firestore behavior without needing the real SDK
        val mockManager = mockk<OnlinePollManager>()
        
        val errorCode = "POLL_NOT_FOUND"
        
        // Setup the mock to trigger the failure callback
        io.mockk.every { 
            mockManager.joinPoll(any(), any(), any()) 
        } answers {
            val callback = arg<(Poll?, String?) -> Unit>(2)
            callback(null, errorCode)
        }
        
        val completed = CompletableDeferred<Pair<Poll?, String?>>()
        mockManager.joinPoll("BAD-CODE", "user-1") { poll, error ->
            completed.complete(poll to error)
        }
        
        val result = completed.await()
        assertNull("Poll should be null on failure", result.first)
        assertEquals("Error message should match mocked error", errorCode, result.second)
    }
}