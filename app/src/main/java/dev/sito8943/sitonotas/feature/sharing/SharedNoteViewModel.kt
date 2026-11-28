package dev.sito8943.sitonotas.feature.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import dev.sito8943.sitonotas.feature.sharing.data.NoteOwnerDto
import dev.sito8943.sitonotas.feature.sharing.data.SharingEndpoints
import dev.sito8943.sitonotas.core.sync.ServerTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SharedSaveStatus { IDLE, SAVING, SAVED, FAILED }

data class SharedNoteUiState(
    val loading: Boolean = true,
    val loadError: SharingError? = null,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long? = null,
    val owner: NoteOwnerDto? = null,
    /** Shared with EDIT: title and content can change (never tags or favorite). */
    val canEdit: Boolean = false,
    val saveStatus: SharedSaveStatus = SharedSaveStatus.IDLE,
    val saveError: SharingError? = null,
    /** The note turned out to be the user's own and is on this phone: open the normal editor. */
    val openLocalId: Long? = null,
)

/**
 * A note shared with the user (web `Note` page with `owner`): read only, or
 * editable with an EDIT share. Online only: it is read with `GET /notes/{id}`
 * and each edit is sent with `PATCH /notes/{id}` after a short pause (and on
 * leaving), like `useNoteEditor`. A failed save stays visible and the next
 * edit (or leaving) tries again; nothing is kept in Room.
 */
class SharedNoteViewModel(
    private val remoteId: Long,
    private val api: SharingEndpoints,
    private val notes: NoteRepository,
    private val saveScope: CoroutineScope,
    private val autosaveMillis: Long = AUTOSAVE_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(SharedNoteUiState())
    val state: StateFlow<SharedNoteUiState> = _state.asStateFlow()

    private val writes = Mutex()
    private var pendingSave: Job? = null

    /** What the server has, to skip saves that change nothing. */
    private var savedTitle = ""
    private var savedContent = ""

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            runCatchingNonCancel { api.getNote(remoteId) }
                .onSuccess { note ->
                    if (note.owner == null) {
                        val localId = notes.localIdOf(remoteId)
                        if (localId != null) {
                            _state.update { it.copy(openLocalId = localId) }
                            return@onSuccess
                        }
                    }
                    savedTitle = note.title.orEmpty()
                    savedContent = note.content.orEmpty()
                    _state.update {
                        it.copy(
                            loading = false,
                            title = savedTitle,
                            content = savedContent,
                            updatedAt = ServerTime.toEpochMillis(note.updatedAt),
                            owner = note.owner,
                            canEdit = note.owner != null && note.canEdit,
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(loading = false, loadError = SharingError.of(error)) } }
        }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onContentChange(value: String) = edit { it.copy(content = value) }

    private fun edit(change: (SharedNoteUiState) -> SharedNoteUiState) {
        if (!_state.value.canEdit) return
        _state.update(change)
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(autosaveMillis)
            save()
        }
    }

    /** Sends what is left now (leaving the screen). Runs in [saveScope] so it outlives the ViewModel. */
    fun flush() {
        pendingSave?.cancel()
        pendingSave = null
        saveScope.launch { save() }
    }

    private suspend fun save() = writes.withLock {
        val current = _state.value
        if (!current.canEdit || (current.title == savedTitle && current.content == savedContent)) return@withLock
        _state.update { it.copy(saveStatus = SharedSaveStatus.SAVING, saveError = null) }
        runCatchingNonCancel { api.updateNote(remoteId, current.title, current.content) }
            .onSuccess {
                savedTitle = current.title
                savedContent = current.content
                _state.update {
                    it.copy(saveStatus = SharedSaveStatus.SAVED, updatedAt = System.currentTimeMillis())
                }
            }
            .onFailure { error ->
                _state.update { it.copy(saveStatus = SharedSaveStatus.FAILED, saveError = SharingError.of(error)) }
            }
    }

    /** Try again after a failed save. */
    fun retrySave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch { save() }
    }

    override fun onCleared() {
        // Leaving with edits not sent (viewModelScope is already cancelled
        // here, so the pending autosave died): the edit must not be lost.
        val current = _state.value
        if (current.canEdit && (current.title != savedTitle || current.content != savedContent)) {
            saveScope.launch { save() }
        }
    }

    private companion object {
        /** Same pause as the web's autosave. */
        const val AUTOSAVE_MILLIS = 600L
    }
}
