package dev.sito8943.sitonotas.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement as LayoutArrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Andamiaje de pagina: fondo, titulo y contenido. Portado de
 * `shared/design/templates/Page`.
 *
 * No aplica safeDrawingPadding: de eso se encarga el Scaffold que la contiene,
 * que ya conoce la barra de pestañas.
 *
 * `onBack` dibuja la flecha de volver junto al titulo. Las pantallas que se
 * apilan (los formularios) la pasan: el gesto del sistema existe, pero sin un
 * control visible no hay forma de saber que se puede salir sin guardar.
 *
 * `leading` ocupa ese mismo hueco cuando la pantalla no se apila: una accion
 * pegada al titulo en vez de al borde final.
 */
@Composable
fun Page(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NotesTheme.colors.background),
        verticalArrangement = Arrangement.spacedBy(spacing(3)),
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (onBack != null || leading != null) spacing(2) else spacing(5),
                end = spacing(5),
                top = spacing(4),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                NotesIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    onClick = onBack,
                    tint = NotesTheme.colors.textStrong,
                    size = IconButtonSize.LG,
                )
            }
            leading?.invoke()
            NotesText(
                text = title,
                style = NotesTheme.typography.title,
                modifier = Modifier.weight(1f),
            )
            // Al borde final del titulo, no en una fila propia debajo: son
            // acciones de la pantalla, no contenido.
            actions()
        }
        content()
    }
}

/** Estado vacio. Portado de `templates/Empty`. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    NotesText(
        text = text,
        modifier = modifier.padding(horizontal = spacing(5), vertical = spacing(6)),
        style = NotesTheme.typography.body,
        color = NotesTheme.colors.textMuted,
    )
}
