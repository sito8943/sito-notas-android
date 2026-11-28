package dev.sito8943.sitonotas.feature.sharing

import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.feature.sharing.data.SharingEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Badge of the "Compartidas" tab (web `useUnseenShared`): how many notes
 * shared with the user they have not seen. Best effort: offline or a guest
 * keeps 0 and nothing is shown.
 */
class UnseenShares(
    private val session: StateFlow<SessionState>,
    private val api: SharingEndpoints,
    private val scope: CoroutineScope,
) {
    private val _count = MutableStateFlow(0L)
    val count: StateFlow<Long> = _count.asStateFlow()

    private val mutex = Mutex()

    fun refresh() {
        scope.launch {
            mutex.withLock {
                if (session.value !is SessionState.Authenticated) {
                    _count.value = 0
                    return@withLock
                }
                runCatchingNonCancel { api.countUnseen() }.onSuccess { _count.value = it }
            }
        }
    }

    /** The user opened "Compartidas conmigo": clears the badge at once, then tells the server. */
    suspend fun markSeen() {
        if (_count.value == 0L) {
            runCatchingNonCancel { api.countUnseen() }.onSuccess { _count.value = it }
        }
        if (_count.value > 0L) {
            _count.value = 0
            runCatchingNonCancel { api.markSeen() }.onFailure { refresh() }
        }
    }
}
