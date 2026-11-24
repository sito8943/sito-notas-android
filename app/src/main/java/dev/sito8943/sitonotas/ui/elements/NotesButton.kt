package dev.sito8943.sitonotas.ui.elements

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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

/**
 * Variantes del `Button` de @sito/ui: `FILLED` es `submit`, `OUTLINED` y
 * `TEXT` igual que alli.
 */
enum class ButtonVariant { FILLED, OUTLINED, TEXT }

enum class ButtonColor { DEFAULT, PRIMARY, SECONDARY, DANGER, SUCCESS, WARNING }

/**
 * Boton en pastilla (`--sito-ui-radius-pill`), texto seminegrita. `loading`
 * bloquea la pulsacion ademas de mostrar el spinner: si no, un doble toque
 * manda dos peticiones. Alto minimo 48dp (area tactil de Android).
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
    @DrawableRes icon: Int? = null,
) {
    val colors = NotesTheme.colors
    val isEnabled = enabled && !loading

    val container = when (variant) {
        ButtonVariant.FILLED -> colors.fillFor(color)
        ButtonVariant.OUTLINED, ButtonVariant.TEXT -> Color.Transparent
    }
    val content = when (variant) {
        ButtonVariant.FILLED -> if (color == ButtonColor.WARNING) Color.Black else colors.textInverted
        ButtonVariant.OUTLINED, ButtonVariant.TEXT ->
            if (color == ButtonColor.DEFAULT) colors.textStrong else colors.inkFor(color)
    }
    val border = when (variant) {
        ButtonVariant.OUTLINED -> BorderStroke(
            BorderWidth.thin,
            if (color == ButtonColor.DEFAULT) colors.border else colors.inkFor(color),
        )
        ButtonVariant.FILLED, ButtonVariant.TEXT -> null
    }

    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MIN_HEIGHT),
        enabled = isEnabled,
        shape = Radius.fullShape,
        border = border,
        contentPadding = PaddingValues(horizontal = spacing(5), vertical = spacing(2)),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * DISABLED_ALPHA),
            disabledContentColor = content.copy(alpha = DISABLED_ALPHA),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // El contenido se mantiene en el arbol aunque este cargando, para
            // que el boton no cambie de ancho al empezar y acabar la peticion.
            val shown = if (loading) Color.Transparent else if (isEnabled) content else content.copy(alpha = DISABLED_ALPHA)
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing(2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) FaIcon(icon, contentDescription = null, tint = shown, size = 16.dp)
                NotesText(text = text, style = NotesTheme.typography.button, color = shown, maxLines = 1)
            }
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

/** Relleno (`--sito-ui-color-*-default`). */
private fun NotesColors.fillFor(color: ButtonColor): Color = when (color) {
    ButtonColor.DANGER -> negative
    ButtonColor.SUCCESS -> positive
    ButtonColor.WARNING -> warning
    ButtonColor.DEFAULT, ButtonColor.PRIMARY, ButtonColor.SECONDARY -> primary
}

/**
 * Color como tinta sobre la pagina. La marca usa `accent` (`--color-text-primary`)
 * como hace la web en botones de texto y contorno: el relleno no se lee en oscuro.
 */
private fun NotesColors.inkFor(color: ButtonColor): Color = when (color) {
    ButtonColor.DANGER -> negative
    ButtonColor.SUCCESS -> positive
    ButtonColor.WARNING -> warning
    ButtonColor.DEFAULT, ButtonColor.PRIMARY, ButtonColor.SECONDARY -> accent
}

private val MIN_HEIGHT = 48.dp
private const val DISABLED_ALPHA = 0.5f
