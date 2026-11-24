package dev.sito8943.sitonotas.ui.elements

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Icono de Font Awesome Free 6.3.0 (los mismos que usa la web), vendorizado
 * como VectorDrawable en `res/drawable/fa_*.xml`.
 *
 * Como `FontAwesomeIcon`, el alto es [size] y el ancho sale de la proporcion
 * del glifo: los iconos anchos (`fa_eye_slash`, 640x512) no se encogen para
 * caber en un cuadrado.
 */
@Composable
fun FaIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = NotesTheme.colors.textMuted,
    size: Dp = 20.dp,
) {
    val painter = painterResource(icon)
    val intrinsic = painter.intrinsicSize
    val aspect = if (intrinsic.height > 0f) intrinsic.width / intrinsic.height else 1f
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(width = size * aspect, height = size),
        tint = tint,
    )
}
