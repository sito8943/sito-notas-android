package dev.sito8943.sitonotas.ui.elements

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Portado del elemento `Typography`. Envuelve el Text de Material3 para que el
 * color por defecto salga de la paleta semantica y no de MaterialTheme, que no
 * se usa como fuente de color.
 */
@Composable
fun NotesText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NotesTheme.typography.body,
    color: Color = NotesTheme.colors.textStrong,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = align,
        maxLines = maxLines,
        overflow = overflow,
    )
}
