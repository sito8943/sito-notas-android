package dev.sito8943.sitonotas.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.feature.notes.Note
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Que borrado definitivo se esta confirmando. */
sealed interface TrashConfirmation {
    data class One(val note: Note) : TrashConfirmation
    data object All : TrashConfirmation
}

data class TrashUiState(
    val loading: Boolean = true,
    val notes: List<Note> = emptyList(),
    val confirmation: TrashConfirmation? = null,
)

class TrashViewModel(private val notes: NoteRepository) : ViewModel() {

    private val confirmation = MutableStateFlow<TrashConfirmation?>(null)

    val state: StateFlow<TrashUiState> = combine(notes.observeTrash(), confirmation) { trashed, confirming ->
        TrashUiState(loading = false, notes = trashed, confirmation = confirming)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(note: Note) {
        viewModelScope.launch { notes.restore(note.id) }
    }

    fun askDelete(note: Note) {
        confirmation.value = TrashConfirmation.One(note)
    }

    fun askEmpty() {
        confirmation.value = TrashConfirmation.All
    }

    fun dismiss() {
        confirmation.value = null
    }

    fun confirm() {
        val current = confirmation.value ?: return
        confirmation.value = null
        viewModelScope.launch {
            when (current) {
                is TrashConfirmation.One -> notes.deletePermanently(current.note.id)
                TrashConfirmation.All -> notes.emptyTrash()
            }
        }
    }
}
