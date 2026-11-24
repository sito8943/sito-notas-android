package dev.sito8943.sitonotas.core.auth

import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.AuthTokenSupplier
import dev.sito8943.sitonotas.core.network.RefreshOutcome
import dev.sito8943.sitonotas.feature.auth.data.AuthApi
import dev.sito8943.sitonotas.feature.auth.data.AuthResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Quien esta usando la app ahora mismo. */
sealed interface SessionState {
    /** Aun no se ha leido el disco: no se sabe si hay sesion. */
    data object Loading : SessionState

    /** Sin sesion. La app funciona igualmente: es local-first. */
    data object Guest : SessionState

    data class Authenticated(val session: AuthSession) : SessionState
}

/**
 * Dueño de la sesion: la lee del disco al arrancar, la guarda cifrada, la
 * refresca y la tira cuando caduca.
 *
 * Implementa `AuthTokenSupplier` para que `RestClient` pueda pedirle el token y
 * el refresco sin que la capa de red conozca nada mas de autenticacion.
 */
class SessionRepository(
    private val store: SessionStore,
    private val api: () -> AuthApi,
    private val scope: CoroutineScope,
) : AuthTokenSupplier {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    /**
     * Single-flight. Una rafaga de peticiones autenticadas (el pull del sync,
     * por ejemplo) puede dar 401 a la vez; hay que refrescar UNA sola vez y que
     * todas esperen a ese refresco. El refresh token rota y se consume al usarlo,
     * asi que dos refrescos en paralelo se invalidarian entre si y provocarian un
     * cierre de sesion falso.
     */
    private var refreshInFlight: Deferred<RefreshOutcome>? = null

    /** Se llama una vez al arrancar el proceso. */
    fun restore() {
        _state.value = when (val result = store.load()) {
            is SessionLoadResult.Loaded -> SessionState.Authenticated(result.session)
            // Missing y Unreadable acaban igual: invitado. La diferencia solo
            // importa para no tratar un descifrado fallido como un error visible.
            SessionLoadResult.Missing, SessionLoadResult.Unreadable -> SessionState.Guest
        }
    }

    /**
     * Si ya se contesto a la pantalla de login. Se lee sincrono al arrancar
     * para decidir el destino inicial del grafo.
     */
    fun loginAnswered(): Boolean = store.loginAnswered()

    /**
     * Eligio seguir sin cuenta. Se recuerda: si no, volveria a ver el login en
     * cada arranque, y ademas cambiar el idioma recrea la Activity y la
     * mandaba de vuelta ahi.
     */
    fun continueAsGuest() {
        store.markLoginAnswered()
        _state.value = SessionState.Guest
    }

    /** Persiste una sesion recien obtenida (login o registro). */
    fun onAuthenticated(response: AuthResponse) {
        val session = AuthSession(
            userId = requireNotNull(response.id) { "La respuesta de auth no trae id" },
            username = response.username.orEmpty(),
            email = response.email.orEmpty(),
            accessToken = response.token,
            refreshToken = response.refreshToken,
            accessTokenExpiresAt = response.accessTokenExpiresAt,
            emailConfirmed = response.emailConfirmed ?: false,
        )
        store.save(session)
        store.markLoginAnswered()
        _state.value = SessionState.Authenticated(session)
    }

    suspend fun signOut() {
        val current = currentSession()
        // El cierre local no depende de que el servidor conteste: si falla la
        // red, la sesion se cierra igual en el dispositivo.
        runCatching { api().signOut(current?.refreshToken) }
        clearSession()
    }

    override suspend fun accessToken(): String? = currentSession()?.accessToken

    override suspend fun refresh(): RefreshOutcome {
        val deferred = refreshMutex.withLock {
            refreshInFlight ?: scope.async { performRefresh() }.also { refreshInFlight = it }
        }
        return try {
            deferred.await()
        } finally {
            refreshMutex.withLock {
                if (refreshInFlight === deferred) refreshInFlight = null
            }
        }
    }

    override suspend fun onSessionExpired() = clearSession()

    /**
     * Un `runCatching { ... }.getOrDefault(false)` aqui era un cierre de sesion
     * por quedarse sin cobertura: se tragaba tambien el `NetworkException` y
     * `RestClient` no tenia forma de distinguirlo de un token rechazado.
     *
     * Solo un NO explicito del servidor tumba la sesion.
     */
    private suspend fun performRefresh(): RefreshOutcome {
        // Sin refresh token no hay nada que canjear, y esperar a tener red no lo
        // va a hacer aparecer.
        val refreshToken = currentSession()?.refreshToken ?: return RefreshOutcome.REJECTED
        val response = try {
            api().refresh(refreshToken)
        } catch (error: ApiException) {
            // 4xx es el backend diciendo que el token no vale. 5xx es el backend
            // roto, que no dice nada del token.
            return if (error.status in CLIENT_ERRORS) {
                RefreshOutcome.REJECTED
            } else {
                RefreshOutcome.UNREACHABLE
            }
        } catch (error: Exception) {
            // Sin red, timeout o una respuesta que no se pudo leer. Nada de esto
            // demuestra que la sesion haya caducado.
            return RefreshOutcome.UNREACHABLE
        }

        val previous = currentSession() ?: return RefreshOutcome.REJECTED
        val updated = previous.copy(
            accessToken = response.token,
            // El backend rota el refresh token; si no manda uno nuevo, se
            // conserva el que habia en vez de dejarlo a nulo.
            refreshToken = response.refreshToken ?: previous.refreshToken,
            accessTokenExpiresAt = response.accessTokenExpiresAt
                ?: previous.accessTokenExpiresAt,
        )
        store.save(updated)
        _state.value = SessionState.Authenticated(updated)
        return RefreshOutcome.REFRESHED
    }

    /**
     * Cerrar sesion **si** devuelve al login: es lo que se acaba de pedir. La
     * marca se borra con la sesion, a diferencia de lo que pasa al elegir
     * seguir sin cuenta.
     */
    private fun clearSession() {
        store.clear()
        _state.value = SessionState.Guest
    }

    private fun currentSession(): AuthSession? =
        (_state.value as? SessionState.Authenticated)?.session

    private companion object {
        val CLIENT_ERRORS = 400..499
    }
}
