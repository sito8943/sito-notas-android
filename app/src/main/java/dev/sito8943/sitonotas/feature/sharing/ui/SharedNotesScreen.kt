package dev.sito8943.sitonotas.feature.sharing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.sync.ServerTime
import dev.sito8943.sitonotas.feature.notes.ui.NotePreview
import dev.sito8943.sitonotas.feature.sharing.SharedNotesViewModel
import dev.sito8943.sitonotas.feature.sharing.data.SharedNoteDto
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatNoteDate
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** "Compartidas conmigo" (web `Shared` page). */
@Composable
fun SharedNotesScreen(
    viewModel: SharedNotesViewModel,
    onOpenNote: (remoteId: Long) -> Unit,
    onSignIn: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors

    Page(
        title = stringResource(R.string.shared_title),
        description = stringResource(R.string.shared_description),
        actions = {
            if (!state.needsAccount) {
                NotesIconButton(
                    icon = R.drawable.fa_rotate,
                    contentDescription = stringResource(R.string.sharing_retry),
                    onClick = viewModel::refresh,
                    enabled = !state.loading,
                )
            }
        },
    ) {
        when {
            state.needsAccount -> Column(
                modifier = Modifier.fillMaxWidth().padding(spacing(6)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing(4)),
            ) {
                FaIcon(R.drawable.fa_user_group, contentDescription = null, tint = colors.textSubtle, size = 32.dp)
                NotesText(stringResource(R.string.shared_needs_account), color = colors.textMuted)
                NotesButton(
                    text = stringResource(R.string.sharing_sign_in),
                    onClick = onSignIn,
                    color = ButtonColor.PRIMARY,
                )
            }
            state.error != null && state.notes.isEmpty() -> SharingErrorState(state.error!!, onRetry = viewModel::refresh)
            state.loading && state.notes.isEmpty() -> Unit
            state.notes.isEmpty() -> EmptyState(stringResource(R.string.shared_empty), icon = R.drawable.fa_user_group)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = spacing(4), end = spacing(4), bottom = spacing(6)),
                verticalArrangement = Arrangement.spacedBy(spacing(3)),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    SharedNoteCard(note, onClick = { onOpenNote(note.id) })
                }
            }
        }
    }
}

/** Web `SharedNote` card: preview, owner and last edit. */
@Composable
private fun SharedNoteCard(note: SharedNoteDto, onClick: () -> Unit) {
    val colors = NotesTheme.colors
    val typography = NotesTheme.typography
    Surface(modifier = Modifier.fillMaxWidth(), shape = Radius.lgShape, color = colors.surface) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(spacing(5)),
            verticalArrangement = Arrangement.spacedBy(spacing(3)),
        ) {
            NotesText(
                note.title.orEmpty().ifBlank { stringResource(R.string.note_untitled) },
                style = typography.cardTitle,
                color = colors.textStrong,
                maxLines = 2,
            )
            NotePreview(note.content.orEmpty())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing(2))) {
                FaIcon(R.drawable.fa_user, contentDescription = null, tint = colors.textMuted, size = 12.dp)
                NotesText(
                    stringResource(R.string.shared_from, note.owner?.displayName.orEmpty()),
                    style = typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                ServerTime.toEpochMillis(note.updatedAt)?.let {
                    NotesText(formatNoteDate(it), style = typography.caption, color = colors.textMuted)
                }
            }
        }
    }
}
