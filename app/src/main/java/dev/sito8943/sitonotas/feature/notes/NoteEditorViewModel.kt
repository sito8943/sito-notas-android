package dev.sito8943.sitonotas.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NoteEditorUiState(
    val loading: Boolean = true,
    val noteId: Long? = null,
    val title: String = "",
    val content: String = "",
    val favorite: Boolean = false,
    val categoryIds: Set<Long> = emptySet(),
    val allCategories: List<Category> = emptyList(),
    val preview: Boolean = false,
    /** La nota se borro mientras estaba abierta (otro dispositivo, papelera). */
    val missing: Boolean = false,
) {
    val selectedCategories: List<Category>
        get() = allCategories.filter { it.id in categoryIds }
}

/**
 * Editor con autoguardado local: cada cambio se escribe en Room tras una
 * pausa corta, y al salir se guarda lo que quede. Una nota nueva no se crea
 * hasta que tiene algo escrito (o se marca favorita / se le pone categoria).
 *
 * Las escrituras corren en [saveScope] (el del proceso) y no en el
 * `viewModelScope`: al cerrar el editor el ViewModel se destruye, y el ultimo
 * guardado no puede morir con el.
 */
class NoteEditorViewModel(
    noteId: Long?,
    private val notes: NoteRepository,
    categories: CategoryRepository,
    private val saveScope: CoroutineScope,
    private val autosaveMillis: Long = AUTOSAVE_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(NoteEditorUiState(noteId = noteId, loading = noteId != null))
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    /** Serializa las escrituras: dos guardados a la vez crearian dos notas. */
    private val writes = Mutex()
    private var pendingSave: Job? = null

    init {
        viewModelScope.launch {
            categories.observeCategories().collect { all ->
                _state.update { state ->
                    state.copy(
                        allCategories = all,
                        categoryIds = state.categoryIds.filterTo(mutableSetOf()) { id -> all.any { it.id == id } },
                    )
                }
            }
        }
        if (noteId != null) {
            viewModelScope.launch {
                val note = notes.observeNote(noteId).first()
                _state.update {
                    if (note == null) {
                        it.copy(loading = false, missing = true)
                    } else {
                        it.copy(
                            loading = false,
                            title = note.title,
                            content = note.content,
                            favorite = note.favorite,
                            categoryIds = note.categories.map { c -> c.id }.toSet(),
                            // Abrir una nota con contenido muestra la vista previa.
                            preview = note.content.isNotBlank(),
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value) }
        scheduleSave()
    }

    fun onContentChange(value: String) {
        _state.update { it.copy(content = value) }
        scheduleSave()
    }

    fun togglePreview() = _state.update { it.copy(preview = !it.preview) }

    fun toggleFavorite() {
        _state.update { it.copy(favorite = !it.favorite) }
        save(force = true) { id, s -> notes.setFavorite(id, s.favorite) }
    }

    fun setCategories(ids: Set<Long>) {
        _state.update { it.copy(categoryIds = ids) }
        save(force = true) { id, s -> notes.setCategories(id, s.categoryIds) }
    }

    /** Guarda ya lo que haya (al salir de la pantalla). */
    fun flush() {
        pendingSave?.cancel()
        save(force = false) { _, _ -> }
    }

    fun trash(onDone: () -> Unit) {
        pendingSave?.cancel()
        saveScope.launch {
            writes.withLock {
                val id = persistText(force = false)
                if (id != null) notes.trash(id)
            }
            onDone()
        }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(autosaveMillis)
            save(force = false) { _, _ -> }
        }
    }

    /**
     * Escribe titulo y contenido y luego [after]. Con [force] crea la nota
     * aunque este vacia: marcar favorita o poner categoria ya es contenido.
     */
    private fun save(force: Boolean, after: suspend (Long, NoteEditorUiState) -> Unit) {
        saveScope.launch {
            writes.withLock {
                val id = persistText(force) ?: return@withLock
                after(id, _state.value)
            }
        }
    }

    /** @return id de la nota, o nulo si es nueva y no hay nada que guardar. */
    private suspend fun persistText(force: Boolean): Long? {
        val current = _state.value
        if (current.loading || current.missing) return null
        val existing = current.noteId
        if (existing != null) {
            notes.updateText(existing, current.title, current.content)
            return existing
        }
        if (!force && current.title.isBlank() && current.content.isBlank()) return null
        val created = notes.create(
            title = current.title,
            content = current.content,
            favorite = current.favorite,
            categoryIds = current.categoryIds,
        )
        _state.update { it.copy(noteId = created) }
        return created
    }

    private companion object {
        const val AUTOSAVE_MILLIS = 600L
    }
}
