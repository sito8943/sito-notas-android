package dev.sito8943.sitonotas.feature.trash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.ui.NoteCard
import dev.sito8943.sitonotas.feature.trash.TrashConfirmation
import dev.sito8943.sitonotas.feature.trash.TrashViewModel
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesConfirmationDialog
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme

@Composable
fun TrashScreen(viewModel: TrashViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Page(
        title = stringResource(R.string.trash_title),
        actions = {
            if (state.notes.isNotEmpty()) {
                NotesButton(
                    text = stringResource(R.string.trash_empty_action),
                    onClick = viewModel::askEmpty,
                    variant = ButtonVariant.TEXT,
                    color = ButtonColor.DANGER,
                )
            }
        },
    ) {
        NotesText(
            text = stringResource(R.string.trash_hint),
            style = NotesTheme.typography.caption,
            color = NotesTheme.colors.textMuted,
            modifier = Modifier.padding(horizontal = spacing(5)),
        )
        when {
            state.loading -> Unit
            state.notes.isEmpty() -> EmptyState(stringResource(R.string.trash_empty))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = spacing(4), end = spacing(4), bottom = spacing(6)),
                verticalArrangement = Arrangement.spacedBy(spacing(2)),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = {},
                        trailing = {
                            NotesIconButton(
                                icon = Icons.Outlined.RestoreFromTrash,
                                contentDescription = stringResource(R.string.trash_restore),
                                onClick = { viewModel.restore(note) },
                            )
                            NotesIconButton(
                                icon = Icons.Outlined.DeleteForever,
                                contentDescription = stringResource(R.string.trash_delete_forever),
                                onClick = { viewModel.askDelete(note) },
                                tint = NotesTheme.colors.negative,
                            )
                        },
                    )
                }
            }
        }
    }

    val confirmation = state.confirmation
    NotesConfirmationDialog(
        open = confirmation != null,
        title = stringResource(
            if (confirmation is TrashConfirmation.All) R.string.trash_empty_title else R.string.trash_delete_title,
        ),
        message = stringResource(R.string.trash_delete_message),
        confirmLabel = stringResource(R.string.trash_delete_forever),
        onConfirm = viewModel::confirm,
        onDismiss = viewModel::dismiss,
    )
}
