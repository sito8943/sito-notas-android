package dev.sito8943.sitonotas.ui.patterns

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Portado del patron `FAB`. Con `label` se estira y lleva texto: es para
 * acciones que no se adivinan solo por el icono, como terminar de ordenar.
 */
@Composable
fun NotesFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
    label: String? = null,
) {
    if (label != null) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = Radius.fullShape,
            containerColor = NotesTheme.colors.primary,
            contentColor = NotesTheme.colors.textInverted,
            icon = { Icon(icon, contentDescription = null) },
            text = { NotesText(text = label, color = NotesTheme.colors.textInverted) },
        )
        return
    }
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = Radius.fullShape,
        containerColor = NotesTheme.colors.primary,
        contentColor = NotesTheme.colors.textInverted,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
