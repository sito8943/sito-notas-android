package dev.sito8943.sitonotas.ui.elements

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** Portado de `IconButton/constants.ts`. */
enum class IconButtonSize { SM, MD, LG }

/**
 * Portado del elemento `IconButton`. Solo la variante de texto, que es la unica
 * que usan las acciones de fila en la app RN.
 */
@Composable
fun NotesIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NotesTheme.colors.textMuted,
    size: IconButtonSize = IconButtonSize.MD,
    enabled: Boolean = true,
) {
    val iconSize = when (size) {
        IconButtonSize.SM -> 16.dp
        IconButtonSize.MD -> 20.dp
        IconButtonSize.LG -> 24.dp
    }

    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = NotesTheme.colors.textSubtle,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}
