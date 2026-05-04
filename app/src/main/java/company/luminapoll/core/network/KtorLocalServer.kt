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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class KtorLocalServer(
    context: Context,
    private val nsdHelper: NsdHelper = NsdHelper(context)
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _pollState = MutableStateFlow<Poll?>(null)
    val pollState = _pollState.asStateFlow()
    private val sessions = Collections.newSetFromMap(ConcurrentHashMap<DefaultWebSocketServerSession, Boolean>())

    fun parsePoll(json: String): Poll? {
        return try {
            Json.decodeFromString<Poll>(json)
        } catch (e: Exception) {
            null
        }
    }

    fun serializePoll(poll: Poll): String {
        return Json.encodeToString(poll)
    }

    fun start(poll: Poll) {
        stopServer() // Ensure clean start
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        _pollState.value = poll
        
        server = embeddedServer(CIO, port = poll.port, host = "0.0.0.0") {
            configureServerModule()
        }.start(wait = false)
        
        nsdHelper.registerService(poll.port, poll.code)
        
        scope.launch {
            _pollState.collect { updatedPoll ->
                updatedPoll?.let { broadcastUpdate(it) }
            }
        }

        // Automatic end/expiry timer
        scope.launch {
            while (isActive) {
                delay(5000)
                checkPollExpiration()
            }
        }
    }

    private fun Application.configureServerModule() {
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
                this@KtorLocalServer.checkPollExpiration()
                _pollState.value?.let { call.respond(it) } ?: call.respondText("No poll active")
            }
            
            webSocket("/poll-ws") {
                this@KtorLocalServer.checkPollExpiration()
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
                        sendSerialized<PollMessage>(PollMessage.Update(it))
                    }
                    
                    while (true) {
                        val message = receiveDeserialized<PollMessage>()
                        this@KtorLocalServer.checkPollExpiration()
                        this@KtorLocalServer.handleMessage(message)
                    }
                } catch (e: Exception) {
                    // This is expected when connection closes
                } finally {
                    sessions.remove(this)
                    _pollState.update { it?.copy(participantCount = (it.participantCount - 1).coerceAtLeast(0)) }
                }
            }
        }
    }

    private fun checkPollExpiration() {
        val current = _pollState.value ?: return
        val now = System.currentTimeMillis()
        
        // 1. Check if voting phase should end
        if (current.status == PollStatus.ACTIVE && now > current.endTimeMillis) {
            val expiry = current.calculateResultExpiry(now)
            _pollState.update { it?.copy(status = PollStatus.ENDED, resultExpiryMillis = expiry) }
        }
        
        // 2. Check if results have expired
        if (current.status == PollStatus.ENDED && current.resultExpiryMillis > 0 && now > current.resultExpiryMillis) {
            stopServer()
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
                session.sendSerialized<PollMessage>(message)
            } catch (e: Exception) {
                // Session might be closed
            }
        }
    }

    /**
     * Ends the voting phase but keeps the server alive for results viewing.
     */
    fun stop() {
        val now = System.currentTimeMillis()
        val expiry = _pollState.value?.calculateResultExpiry(now) ?: (now + Poll.EXPIRY_LOCAL_MS)
        _pollState.update { it?.copy(status = PollStatus.ENDED, resultExpiryMillis = expiry) }
    }

    /**
     * Completely shuts down the server and network services.
     */
    fun stopServer() {
        nsdHelper.unregisterService()
        _pollState.update { null }
        server?.stop(1000, 2000)
        scope.cancel()
    }
}
