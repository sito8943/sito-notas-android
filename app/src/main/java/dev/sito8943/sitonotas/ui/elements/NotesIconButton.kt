package dev.sito8943.sitonotas.ui.elements

import androidx.annotation.DrawableRes
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** Tamaños del icono, como `ICON_BUTTON_SIZES` de @sito/ui (16/20/24). */
enum class IconButtonSize { SM, MD, LG }

/**
 * Boton de icono redondo, como el `IconButton` de @sito/ui. El glifo sigue el
 * tamaño de la web pero el area tactil es la de Android (48dp, con ripple).
 */
@Composable
fun NotesIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NotesTheme.colors.textMuted,
    size: IconButtonSize = IconButtonSize.MD,
    enabled: Boolean = true,
) {
    val iconSize = when (size) {
        IconButtonSize.SM -> 16.dp
        IconButtonSize.MD -> 18.dp
        IconButtonSize.LG -> 22.dp
    }

    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        FaIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else NotesTheme.colors.textSubtle.copy(alpha = DISABLED_ALPHA),
            size = iconSize,
        )
    }
}

private const val DISABLED_ALPHA = 0.5f
