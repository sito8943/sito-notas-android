package dev.sito8943.sitonotas.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.ui.foundations.BorderWidth
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** `#RRGGBB` (o `#RGB`, `#RRGGBBAA`) a color; nulo si no se entiende. */
fun parseHexColor(hex: String?): Color? {
    val value = hex?.removePrefix("#") ?: return null
    val expanded = when (value.length) {
        3 -> value.map { "$it$it" }.joinToString("") + "FF"
        6 -> value + "FF"
        8 -> value
        else -> return null
    }
    val rgba = expanded.toLongOrNull(16) ?: return null
    return Color(
        red = ((rgba shr 24) and 0xFF).toInt(),
        green = ((rgba shr 16) and 0xFF).toInt(),
        blue = ((rgba shr 8) and 0xFF).toInt(),
        alpha = (rgba and 0xFF).toInt(),
    )
}

@Composable
fun ColorDot(color: String?, modifier: Modifier = Modifier, size: Int = 10) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(parseHexColor(color) ?: NotesTheme.colors.textSubtle),
    )
}

/** Etiqueta de categoria: punto de color y nombre. Seleccionable si hay [onClick]. */
@Composable
fun CategoryChip(
    name: String,
    color: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = NotesTheme.colors
    Row(
        modifier = modifier
            .clip(Radius.fullShape)
            .background(if (selected) colors.accent.copy(alpha = 0.15f) else colors.surface)
            .border(BorderWidth.thin, if (selected) colors.accent else colors.border, Radius.fullShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = spacing(3), vertical = spacing(1)),
        horizontalArrangement = Arrangement.spacedBy(spacing(2)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color)
        NotesText(
            text = name,
            style = NotesTheme.typography.caption,
            color = if (selected) colors.accent else colors.textStrong,
            maxLines = 1,
        )
    }
}
