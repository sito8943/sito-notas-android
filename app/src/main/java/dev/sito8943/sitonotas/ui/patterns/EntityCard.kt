package dev.sito8943.sitonotas.ui.patterns

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Accion de fila. Portado de `interactions/types.ts` -> `Action<T>`, sin el
 * generico: aqui la entidad la captura el lambda del que crea la accion.
 */
data class EntityAction(
    @param:DrawableRes val icon: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val tint: Color? = null,
    val enabled: Boolean = true,
    /**
     * Con entradas, el boton no actua: abre un menu con ellas. Es el "⋮" de
     * las tarjetas, para no llenarlas de iconos.
     */
    val menu: List<EntityMenuItem> = emptyList(),
)

/** Una entrada del menu de una [EntityAction]. */
data class EntityMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

/**
 * Fila en tarjeta (`rounded-xl bg-base-light`, como `TagRow` de la web).
 * Portado del patron `EntityCard`: contenido pulsable mas una fila de acciones
 * en linea. Las acciones viven en la propia tarjeta para que editar y borrar no
 * obliguen a entrar en el detalle.
 */
@Composable
fun EntityCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actions: List<EntityAction> = emptyList(),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = NotesTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.lgShape,
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = spacing(4), top = spacing(2), bottom = spacing(2), end = spacing(1)),
            horizontalArrangement = Arrangement.spacedBy(spacing(3)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()

            actions.forEach { action ->
                NotesIconButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    tint = action.tint ?: colors.textMuted,
                    size = IconButtonSize.MD,
                    enabled = action.enabled,
                )
            }
        }
    }
}
