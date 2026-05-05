package company.luminapoll.core.network

import kotlinx.serialization.Serializable

@Serializable
enum class PollStatus {
    ACTIVE, FULL, ENDED
}

@Serializable
data class Poll(
    val id: String = "",
    val title: String = "",
    val code: String = "",
    val question: String = "",
    val options: List<PollOption> = emptyList(),
    val hostIp: String = "",
    val hostId: String = "",
    val hostName: String = "Unknown Host",
    val port: Int = 8080,
    var participantCount: Int = 0,
    var status: PollStatus = PollStatus.ACTIVE,
    var maxParticipants: Int = 50,
    var durationMinutes: Int = 5,
    var endTimeMillis: Long = 0,
    var resultExpiryMillis: Long = 0,
    val votedUserIds: List<String> = emptyList(),
    val participantIds: List<String> = emptyList(),
    val isOnline: Boolean = false
) {
    companion object {
        const val EXPIRY_LOCAL_MS = 60 * 60 * 1000L // 1 hour
        const val EXPIRY_ONLINE_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun calculateResultExpiry(currentTime: Long): Long {
        return currentTime + if (isOnline) EXPIRY_ONLINE_MS else EXPIRY_LOCAL_MS
    }
}

@Serializable
data class PollOption(
    val id: Int = 0,
    val text: String = "",
    var votes: Int = 0
)

@Serializable
sealed class PollMessage {
    @Serializable
    data class Join(val participantName: String, val deviceId: String) : PollMessage()
    
    @Serializable
    data class Vote(val optionId: Int, val voterId: String) : PollMessage()
    
    @Serializable
    data class Update(val poll: Poll) : PollMessage()
    
    @Serializable
    data class Error(val message: String) : PollMessage()
}
