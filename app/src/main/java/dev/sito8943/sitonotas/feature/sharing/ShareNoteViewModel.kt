package dev.sito8943.sitonotas.feature.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import dev.sito8943.sitonotas.feature.sharing.data.NoteShareDto
import dev.sito8943.sitonotas.feature.sharing.data.SharePermission
import dev.sito8943.sitonotas.feature.sharing.data.SharingEndpoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the note can be shared at all. */
sealed interface ShareAvailability {
    data object Loading : ShareAvailability

    /** Guest mode: sharing needs an account (the server knows who is who). */
    data object NoAccount : ShareAvailability

    /** Not uploaded yet: the server has no id to share. */
    data object NotSynced : ShareAvailability

    data class Ready(val remoteId: Long) : ShareAvailability
}

/** One-shot notices, like the web's toasts. */
enum class ShareNotice { LINK_TURNED_OFF }

data class ShareNoteUiState(
    val availability: ShareAvailability = ShareAvailability.Loading,
    val title: String = "",
    val content: String = "",
    val shares: List<NoteShareDto> = emptyList(),
    val sharesLoading: Boolean = false,
    val sharesError: SharingError? = null,
    val email: String = "",
    val permission: SharePermission = SharePermission.READ,
    val adding: Boolean = false,
    val addError: SharingError? = null,
    val linkToken: String? = null,
    val linkLoading: Boolean = false,
    val linkBusy: Boolean = false,
    /** Failure of a change (permission, remove, link), shown once. */
    val actionError: SharingError? = null,
    val notice: ShareNotice? = null,
) {
    val remoteId: Long? get() = (availability as? ShareAvailability.Ready)?.remoteId
    val canSubmit: Boolean get() = email.isNotBlank() && !adding && remoteId != null
}

/**
 * Share dialog of the web (`ShareNoteDialog`): people by email with view or
 * edit permission, the public link ("general access") and a copy through
 * the share sheet. Online only, nothing is stored in Room. Changing and
 * removing update the list at once and roll back on error, like the web.
 */
class ShareNoteViewModel(
    private val localNoteId: Long?,
    private val notes: NoteRepository,
    private val session: StateFlow<SessionState>,
    private val api: SharingEndpoints,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareNoteUiState())
    val state: StateFlow<ShareNoteUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val note = localNoteId?.let { notes.observeNote(it).first() }
        _state.update { it.copy(title = note?.title.orEmpty(), content = note?.content.orEmpty()) }
        val availability = when {
            session.value !is SessionState.Authenticated -> ShareAvailability.NoAccount
            else -> localNoteId?.let { notes.remoteIdOf(it) }
                ?.let { ShareAvailability.Ready(it) } ?: ShareAvailability.NotSynced
        }
        _state.update { it.copy(availability = availability) }
        if (availability is ShareAvailability.Ready) {
            load(availability.remoteId)
        }
    }

    /** Loads (or reloads after an error) the people and the public link. */
    fun retry() {
        val remoteId = _state.value.remoteId ?: return
        viewModelScope.launch { load(remoteId) }
    }

    private suspend fun load(remoteId: Long) {
        _state.update { it.copy(sharesLoading = true, linkLoading = true, sharesError = null) }
        val shares = runCatchingNonCancel { api.listShares(remoteId) }
        val link = runCatchingNonCancel { api.getLink(remoteId) }
        _state.update {
            it.copy(
                sharesLoading = false,
                linkLoading = false,
                shares = shares.getOrDefault(it.shares),
                sharesError = (shares.exceptionOrNull() ?: link.exceptionOrNull())?.let(SharingError::of),
                linkToken = link.getOrDefault(it.linkToken),
            )
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, addError = null) }

    fun onPermissionChange(value: SharePermission) = _state.update { it.copy(permission = value) }

    fun add() {
        val current = _state.value
        val remoteId = current.remoteId ?: return
        val email = current.email.trim()
        if (email.isEmpty() || current.adding) return
        _state.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            runCatchingNonCancel { api.share(remoteId, email, current.permission) }
                .onSuccess { share ->
                    _state.update { it.copy(adding = false, email = "", shares = it.shares + share) }
                }
                .onFailure { error ->
                    _state.update { it.copy(adding = false, addError = SharingError.ofShare(error)) }
                }
        }
    }

    fun changePermission(userId: Long, permission: SharePermission) {
        val remoteId = _state.value.remoteId ?: return
        val previous = _state.value.shares
        _state.update { state ->
            state.copy(shares = state.shares.map { if (it.userId == userId) it.copy(permission = permission) else it })
        }
        viewModelScope.launch {
            runCatchingNonCancel { api.setPermission(remoteId, userId, permission) }
                .onFailure { error -> _state.update { it.copy(shares = previous, actionError = SharingError.of(error)) } }
        }
    }

    fun remove(userId: Long) {
        val remoteId = _state.value.remoteId ?: return
        val previous = _state.value.shares
        _state.update { state -> state.copy(shares = state.shares.filter { it.userId != userId }) }
        viewModelScope.launch {
            runCatchingNonCancel { api.unshare(remoteId, userId) }
                .onFailure { error -> _state.update { it.copy(shares = previous, actionError = SharingError.of(error)) } }
        }
    }

    /** "Anyone with the link" on/off. Turning it on again gives a new token, so old links die. */
    fun setPublic(public: Boolean) {
        val current = _state.value
        val remoteId = current.remoteId ?: return
        if (current.linkBusy || public == (current.linkToken != null)) return
        _state.update { it.copy(linkBusy = true) }
        viewModelScope.launch {
            val result = runCatchingNonCancel {
                if (public) api.createLink(remoteId) else api.removeLink(remoteId).let { null }
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { token ->
                        state.copy(
                            linkBusy = false,
                            linkToken = token,
                            notice = if (public) state.notice else ShareNotice.LINK_TURNED_OFF,
                        )
                    },
                    onFailure = { state.copy(linkBusy = false, actionError = SharingError.of(it)) },
                )
            }
        }
    }

    fun consumeActionError() = _state.update { it.copy(actionError = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }
}

/** `runCatching` that lets coroutine cancellation through. */
internal inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
