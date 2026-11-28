package dev.sito8943.sitonotas.feature.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.sync.ServerTime
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import dev.sito8943.sitonotas.feature.sharing.data.SharingEndpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a public link should go instead of the public view. */
sealed interface PublicNoteRedirect {
    /** The user's own note, on this phone. */
    data class Local(val localId: Long) : PublicNoteRedirect

    /** Shared with the user (or their own note not on this phone yet). */
    data class Shared(val remoteId: Long) : PublicNoteRedirect
}

data class PublicNoteUiState(
    val loading: Boolean = true,
    /** 404: unknown, revoked link or trashed note. */
    val notFound: Boolean = false,
    val error: SharingError? = null,
    val title: String = "",
    val content: String = "",
    val ownerName: String = "",
    val updatedAt: Long? = null,
    val redirect: PublicNoteRedirect? = null,
)

/**
 * A note opened through its public link (web `PublicNote`): read only, no
 * account needed. The owner and users it is shared with go to the normal
 * note view instead, as on the web.
 */
class PublicNoteViewModel(
    private val token: String,
    private val api: SharingEndpoints,
    private val notes: NoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PublicNoteUiState())
    val state: StateFlow<PublicNoteUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, notFound = false) }
        viewModelScope.launch {
            runCatchingNonCancel { api.getPublic(token) }
                .onSuccess { note ->
                    val redirect = note.noteId?.let { remoteId ->
                        notes.localIdOf(remoteId)?.let { PublicNoteRedirect.Local(it) }
                            ?: PublicNoteRedirect.Shared(remoteId)
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            title = note.title.orEmpty(),
                            content = note.content.orEmpty(),
                            ownerName = note.ownerName.orEmpty(),
                            updatedAt = ServerTime.toEpochMillis(note.updatedAt),
                            redirect = redirect,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            notFound = error is ApiException && error.status == 404,
                            error = SharingError.of(error),
                        )
                    }
                }
        }
    }

    fun consumeRedirect() = _state.update { it.copy(redirect = null) }
}
