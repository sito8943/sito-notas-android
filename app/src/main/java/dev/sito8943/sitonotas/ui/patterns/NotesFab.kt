package dev.sito8943.sitonotas.ui.patterns

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Boton flotante de Android con el aspecto del "+" de la web (boton redondo
 * relleno con la marca, `faPlus`, sombra). Tamaño y posicion son los de un FAB.
 */
@Composable
fun NotesFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.fa_plus,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = NotesTheme.colors.primary,
        contentColor = NotesTheme.colors.textInverted,
    ) {
        FaIcon(icon, contentDescription = contentDescription, tint = NotesTheme.colors.textInverted, size = 22.dp)
    }
}
