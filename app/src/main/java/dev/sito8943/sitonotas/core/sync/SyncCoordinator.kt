package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.core.data.entity.PendingStats
import dev.sito8943.sitonotas.core.network.NetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Estado visible de la sincronizacion. */
sealed interface SyncStatus {
    /** Sin cuenta: todo se queda en el telefono. */
    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    data class UpToDate(val at: Long) : SyncStatus

    /**
     * No se pudo. [offline] separa "no habia red" (se arregla solo) de un
     * rechazo del servidor (no).
     */
    data class Failed(val message: String, val offline: Boolean) : SyncStatus

    /**
     * Entro otra cuenta y hay [pending] cambios sin subir de la anterior: no se
     * sincroniza hasta que se decida (`SyncCoordinator.resolveAccountSwitch`).
     */
    data class AccountSwitch(val pending: Int) : SyncStatus
}

/**
 * Decide **cuando** sincronizar; `SyncEngine` decide **como**.
 *
 * Disparadores:
 * - cambios locales pendientes (Room reemite la firma de lo pendiente), con
 *   espera de 1,5 s para agrupar rafagas de escritura (el autoguardado);
 * - inicio de sesion;
 * - la app vuelve a primer plano ([requestSync] desde la Activity);
 * - el boton "Sincronizar ahora" ([syncNow]).
 *
 * Un fallo no se reintenta en bucle: se reintenta en el siguiente disparador.
 * Un mutex serializa las pasadas: dos a la vez subirian las mismas altas dos
 * veces, porque ninguna habria guardado aun el `remoteId`.
 *
 * Sin WorkManager (como SitoWallet): las notas se escriben con la app abierta,
 * y lo pendiente sube la proxima vez que se abra.
 */
class SyncCoordinator(
    private val engine: SyncEngine,
    private val pendingStats: Flow<PendingStats>,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
    private val accountGuard: AccountGuard? = null,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val running = Mutex()

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            val localChanges = pendingStats
                .distinctUntilChanged()
                // Con cero pendientes no hay nada que subir: es justo la
                // emision que provoca una subida al terminar, y repetirla
                // seria un bucle de descargas vacias.
                .filter { it.count > 0 }
                .debounce(debounceMillis)
                .map { }

            val signedIn = sessionState
                .map { (it as? SessionState.Authenticated)?.session?.userId }
                .distinctUntilChanged()
                .filterNotNull()
                .map { }

            merge(localChanges, signedIn, requests)
                .conflate()
                .collect { runSync() }
        }

        // Sin cuenta, el estado es Idle aunque haya algo pendiente.
        scope.launch {
            sessionState.collect { session ->
                if (session !is SessionState.Authenticated) _status.value = SyncStatus.Idle
            }
        }
    }

    /** Primer plano y otros avisos que no necesitan esperar el resultado. */
    fun requestSync() {
        requests.tryEmit(Unit)
    }

    /** El boton manual: sincroniza ya y devuelve el resultado. */
    suspend fun syncNow(): Result<SyncResult> = runSync()

    /**
     * Respuesta al dialogo de cambio de cuenta: [upload] sube lo pendiente a
     * la cuenta nueva; si no, se descarta. Despues sincroniza.
     */
    fun resolveAccountSwitch(upload: Boolean) {
        val userId = currentUserId() ?: return
        scope.launch {
            running.withLock {
                accountGuard?.resolve(userId, upload)
                _status.value = SyncStatus.Idle
            }
            runSync()
        }
    }

    private fun currentUserId(): Long? =
        (sessionState.value as? SessionState.Authenticated)?.session?.userId

    private suspend fun runSync(): Result<SyncResult> {
        val userId = currentUserId()
            ?: return Result.failure(IllegalStateException("Sin sesion"))

        return running.withLock {
            val waiting = accountGuard?.check(userId) ?: 0
            if (waiting > 0) {
                _status.value = SyncStatus.AccountSwitch(waiting)
                return@withLock Result.failure(AccountSwitchPendingException(waiting))
            }
            _status.value = SyncStatus.Syncing
            try {
                val result = engine.sync(userId)
                _status.value = SyncStatus.UpToDate(now())
                Result.success(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: NetworkException) {
                _status.value = SyncStatus.Failed(error.message.orEmpty(), offline = true)
                Result.failure(error)
            } catch (error: Exception) {
                _status.value = SyncStatus.Failed(error.message.orEmpty(), offline = false)
                Result.failure(error)
            }
        }
    }

    private companion object {
        /** Lo bastante para que el autoguardado termine una rafaga antes de subir. */
        const val DEBOUNCE_MILLIS = 1_500L
    }
}

/** No se sincroniza: falta decidir que hacer con lo pendiente de la cuenta anterior. */
class AccountSwitchPendingException(val pending: Int) :
    Exception("$pending cambios de otra cuenta esperan una decision")
