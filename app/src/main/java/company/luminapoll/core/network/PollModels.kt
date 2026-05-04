package company.luminapoll.core.network

import kotlinx.serialization.Serializable

@Serializable
enum class PollStatus {
    ACTIVE, FULL, ENDED
}

@Serializable
data class Poll(
    val id: String,
    val title: String,
    val code: String,
    val question: String,
    val options: List<PollOption>,
    val hostIp: String,
    val hostId: String = "",
    val hostName: String = "Unknown Host",
    val port: Int = 8080,
    var participantCount: Int = 0,
    var status: PollStatus = PollStatus.ACTIVE,
    var maxParticipants: Int = 50,
    var durationMinutes: Int = 5,
    var endTimeMillis: Long = 0,
    val votedUserIds: MutableSet<String> = mutableSetOf()
)

@Serializable
data class PollOption(
    val id: Int,
    val text: String,
    var votes: Int = 0
)

@Serializable
sealed class PollMessage {
    @Serializable
    data class Join(val participantName: String) : PollMessage()
    
    @Serializable
    data class Vote(val optionId: Int, val voterId: String) : PollMessage()
    
    @Serializable
    data class Update(val poll: Poll) : PollMessage()
    
    @Serializable
    data class Error(val message: String) : PollMessage()
}
