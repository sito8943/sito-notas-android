package dev.sito8943.sitonotas.feature.trash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.Note
import dev.sito8943.sitonotas.feature.notes.ui.NoteCard
import dev.sito8943.sitonotas.feature.trash.TrashConfirmation
import dev.sito8943.sitonotas.feature.trash.TrashViewModel
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatNoteDate
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesConfirmationDialog
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Papelera, como `Trash` en la web: titulo con su descripcion, boton de
 * vaciar en contorno rojo y tarjetas con cuando se borran para siempre.
 */
@Composable
fun TrashScreen(viewModel: TrashViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors

    Page(
        title = stringResource(R.string.trash_title),
        description = stringResource(R.string.trash_hint),
    ) {
        when {
            state.loading -> Unit
            state.notes.isEmpty() -> EmptyState(
                text = stringResource(R.string.trash_empty),
                icon = R.drawable.fa_trash_can_regular,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = spacing(4), end = spacing(4), bottom = spacing(6)),
                verticalArrangement = Arrangement.spacedBy(spacing(4)),
            ) {
                item(key = "empty-trash") {
                    NotesButton(
                        text = stringResource(R.string.trash_empty_action),
                        onClick = viewModel::askEmpty,
                        variant = ButtonVariant.OUTLINED,
                        color = ButtonColor.DANGER,
                    )
                }
                items(state.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = null,
                        modifier = Modifier.alpha(TRASHED_ALPHA),
                        showCategories = false,
                        meta = { PurgeMeta(note) },
                        actions = {
                            NotesIconButton(
                                icon = R.drawable.fa_rotate_left,
                                contentDescription = stringResource(R.string.trash_restore),
                                onClick = { viewModel.restore(note) },
                                tint = colors.accent,
                                size = IconButtonSize.SM,
                            )
                            NotesIconButton(
                                icon = R.drawable.fa_trash,
                                contentDescription = stringResource(R.string.trash_delete_forever),
                                onClick = { viewModel.askDelete(note) },
                                tint = colors.negative,
                                size = IconButtonSize.SM,
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

/** "Borrada hace…" y los dias que le quedan; en rojo los ultimos 3 (`trash-note-purge.soon`). */
@Composable
private fun PurgeMeta(note: Note) {
    val colors = NotesTheme.colors
    val deletedAt = note.deletedAt ?: note.updatedAt
    val daysLeft = daysUntilPurge(deletedAt, System.currentTimeMillis())
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        NotesText(
            stringResource(R.string.trash_deleted_at, formatNoteDate(deletedAt)),
            style = NotesTheme.typography.caption,
            color = colors.textMuted,
        )
        val soon = daysLeft <= SOON_DAYS
        NotesText(
            text = if (daysLeft == 0) {
                stringResource(R.string.trash_purge_today)
            } else {
                pluralStringResource(R.plurals.trash_purge_in, daysLeft, daysLeft)
            },
            style = NotesTheme.typography.caption.let { if (soon) it.copy(fontWeight = FontWeight.SemiBold) else it },
            color = if (soon) colors.negative else colors.textMuted,
        )
    }
}

/** Dias enteros hasta la purga del servidor (`getDaysUntilPurge` en la web). */
internal fun daysUntilPurge(deletedAt: Long, now: Long): Int =
    (RETENTION_DAYS - maxOf(0L, now - deletedAt) / DAY_MILLIS).coerceAtLeast(0L).toInt()

private const val TRASHED_ALPHA = 0.9f
private const val SOON_DAYS = 3
/** `app.notes.trash.retention-days` del backend (el mismo 30 de `trash_hint`). */
private const val RETENTION_DAYS = 30L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
