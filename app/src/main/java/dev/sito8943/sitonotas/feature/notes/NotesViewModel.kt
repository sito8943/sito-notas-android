package dev.sito8943.sitonotas.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.model.SearchText
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** Ordenadas por uso, como `sortTagsByUses`. */
    val categories: List<Category> = emptyList(),
    val counts: TabCounts = TabCounts(),
    val filters: NoteFilters = NoteFilters(),
)

@OptIn(FlowPreview::class)
class NotesViewModel(
    private val notes: NoteRepository,
    categories: CategoryRepository,
    /** Todas las notas activas en el orden nuevo (ids locales, de arriba abajo). */
    private val reorder: suspend (List<Long>) -> Unit,
) : ViewModel() {

    private val filters = MutableStateFlow(NoteFilters())

    /** Ids de todas las notas activas en el orden mostrado, para fusionar un reorden filtrado. */
    @Volatile
    private var allIds: List<Long> = emptyList()

    /** Como `useDebounce(toSearch, 500)` de la web; vaciar la busqueda es inmediato. */
    private val searchQuery = filters
        .map { it.query }
        .distinctUntilChanged()
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS }

    val state: StateFlow<NotesUiState> = combine(
        notes.observeNotes(),
        categories.observeCategories(),
        filters,
        searchQuery,
    ) { all, cats, current, query ->
        allIds = all.map { it.id }
        val counts = TabCounts.of(all)
        // Una pestaña sin notas queda deshabilitada; si era la elegida, vuelve a "Todas".
        val effective = current.copy(
            categoryId = current.categoryId?.takeIf { id -> cats.any { it.id == id } && counts.of(id) > 0 },
            favoritesOnly = current.favoritesOnly && counts.favorites > 0,
        )
        NotesUiState(
            loading = false,
            notes = all.filter { it.matches(effective, query) },
            totalNotes = all.size,
            categories = cats.sortedByUses(counts),
            counts = counts,
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

    /**
     * Soltar tras arrastrar. [visibleIds] es el orden nuevo de las notas que se
     * ven; las ocultas por la busqueda o el filtro conservan su hueco.
     */
    fun onReordered(visibleIds: List<Long>) {
        val merged = NoteListRules.mergeVisibleOrder(allIds, visibleIds)
        if (merged == allIds) return
        allIds = merged
        viewModelScope.launch { reorder(merged) }
    }

    private fun Note.matches(filters: NoteFilters, query: String): Boolean {
        if (filters.favoritesOnly && !favorite) return false
        if (filters.categoryId != null && categories.none { it.id == filters.categoryId }) return false
        return SearchText.matches(title, content, query)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 500L
    }
}
