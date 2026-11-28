package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.core.network.AuthTokenSupplier
import dev.sito8943.sitonotas.core.network.RefreshOutcome
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Cambios en tiempo real: STOMP sobre el WebSocket `/ws` del servidor de notas
 * (`WebSocketConfig`), como `SocketClient` en la web.
 *
 * Solo conecta con sesion y con la app en primer plano. No trae datos: cada
 * evento de `/user/queue/notes` (guardado de una nota, papelera, compartir)
 * pide una sincronizacion, agrupando rafagas ([debounceMillis]). Los de
 * `/user/queue/sync-status` no se escuchan: los provoca la propia
 * sincronizacion y serian un bucle.
 *
 * El token va en la cabecera `Authorization` del frame CONNECT
 * (`StompAuthChannelInterceptor`). Si el servidor lo rechaza antes de
 * CONNECTED se refresca una vez; un refresco rechazado cierra la sesion, como
 * en `RestClient`. Caidas: reintento con espera creciente (1 s .. 60 s).
 */
class RealtimeClient(
    private val socketUrl: String,
    private val tokens: AuthTokenSupplier,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
    private val onRemoteChange: () -> Unit,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val http: OkHttpClient = defaultClient(),
) {

    private val foreground = MutableStateFlow(false)

    private val changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** La Activity: `onStart` -> true, `onStop` -> false. */
    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            changes.debounce(debounceMillis).collect { onRemoteChange() }
        }
        scope.launch {
            // Girar la pantalla para y arranca la Activity: no vale la pena
            // cortar el socket por eso.
            val visible = foreground.transformLatest { value ->
                if (!value) delay(BACKGROUND_GRACE_MILLIS)
                emit(value)
            }
            combine(visible, sessionState) { fg, session ->
                (session as? SessionState.Authenticated)?.session?.userId?.takeIf { fg }
            }
                .distinctUntilChanged()
                .collectLatest { userId -> if (userId != null) keepConnected() }
        }
    }

    /** Conecta y reconecta hasta que lo cancelen (fondo o fin de sesion). */
    private suspend fun keepConnected() {
        var backoff = MIN_BACKOFF_MILLIS
        var refreshed = false
        while (true) {
            when (connectOnce()) {
                Outcome.DROPPED_AFTER_CONNECT -> {
                    backoff = MIN_BACKOFF_MILLIS
                    refreshed = false
                }
                Outcome.REJECTED -> if (!refreshed) {
                    refreshed = true
                    when (tokens.refresh()) {
                        RefreshOutcome.REFRESHED -> continue
                        RefreshOutcome.REJECTED -> {
                            tokens.onSessionExpired()
                            return
                        }
                        RefreshOutcome.UNREACHABLE -> Unit
                    }
                }
                Outcome.FAILED -> Unit
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
    }

    private enum class Outcome { DROPPED_AFTER_CONNECT, REJECTED, FAILED }

    private suspend fun connectOnce(): Outcome {
        val token = tokens.accessToken() ?: return Outcome.REJECTED
        val done = CompletableDeferred<Outcome>()
        val request = Request.Builder().url(socketUrl).build()
        val connected = AtomicBoolean(false)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    StompFrame(
                        StompFrame.CONNECT,
                        linkedMapOf(
                            "accept-version" to "1.2",
                            "host" to request.url.host,
                            "heart-beat" to "0,0",
                            "Authorization" to "Bearer $token",
                        ),
                    ).encode(),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = StompFrame.parse(text) ?: return
                when (frame.command) {
                    StompFrame.CONNECTED -> {
                        connected.set(true)
                        SUBSCRIPTIONS.forEachIndexed { index, destination ->
                            webSocket.send(
                                StompFrame(
                                    StompFrame.SUBSCRIBE,
                                    linkedMapOf("id" to "sub-$index", "destination" to destination),
                                ).encode(),
                            )
                        }
                    }
                    StompFrame.MESSAGE -> if (frame.headers["destination"] in SUBSCRIPTIONS) changes.tryEmit(Unit)
                    StompFrame.ERROR -> {
                        done.complete(if (connected.get()) Outcome.DROPPED_AFTER_CONNECT else Outcome.REJECTED)
                        webSocket.close(NORMAL_CLOSURE, null)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.complete(if (connected.get()) Outcome.DROPPED_AFTER_CONNECT else Outcome.FAILED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val rejected = !connected.get() && response?.code == HTTP_UNAUTHORIZED
                done.complete(
                    when {
                        connected.get() -> Outcome.DROPPED_AFTER_CONNECT
                        rejected -> Outcome.REJECTED
                        else -> Outcome.FAILED
                    },
                )
            }
        }

        val socket = http.newWebSocket(request, listener)
        return try {
            done.await()
        } catch (error: CancellationException) {
            socket.send(StompFrame(StompFrame.DISCONNECT).encode())
            socket.close(NORMAL_CLOSURE, null)
            throw error
        }
    }

    companion object {
        /** Lo que escucha; los eventos de `/user/queue/sync-status` no. */
        val SUBSCRIPTIONS = listOf("/user/queue/notes")

        const val DEBOUNCE_MILLIS = 1_000L
        const val MIN_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
        private const val BACKGROUND_GRACE_MILLIS = 5_000L
        private const val NORMAL_CLOSURE = 1000
        private const val HTTP_UNAUTHORIZED = 401

        /** `https://api/x` -> `wss://api/x/ws`, como `toSocketUrl` en la web. */
        fun socketUrlOf(apiBaseUrl: String): String {
            val base = apiBaseUrl.trim().trimEnd('/')
            val ws = when {
                base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
                base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7)
                else -> base
            }
            return "$ws/ws"
        }

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            // Mantiene viva la conexion sin latidos STOMP (el broker simple no los manda).
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }
}
