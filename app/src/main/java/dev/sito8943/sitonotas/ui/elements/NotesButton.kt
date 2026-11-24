package dev.sito8943.sitonotas.ui.elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.ui.foundations.BorderWidth
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesColors
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** Portado de `Button/constants.ts`. */
enum class ButtonVariant { FILLED, OUTLINED, TEXT }

enum class ButtonColor { DEFAULT, PRIMARY, SECONDARY, DANGER, SUCCESS, WARNING }

/**
 * Portado del elemento `Button`. Igual que alli, `loading` bloquea la pulsacion
 * ademas de mostrar el spinner: si no, un doble toque manda dos peticiones.
 */
@Composable
fun NotesButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.FILLED,
    color: ButtonColor = ButtonColor.DEFAULT,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = NotesTheme.colors
    val semantic = colors.semanticFor(color)
    val isEnabled = enabled && !loading

    val container = when (variant) {
        ButtonVariant.FILLED -> semantic
        ButtonVariant.OUTLINED, ButtonVariant.TEXT -> Color.Transparent
    }
    val content = when (variant) {
        ButtonVariant.FILLED -> colors.textInverted
        ButtonVariant.OUTLINED, ButtonVariant.TEXT ->
            if (color == ButtonColor.DEFAULT) colors.textStrong else semantic
    }
    val border = when (variant) {
        ButtonVariant.OUTLINED -> BorderStroke(
            BorderWidth.thin,
            if (color == ButtonColor.DEFAULT) colors.border else semantic,
        )
        ButtonVariant.FILLED, ButtonVariant.TEXT -> null
    }

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MIN_HEIGHT),
        enabled = isEnabled,
        shape = Radius.mdShape,
        border = border,
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // Material aplica su propio alpha a lo deshabilitado; se replica el
            // 0.5 de la app RN manteniendo el mismo color.
            disabledContainerColor = container.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = content.copy(alpha = DISABLED_ALPHA),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // El texto se mantiene en el arbol aunque este cargando, para que el
            // boton no cambie de ancho al empezar y acabar la peticion.
            NotesText(
                text = text,
                style = NotesTheme.typography.bodyStrong,
                color = if (loading) Color.Transparent else content,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spacing(5)),
                    color = content,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/** Portado de `getSemanticColor` en `Button/utils.ts`. */
private fun NotesColors.semanticFor(color: ButtonColor): Color = when (color) {
    ButtonColor.SECONDARY -> accent
    ButtonColor.DANGER -> negative
    ButtonColor.SUCCESS -> positive
    ButtonColor.WARNING -> warning
    ButtonColor.DEFAULT, ButtonColor.PRIMARY -> primary
}

private val MIN_HEIGHT = 48.dp
private const val DISABLED_ALPHA = 0.5f
