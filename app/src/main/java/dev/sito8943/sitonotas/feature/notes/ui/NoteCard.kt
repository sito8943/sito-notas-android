package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.Note
import dev.sito8943.sitonotas.ui.elements.CategoryChip
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatNoteDate
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** Tarjeta de nota de la lista. Las acciones van en [trailing]. */
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = NotesTheme.colors
    Surface(modifier = modifier.fillMaxWidth(), shape = Radius.mdShape, color = colors.surface) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = spacing(4), top = spacing(3), bottom = spacing(3), end = spacing(1)),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing(1)),
            ) {
                NotesText(
                    text = note.title.ifBlank { stringResource(R.string.note_untitled) },
                    style = NotesTheme.typography.bodyStrong,
                    color = if (note.title.isBlank()) colors.textMuted else colors.textStrong,
                    maxLines = 1,
                )
                val preview = notePreview(note.content)
                if (preview.isNotEmpty()) {
                    NotesText(text = preview, color = colors.textMuted, maxLines = 3)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing(2)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = spacing(1)),
                ) {
                    note.categories.take(MAX_CHIPS).forEach { CategoryChip(it.name, it.color) }
                    if (note.categories.size > MAX_CHIPS) {
                        NotesText(
                            "+${note.categories.size - MAX_CHIPS}",
                            style = NotesTheme.typography.caption,
                            color = colors.textMuted,
                        )
                    }
                    NotesText(
                        text = formatNoteDate(note.deletedAt ?: note.updatedAt),
                        style = NotesTheme.typography.caption,
                        color = colors.textSubtle,
                    )
                    if (note.pendingSync) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = stringResource(R.string.note_pending_sync),
                            tint = colors.textSubtle,
                            modifier = Modifier.size(spacing(4)),
                        )
                    }
                }
            }
            if (onToggleFavorite != null) {
                NotesIconButton(
                    icon = if (note.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (note.favorite) R.string.note_unfavorite else R.string.note_favorite,
                    ),
                    onClick = onToggleFavorite,
                    tint = if (note.favorite) colors.warning else colors.textMuted,
                    size = IconButtonSize.LG,
                )
            }
            trailing()
        }
    }
}

/** Texto plano para la tarjeta: sin marcas de Markdown ni lineas vacias. */
internal fun notePreview(content: String): String = content
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("```") }
    .map { line ->
        line.replace(Regex("^(#{1,6}|[-*+]\\s+\\[[ xX]?]|[-*+]|\\d+[.)]|>)\\s*"), "")
            .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("[*_~`]"), "")
    }
    .take(PREVIEW_LINES)
    .joinToString(" · ")

private const val MAX_CHIPS = 2
private const val PREVIEW_LINES = 4
