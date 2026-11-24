package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.Note
import dev.sito8943.sitonotas.ui.elements.CategoryChip
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatNoteDate
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Tarjeta de nota, como `PreviewNote` de la web: `rounded-xl`, superficie,
 * titulo en Poppins, extracto con formato ([NotePreview]), fecha y un pie con las categorias y
 * las acciones.
 *
 * [meta] sustituye a la fecha (la papelera muestra cuando se borra) y
 * [actions] son los botones del pie. Sin categorias, la meta baja al pie.
 */
@Composable
fun NoteCard(
    note: Note,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showCategories: Boolean = true,
    /** Alto maximo del extracto: el de la lista o el de la papelera. */
    previewHeight: Dp = LIST_PREVIEW_HEIGHT,
    meta: @Composable () -> Unit = { NoteDate(note) },
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NotesTheme.colors
    Surface(modifier = modifier.fillMaxWidth(), shape = Radius.lgShape, color = colors.surface) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = spacing(5), end = spacing(2), top = spacing(5), bottom = spacing(2)),
            verticalArrangement = Arrangement.spacedBy(spacing(3)),
        ) {
            Column(
                modifier = Modifier.padding(end = spacing(3)),
                verticalArrangement = Arrangement.spacedBy(spacing(3)),
            ) {
                NotesText(
                    text = note.title.ifBlank { stringResource(R.string.note_untitled) },
                    style = NotesTheme.typography.cardTitle,
                    color = colors.textStrong,
                    maxLines = 2,
                )
                NotePreview(note.content, maxHeight = previewHeight)
                if (showCategories) meta()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing(1)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!showCategories) {
                        // Sin categorias el pie lleva la meta junto a las
                        // acciones, como `trash-note-footer`.
                        meta()
                    } else {
                        note.categories.take(MAX_CHIPS).forEach {
                            CategoryChip(it.name, it.color, modifier = Modifier.weight(1f, fill = false))
                        }
                        if (note.categories.size > MAX_CHIPS) {
                            NotesText(
                                "+${note.categories.size - MAX_CHIPS}",
                                style = NotesTheme.typography.caption,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
                actions()
            }
        }
    }
}

/** Fecha de la tarjeta (`preview-note-date`) y el aviso de pendiente de subir. */
@Composable
private fun NoteDate(note: Note) {
    val colors = NotesTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing(2)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotesText(
            text = formatNoteDate(note.updatedAt),
            style = NotesTheme.typography.caption,
            color = colors.textMuted,
        )
        if (note.pendingSync) {
            FaIcon(
                R.drawable.fa_cloud_arrow_up,
                contentDescription = stringResource(R.string.note_pending_sync),
                tint = colors.textMuted,
                size = 12.dp,
            )
        }
    }
}

private const val MAX_CHIPS = 2
