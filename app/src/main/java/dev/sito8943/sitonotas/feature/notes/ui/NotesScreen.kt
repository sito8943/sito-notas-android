package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.NotesViewModel
import dev.sito8943.sitonotas.ui.elements.CategoryChip
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesTextField
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesFab
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters = state.filters

    Box(modifier = Modifier.fillMaxSize()) {
        Page(title = stringResource(R.string.notes_title)) {
            NotesTextField(
                value = filters.query,
                onValueChange = viewModel::onQueryChange,
                label = stringResource(R.string.notes_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing(4)),
                imeAction = ImeAction.Search,
                trailing = {
                    if (filters.query.isEmpty()) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = NotesTheme.colors.textSubtle)
                    } else {
                        NotesIconButton(
                            icon = Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.notes_search_clear),
                            onClick = { viewModel.onQueryChange("") },
                        )
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing(4)),
                horizontalArrangement = Arrangement.spacedBy(spacing(2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryChip(
                    name = stringResource(R.string.notes_filter_favorites),
                    color = "#F59E0B",
                    selected = filters.favoritesOnly,
                    onClick = viewModel::onFavoritesOnlyToggle,
                )
                state.categories.forEach { category ->
                    CategoryChip(
                        name = category.name,
                        color = category.color,
                        selected = filters.categoryId == category.id,
                        onClick = { viewModel.onCategorySelected(category.id) },
                    )
                }
            }

            when {
                state.loading -> Unit
                state.notes.isEmpty() -> EmptyState(
                    stringResource(
                        if (state.totalNotes == 0) R.string.notes_empty else R.string.notes_empty_filtered,
                    ),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = spacing(4), end = spacing(4), bottom = spacing(24)),
                    verticalArrangement = Arrangement.spacedBy(spacing(2)),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(note) },
                            trailing = {
                                NotesIconButton(
                                    icon = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.note_trash),
                                    onClick = { viewModel.trash(note) },
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
                .padding(spacing(5)),
        )
    }
}

