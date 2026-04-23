package company.luminapoll.core.network

import android.content.Context
import company.luminapoll.core.utils.NsdHelper
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.response.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class KtorLocalServer(context: Context) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nsdHelper = NsdHelper(context)
    
    private val _pollState = MutableStateFlow<Poll?>(null)
    val pollState = kotlinx.coroutines.flow.asStateFlow(_pollState)
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

    fun start(poll: Poll) {
        _pollState.value = poll
        
        server = embeddedServer(CIO, port = poll.port, host = "0.0.0.0") {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(ContentNegotiation) {
                json()
            }
            
            routing {
                get("/poll") {
                    checkPollExpiration()
                    _pollState.value?.let { call.respond(it) } ?: call.respondText("No poll active")
                }
                
                webSocket("/poll-ws") {
                    checkPollExpiration()
                    val currentPoll = _pollState.value
                    if (currentPoll == null || currentPoll.status == PollStatus.ENDED) {
                        sendSerialized(PollMessage.Error("This poll has already ended"))
                        close(CloseReason(CloseReason.Codes.NORMAL, "Poll ended"))
                        return@webSocket
                    }

                    if (currentPoll.participantCount >= currentPoll.maxParticipants) {
                        sendSerialized(PollMessage.Error("This poll is full"))
                        close(CloseReason(CloseReason.Codes.NORMAL, "Poll full"))
                        return@webSocket
                    }

                    sessions.add(this)
                    _pollState.update { it?.copy(participantCount = it.participantCount + 1) }
                    
                    try {
                        _pollState.value?.let { 
                            sendSerialized(PollMessage.Update(it))
                        }
                        
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = receiveDeserialized<PollMessage>()
                                checkPollExpiration()
                                handleMessage(message)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        sessions.remove(this)
                        _pollState.update { it?.copy(participantCount = (it.participantCount - 1).coerceAtLeast(0)) }
                    }
                }
            }
        }.start(wait = false)
        
        nsdHelper.registerService(poll.port, poll.code)
        
        scope.launch {
            _pollState.collect { updatedPoll ->
                updatedPoll?.let { broadcastUpdate(it) }
            }
        }

        // Automatic end timer
        scope.launch {
            while (isActive) {
                delay(5000)
                checkPollExpiration()
            }
        }
    }

    private fun checkPollExpiration() {
        val current = _pollState.value ?: return
        if (current.status == PollStatus.ACTIVE && System.currentTimeMillis() > current.endTimeMillis) {
            _pollState.update { it?.copy(status = PollStatus.ENDED) }
        }
    }

    private suspend fun handleMessage(message: PollMessage?) {
        when (message) {
            is PollMessage.Vote -> {
                _pollState.update { currentPoll ->
                    if (currentPoll != null && 
                        currentPoll.status == PollStatus.ACTIVE && 
                        !currentPoll.votedUserIds.contains(message.voterId)) {
                        
                        val updatedOptions = currentPoll.options.map { option ->
                            if (option.id == message.optionId) {
                                option.copy(votes = option.votes + 1)
                            } else option
                        }
                        
                        val updatedVoters = currentPoll.votedUserIds.toMutableSet().apply {
                            add(message.voterId)
                        }
                        
                        currentPoll.copy(
                            options = updatedOptions,
                            votedUserIds = updatedVoters
                        )
                    } else {
                        currentPoll
                    }
                }
            }
            else -> {}
        }
    }

    private suspend fun broadcastUpdate(poll: Poll) {
        val message = PollMessage.Update(poll)
        sessions.forEach { session ->
            try {
                session.sendSerialized(message)
            } catch (e: Exception) {
                // Session might be closed
            }
        }
    }

    fun stop() {
        nsdHelper.unregisterService()
        _pollState.update { it?.copy(status = PollStatus.ENDED) }
        server?.stop(1000, 2000)
        scope.cancel()
    }
}
