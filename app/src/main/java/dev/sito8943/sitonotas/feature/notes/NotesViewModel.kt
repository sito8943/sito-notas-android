package dev.sito8943.sitonotas.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.model.SearchText
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filtros de la lista, como `SearchProvider` en la web. */
data class NoteFilters(
    val query: String = "",
    val categoryId: Long? = null,
    val favoritesOnly: Boolean = false,
) {
    val active: Boolean get() = query.isNotBlank() || categoryId != null || favoritesOnly
}

data class NotesUiState(
    val loading: Boolean = true,
    val notes: List<Note> = emptyList(),
    val totalNotes: Int = 0,
    val categories: List<Category> = emptyList(),
    val filters: NoteFilters = NoteFilters(),
)

class NotesViewModel(
    private val notes: NoteRepository,
    categories: CategoryRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(NoteFilters())

    val state: StateFlow<NotesUiState> = combine(
        notes.observeNotes(),
        categories.observeCategories(),
        filters,
    ) { all, cats, current ->
        // Una categoria borrada no puede quedar como filtro activo.
        val effective = if (current.categoryId != null && cats.none { it.id == current.categoryId }) {
            current.copy(categoryId = null)
        } else {
            current
        }
        NotesUiState(
            loading = false,
            notes = all.filter { it.matches(effective) },
            totalNotes = all.size,
            categories = cats,
            filters = effective,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NotesUiState())

    fun onQueryChange(value: String) = filters.update { it.copy(query = value) }

    fun onCategorySelected(id: Long?) = filters.update {
        it.copy(categoryId = if (it.categoryId == id) null else id)
    }

    fun onFavoritesOnlyToggle() = filters.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch { notes.setFavorite(note.id, !note.favorite) }
    }

    fun trash(note: Note) {
        viewModelScope.launch { notes.trash(note.id) }
    }

    private fun Note.matches(filters: NoteFilters): Boolean {
        if (filters.favoritesOnly && !favorite) return false
        if (filters.categoryId != null && categories.none { it.id == filters.categoryId }) return false
        return SearchText.matches(title, content, filters.query)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
