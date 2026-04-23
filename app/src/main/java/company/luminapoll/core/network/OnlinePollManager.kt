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

    fun joinPoll(code: String, onResult: (Poll?, String?) -> Unit) {
        pollsCollection.document(code).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val poll = doc.toObject(Poll::class.java)
                if (poll != null) {
                    if (poll.status == PollStatus.ENDED || System.currentTimeMillis() > poll.endTimeMillis) {
                        onResult(null, "Poll has ended")
                    } else if (poll.participantCount >= poll.maxParticipants) {
                        onResult(null, "Poll is full")
                    } else {
                        pollsCollection.document(code).update("participantCount", poll.participantCount + 1)
                        observePoll(code)
                        onResult(poll, null)
                    }
                } else {
                    onResult(null, "Poll data corrupted")
                }
            } else {
                onResult(null, "Poll not found")
            }
        }.addOnFailureListener {
            onResult(null, it.message)
        }
    }

    private fun observePoll(code: String) {
        pollListener?.remove()
        pollListener = pollsCollection.document(code).addSnapshotListener { snapshot, error ->
            if (snapshot != null && snapshot.exists()) {
                val poll = snapshot.toObject(Poll::class.java)
                if (poll != null && poll.status == PollStatus.ACTIVE && System.currentTimeMillis() > poll.endTimeMillis) {
                    // Locally ended
                    _currentPoll.value = poll.copy(status = PollStatus.ENDED)
                } else {
                    _currentPoll.value = poll
                }
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
                
                val updatedVoters = freshPoll.votedUserIds.toMutableSet().apply { add(voterId) }
                
                transaction.update(pollsCollection.document(poll.code), "options", updatedOptions)
                transaction.update(pollsCollection.document(poll.code), "votedUserIds", updatedVoters.toList())
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun stopPollEarly(code: String): Boolean {
        return try {
            pollsCollection.document(code).update("status", PollStatus.ENDED).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun leavePoll() {
        val poll = _currentPoll.value ?: return
        pollsCollection.document(poll.code).update("participantCount", (poll.participantCount - 1).coerceAtLeast(0))
        pollListener?.remove()
        pollListener = null
        _currentPoll.value = null
    }
}
