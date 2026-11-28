package dev.sito8943.sitonotas.feature.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.feature.sharing.data.SharedNoteDto
import dev.sito8943.sitonotas.feature.sharing.data.SharingEndpoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SharedNotesUiState(
    val loading: Boolean = true,
    /** Last edited first, as the server sends them. */
    val notes: List<SharedNoteDto> = emptyList(),
    val error: SharingError? = null,
    /** Guest mode: nobody can share with a user the server does not know. */
    val needsAccount: Boolean = false,
)

/**
 * "Compartidas conmigo" (web `Shared` page). Online only: the list is not
 * kept in Room, so offline it shows an explicit error with a retry.
 * Opening it marks every share as seen (clears the tab badge), like
 * `useMarkSharedSeen`.
 */
class SharedNotesViewModel(
    private val session: StateFlow<SessionState>,
    private val api: SharingEndpoints,
    private val unseen: UnseenShares,
) : ViewModel() {

    private val _state = MutableStateFlow(SharedNotesUiState())
    val state: StateFlow<SharedNotesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (session.value !is SessionState.Authenticated) {
            _state.value = SharedNotesUiState(loading = false, needsAccount = true)
            return
        }
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, error = null, needsAccount = false) }
        loadJob = viewModelScope.launch {
            runCatchingNonCancel { api.listSharedWithMe() }
                .onSuccess { notes -> _state.update { it.copy(loading = false, notes = notes) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = SharingError.of(error)) } }
            unseen.markSeen()
        }
    }
}
