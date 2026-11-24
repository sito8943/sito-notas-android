package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.NoteFilters
import dev.sito8943.sitonotas.feature.notes.NotesViewModel
import dev.sito8943.sitonotas.ui.elements.DashedChip
import dev.sito8943.sitonotas.ui.elements.FilterTab
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesSearchField
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesFab
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Lista de notas, como `Home` en la web: buscador, pestañas de filtro
 * ("Todas", "Favoritas" y una por categoria) con el acceso a gestionarlas, y
 * las tarjetas. La nota nueva sale del FAB, como en Android.
 */
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onManageCategories: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters = state.filters
    val colors = NotesTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        Page(title = stringResource(R.string.notes_title)) {
            NotesSearchField(
                value = filters.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.notes_search),
                clearLabel = stringResource(R.string.notes_search_clear),
                modifier = Modifier.padding(horizontal = spacing(4)),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = spacing(4)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing(2)),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = spacing(4)),
                    horizontalArrangement = Arrangement.spacedBy(spacing(2)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Pestañas excluyentes, como en la web: se pasa de una a
                    // otra apagando el filtro anterior con la API del ViewModel.
                    FilterTab(
                        label = stringResource(R.string.notes_filter_all),
                        selected = !filters.favoritesOnly && filters.categoryId == null,
                        onClick = { viewModel.selectTab(filters, favorites = false, categoryId = null) },
                    )
                    FilterTab(
                        label = stringResource(R.string.notes_filter_favorites),
                        selected = filters.favoritesOnly,
                        onClick = { viewModel.selectTab(filters, favorites = true, categoryId = null) },
                    )
                    state.categories.forEach { category ->
                        FilterTab(
                            label = category.name,
                            selected = !filters.favoritesOnly && filters.categoryId == category.id,
                            onClick = { viewModel.selectTab(filters, favorites = false, categoryId = category.id) },
                        )
                    }
                }
                DashedChip(
                    icon = R.drawable.fa_pen,
                    onClick = onManageCategories,
                    contentDescription = stringResource(R.string.notes_manage_categories),
                    modifier = Modifier.size(36.dp),
                )
            }

            when {
                state.loading -> Unit
                state.notes.isEmpty() -> EmptyState(
                    text = stringResource(
                        if (state.totalNotes == 0) R.string.notes_empty else R.string.notes_empty_filtered,
                    ),
                    icon = if (state.totalNotes == 0) R.drawable.fa_note_sticky_regular else R.drawable.fa_magnifying_glass,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = spacing(4), end = spacing(4), bottom = spacing(24)),
                    verticalArrangement = Arrangement.spacedBy(spacing(4)),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            actions = {
                                NotesIconButton(
                                    icon = if (note.favorite) R.drawable.fa_heart else R.drawable.fa_heart_regular,
                                    contentDescription = stringResource(
                                        if (note.favorite) R.string.note_unfavorite else R.string.note_favorite,
                                    ),
                                    onClick = { viewModel.toggleFavorite(note) },
                                    tint = colors.accent,
                                    size = IconButtonSize.SM,
                                )
                                NotesIconButton(
                                    icon = R.drawable.fa_trash,
                                    contentDescription = stringResource(R.string.note_trash),
                                    onClick = { viewModel.trash(note) },
                                    tint = colors.negative,
                                    size = IconButtonSize.SM,
                                )
                            },
                        )
                    }
                }
            }
        }

        NotesFab(
            onClick = onNewNote,
            contentDescription = stringResource(R.string.notes_new),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing(4)),
        )
    }
}

/**
 * Cambia de pestaña con las operaciones que ya expone el ViewModel (alternar
 * favoritas, alternar categoria): apaga el filtro activo y enciende el nuevo.
 */
private fun NotesViewModel.selectTab(current: NoteFilters, favorites: Boolean, categoryId: Long?) {
    if (current.favoritesOnly != favorites) onFavoritesOnlyToggle()
    if (current.categoryId != categoryId) onCategorySelected(categoryId ?: current.categoryId)
}
