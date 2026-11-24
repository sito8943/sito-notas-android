package dev.sito8943.sitonotas.ui.elements

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.R
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

/** Punto de color de una categoria; gris apagado si no tiene (como la web). */
@Composable
fun ColorDot(color: String?, modifier: Modifier = Modifier, size: Int = 8) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(parseHexColor(color) ?: NotesTheme.colors.textMuted),
    )
}

/**
 * Categoria de una nota, como `TagChip` de la web: esquinas suaves (no
 * pastilla), borde, punto de color y nombre. Con [onRemove] lleva una X.
 */
@Composable
fun CategoryChip(
    name: String,
    color: String?,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
) {
    val colors = NotesTheme.colors
    Row(
        modifier = modifier
            .clip(Radius.mdShape)
            .background(colors.surface)
            .border(BorderWidth.thin, colors.border, Radius.mdShape)
            .padding(start = spacing(2), end = if (onRemove != null) 0.dp else spacing(2)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color)
        NotesText(
            text = name,
            modifier = Modifier.padding(vertical = 2.dp),
            style = NotesTheme.typography.caption,
            color = colors.textStrong,
            maxLines = 1,
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(R.drawable.fa_xmark, contentDescription = removeLabel, size = 12.dp)
            }
        }
    }
}

/**
 * Pestaña de filtro, como `Tabs` de la web: chip de esquinas suaves con borde;
 * la seleccionada va rellena con la marca.
 */
@Composable
fun FilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NotesTheme.colors
    val container = if (selected) colors.primary else colors.surface
    val content = if (selected) colors.textInverted else colors.textMuted
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(Radius.mdShape)
            .background(container)
            .border(BorderWidth.thin, if (selected) colors.primary else colors.border, Radius.mdShape)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = spacing(3)),
        contentAlignment = Alignment.Center,
    ) {
        NotesText(
            text = label,
            style = NotesTheme.typography.small,
            color = if (enabled) content else content.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
}

/**
 * Chip de accion con borde discontinuo ("una accion, no una etiqueta" en la
 * web): añadir categoria a la nota, ir a gestionar categorias.
 */
@Composable
fun DashedChip(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentDescription: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = 28.dp,
) {
    val colors = NotesTheme.colors
    val dash = colors.border
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(Radius.mdShape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(Radius.md.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = if (label != null) spacing(2) else spacing(0)),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, contentDescription = if (label == null) contentDescription else null, size = 12.dp)
        if (label != null) {
            NotesText(label, style = NotesTheme.typography.caption, color = colors.textMuted, maxLines = 1)
        }
    }
}
