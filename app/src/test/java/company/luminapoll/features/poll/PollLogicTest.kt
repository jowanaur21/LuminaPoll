package company.luminapoll.features.poll

import company.luminapoll.core.network.Poll
import company.luminapoll.core.network.PollStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PollLogicTest {

    @Test
    fun testCentralizedExpiry_Local() {
        val currentTime = 1000000L
        val poll = Poll(isOnline = false)
        
        val expiry = poll.calculateResultExpiry(currentTime)
        
        // Local expiry should be 1 hour
        assertEquals(currentTime + Poll.EXPIRY_LOCAL_MS, expiry)
    }

    @Test
    fun testCentralizedExpiry_Online() {
        val currentTime = 1000000L
        val poll = Poll(isOnline = true)
        
        val expiry = poll.calculateResultExpiry(currentTime)
        
        // Online expiry should be 24 hours
        assertEquals(currentTime + Poll.EXPIRY_ONLINE_MS, expiry)
    }

    @Test
    fun testPollTitleEqualsQuestion() {
        val question = "What is your favorite color?"
        val poll = Poll(
            title = question,
            question = question
        )
        
        assertEquals(poll.title, poll.question)
        assertEquals("What is your favorite color?", poll.title)
    }

    @Test
    fun testUniqueParticipants_Logic() {
        val initialParticipants = listOf("device1", "device2")
        val poll = Poll(participantIds = initialParticipants, participantCount = 2)
        
        val newDeviceId = "device3"
        val updatedParticipants = if (!poll.participantIds.contains(newDeviceId)) {
            poll.participantIds.toMutableList().apply { add(newDeviceId) }
        } else {
            poll.participantIds
        }
        
        assertEquals(3, updatedParticipants.size)
        
        val reconnectDeviceId = "device1"
        val sameParticipants = if (!updatedParticipants.contains(reconnectDeviceId)) {
            updatedParticipants.toMutableList().apply { add(reconnectDeviceId) }
        } else {
            updatedParticipants
        }
        
        // Should still be 3
        assertEquals(3, sameParticipants.size)
    }
}
