package company.luminapoll.core.network

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class OnlinePollManager {
    private val db = FirebaseFirestore.getInstance()
    private val pollsCollection = db.collection("polls")
    
    private val _currentPoll = MutableStateFlow<Poll?>(null)
    val currentPoll = _currentPoll.asStateFlow()
    
    private var pollListener: ListenerRegistration? = null

    suspend fun createPoll(poll: Poll): Boolean {
        return try {
            pollsCollection.document(poll.code).set(poll).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun joinPoll(code: String, userId: String, onResult: (Poll?, String?) -> Unit) {
        db.runTransaction { transaction ->
            val docRef = pollsCollection.document(code)
            val snapshot = transaction.get(docRef)
            
            if (!snapshot.exists()) {
                throw Exception("Poll not found")
            }
            
            val poll = snapshot.toObject(Poll::class.java) ?: throw Exception("Poll data corrupted")
            val now = System.currentTimeMillis()
            
            if (poll.status == PollStatus.ENDED && poll.resultExpiryMillis > 0 && now > poll.resultExpiryMillis) {
                throw Exception("Poll results have expired and been removed")
            }
            
            // If user is host, they don't count as a participant, but they can still observe
            if (poll.hostId == userId) {
                return@runTransaction poll
            }

            // If already a participant, just return the poll
            if (poll.participantIds.contains(userId)) {
                return@runTransaction poll
            }
            
            if (poll.status == PollStatus.ACTIVE && now > poll.endTimeMillis) {
                throw Exception("Poll has ended")
            }
            
            if (poll.participantCount >= poll.maxParticipants) {
                throw Exception("Poll is full")
            }
            
            val updatedParticipants = poll.participantIds.toMutableList().apply { add(userId) }
            val newCount = updatedParticipants.size
            
            transaction.update(docRef, "participantIds", updatedParticipants)
            transaction.update(docRef, "participantCount", newCount)
            
            poll.copy(participantIds = updatedParticipants, participantCount = newCount)
        }.addOnSuccessListener { poll ->
            startObserving(code)
            onResult(poll, null)
        }.addOnFailureListener { e ->
            onResult(null, e.message)
        }
    }

    fun startObserving(code: String) {
        pollListener?.remove()
        pollListener = pollsCollection.document(code).addSnapshotListener { snapshot, error ->
            if (snapshot != null && snapshot.exists()) {
                val poll = snapshot.toObject(Poll::class.java)
                if (poll != null) {
                    val now = System.currentTimeMillis()
                    if (poll.status == PollStatus.ACTIVE && now > poll.endTimeMillis) {
                        val expiry = poll.calculateResultExpiry(now)
                        _currentPoll.value = poll.copy(status = PollStatus.ENDED, resultExpiryMillis = expiry)
                        // Update Firestore with ENDED status and expiry
                        pollsCollection.document(code).update(
                            "status", PollStatus.ENDED,
                            "resultExpiryMillis", expiry
                        )
                    } else if (poll.status == PollStatus.ENDED && poll.resultExpiryMillis > 0 && now > poll.resultExpiryMillis) {
                        // Results expired, remove document
                        pollsCollection.document(code).delete()
                        _currentPoll.value = null
                        pollListener?.remove()
                    } else {
                        _currentPoll.value = poll
                    }
                }
            } else {
                _currentPoll.value = null
                pollListener?.remove()
            }
        }
    }

    suspend fun vote(optionId: Int, voterId: String): Boolean {
        val poll = _currentPoll.value ?: return false
        if (poll.votedUserIds.contains(voterId)) return false
        if (poll.status == PollStatus.ENDED || System.currentTimeMillis() > poll.endTimeMillis) return false
        
        return try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(pollsCollection.document(poll.code))
                val freshPoll = snapshot.toObject(Poll::class.java) ?: return@runTransaction
                
                if (freshPoll.votedUserIds.contains(voterId)) return@runTransaction
                if (freshPoll.status == PollStatus.ENDED || System.currentTimeMillis() > freshPoll.endTimeMillis) return@runTransaction
                
                val updatedOptions = freshPoll.options.map { option ->
                    if (option.id == optionId) option.copy(votes = option.votes + 1) else option
                }
                
                val updatedVoters = freshPoll.votedUserIds.toMutableList().apply { add(voterId) }
                
                transaction.update(pollsCollection.document(poll.code), "options", updatedOptions)
                transaction.update(pollsCollection.document(poll.code), "votedUserIds", updatedVoters)
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun stopPollEarly(code: String): Boolean {
        val poll = _currentPoll.value ?: return false
        val now = System.currentTimeMillis()
        val expiry = poll.calculateResultExpiry(now)
        return try {
            pollsCollection.document(code).update(
                "status", PollStatus.ENDED,
                "resultExpiryMillis", expiry
            ).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun leavePoll() {
        val poll = _currentPoll.value ?: return
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        
        // Only joiners decrement the participant count
        if (poll.hostId != currentUserId) {
            pollsCollection.document(poll.code).update("participantCount", (poll.participantCount - 1).coerceAtLeast(0))
        }
        
        pollListener?.remove()
        pollListener = null
        _currentPoll.value = null
    }
}
