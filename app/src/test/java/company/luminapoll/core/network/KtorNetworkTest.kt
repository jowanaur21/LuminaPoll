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

            println("Connecting WebSocket...")
            client.connect("127.0.0.1", "TestUser", 9090)
            
            println("Waiting for initial update...")
            // Wait up to 5 seconds for state to be non-null
            var initialState: Poll? = null
            for (i in 1..50) {
                initialState = client.pollState.value
                if (initialState != null) break
                delay(100)
            }
            
            assertNotNull("Initial poll state should be received", initialState)
            println("Initial state received: participantCount=${initialState?.participantCount}")
            assertEquals(1, initialState?.participantCount)

            println("Submitting vote...")
            client.vote(0, "voter-1")
            
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
            server.stop()
        }
    }
}
