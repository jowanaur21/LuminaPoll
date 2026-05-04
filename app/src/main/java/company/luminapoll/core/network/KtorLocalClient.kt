package company.luminapoll.core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class KtorLocalClient {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(ContentNegotiation) {
            json()
        }
    }

    private val _pollState = MutableStateFlow<Poll?>(null)
    val pollState = _pollState.asStateFlow()

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow = _errorFlow.asSharedFlow()

    private val _voteSuccessFlow = MutableSharedFlow<Boolean>()
    val voteSuccessFlow = _voteSuccessFlow.asSharedFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(host: String, participantName: String, port: Int = 8080) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            try {
                client.webSocket(host = host, port = port, path = "/poll-ws") {
                    session = this
                    // Send Join message immediately
                    sendSerialized<PollMessage>(PollMessage.Join(participantName))
                    try {
                        while (isActive) {
                            when (val message = receiveDeserialized<PollMessage>()) {
                                is PollMessage.Update -> {
                                    _pollState.value = message.poll
                                }
                                is PollMessage.Error -> {
                                    _errorFlow.emit(message.message)
                                    if (message.message.contains("vote", ignoreCase = true)) {
                                        _voteSuccessFlow.emit(false)
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (isActive) e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _errorFlow.emit("Poll not found or connection failed")
                }
            } finally {
                session = null
            }
        }
    }

    suspend fun fetchPollDetails(host: String, port: Int = 8080): Poll? {
        return try {
            client.get("http://$host:$port/poll").body()
        } catch (e: Exception) {
            null
        }
    }

    fun vote(optionId: Int, voterId: String) {
        scope.launch {
            try {
                session?.sendSerialized<PollMessage>(PollMessage.Vote(optionId, voterId))
                // We'll assume success if no error is received, or wait for next Update
                _voteSuccessFlow.emit(true)
            } catch (e: Exception) {
                _errorFlow.emit("Failed to submit vote")
                _voteSuccessFlow.emit(false)
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        scope.launch {
            session?.close()
            session = null
        }
    }
}
