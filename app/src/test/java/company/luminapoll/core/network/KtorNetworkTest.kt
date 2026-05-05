package company.luminapoll.core.network

import android.content.Context
import company.luminapoll.core.utils.NsdHelper
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class KtorNetworkTest {

    @Test
    fun testServerClientCommunication() = runBlocking {
        println("Starting testServerClientCommunication")
        // Mock dependencies
        val mockContext = mockk<Context>(relaxed = true)
        val mockNsdHelper = mockk<NsdHelper>(relaxed = true)

        val server = KtorLocalServer(mockContext, mockNsdHelper)
        val client = KtorLocalClient()

        val poll = Poll(
            id = UUID.randomUUID().toString(),
            title = "Test Poll",
            code = "TEST",
            question = "Is this working?",
            options = listOf(
                PollOption(0, "Yes"),
                PollOption(1, "No")
            ),
            hostIp = "127.0.0.1",
            port = 9090, // Use a different port for testing
            endTimeMillis = System.currentTimeMillis() + 60000
        )

        try {
            println("Starting server...")
            server.start(poll)
            delay(2000) // Give server time to start

            println("Fetching poll details...")
            val details = client.fetchPollDetails("127.0.0.1", 9090)
            assertNotNull("Poll details should not be null", details)
            assertEquals("Test Poll", details?.title)

            println("Connecting WebSocket for User 1...")
            // The connect method now takes deviceId
            client.connect("127.0.0.1", "TestUser1", "device-1", 9090)
            
            println("Waiting for initial update...")
            var state1: Poll? = null
            for (i in 1..50) {
                state1 = client.pollState.value
                if (state1 != null && state1.participantCount == 1) break
                delay(100)
            }
            
            assertEquals(1, state1?.participantCount)
            assertEquals("device-1", state1?.participantIds?.get(0))

            println("Connecting User 1 again (Reconnection)...")
            client.connect("127.0.0.1", "TestUser1", "device-1", 9090)
            delay(1000)
            
            assertEquals(1, client.pollState.value?.participantCount) // Should NOT increase

            println("Connecting User 2...")
            val client2 = KtorLocalClient()
            client2.connect("127.0.0.1", "TestUser2", "device-2", 9090)
            
            var state2: Poll? = null
            for (i in 1..50) {
                state2 = client2.pollState.value
                if (state2 != null && state2.participantCount == 2) break
                delay(100)
            }
            assertEquals(2, state2?.participantCount)

            println("Submitting vote...")
            client.vote(0, "device-1")
            
            println("Waiting for vote broadcast...")
            var updatedState: Poll? = null
            for (i in 1..50) {
                updatedState = client.pollState.value
                if (updatedState?.options?.get(0)?.votes == 1) break
                delay(100)
            }
            
            println("Updated state received: votes=${updatedState?.options?.get(0)?.votes}")
            assertEquals(1, updatedState?.options?.get(0)?.votes)

        } finally {
            println("Cleaning up...")
            client.disconnect()
            server.stopServer()
        }
    }
}
