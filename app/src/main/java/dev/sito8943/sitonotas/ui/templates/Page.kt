package dev.sito8943.sitonotas.ui.templates

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Andamiaje de las pestañas: fondo de pagina, titulo en Poppins (como el
 * `h1` de las paginas de la web: `trash-title`, `tags-title`) con su
 * descripcion opcional debajo, y el contenido.
 *
 * No aplica safeDrawingPadding: de eso se encarga el Scaffold que la contiene,
 * que ya conoce la barra de pestañas.
 */
@Composable
fun Page(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NotesTheme.colors.background),
        verticalArrangement = Arrangement.spacedBy(spacing(4)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing(4)).padding(top = spacing(3)),
            verticalArrangement = Arrangement.spacedBy(spacing(1)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotesText(
                    text = title,
                    style = NotesTheme.typography.display,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // Al borde final del titulo: son acciones de la pantalla.
                actions()
            }
            if (description != null) {
                NotesText(
                    text = description,
                    style = NotesTheme.typography.small,
                    color = NotesTheme.colors.textMuted,
                )
            }
        }
        content()
    }
}

/**
 * Estado vacio, como `trash-empty` / `tags-empty` de la web: icono grande y
 * texto apagado, centrados.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing(6), vertical = spacing(16)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing(3)),
    ) {
        if (icon != null) FaIcon(icon, contentDescription = null, size = 48.dp)
        NotesText(
            text = text,
            style = NotesTheme.typography.body,
            color = NotesTheme.colors.textMuted,
            align = TextAlign.Center,
        )
    }
}
