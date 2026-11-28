package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sito8943.sitonotas.feature.notes.Note
import dev.sito8943.sitonotas.feature.notes.NoteListRules
import dev.sito8943.sitonotas.feature.notes.NotesViewMode
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing

/**
 * Las tarjetas, como `NotesGrid` de la web: rejilla escalonada cuyas columnas
 * dependen del ancho, y mantener pulsado + arrastrar para reordenar (siempre,
 * tambien con busqueda o filtro: las ocultas conservan su hueco).
 *
 * En [NotesViewMode.LIST] es una columna; en [NotesViewMode.GRID], las de la web
 * para ese ancho con un minimo de dos (en un telefono la web daria una).
 */
@Composable
fun NotesGrid(
    notes: List<Note>,
    viewMode: NotesViewMode,
    onOpenNote: (Long) -> Unit,
    onReordered: (List<Long>) -> Unit,
    actions: @Composable RowScope.(Note) -> Unit,
) {
    // Orden durante el arrastre y hasta que Room devuelva el definitivo.
    var localOrder by remember { mutableStateOf<List<Long>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(notes) { if (!dragging) localOrder = null }

    val shown = localOrder?.let { order ->
        val byId = notes.associateBy { it.id }
        order.mapNotNull { byId[it] } + notes.filter { it.id !in order }
    } ?: notes

    val gridState = rememberLazyStaggeredGridState()
    val dragState = rememberDragReorderState(
        gridState = gridState,
        onMove = { from, to ->
            dragging = true
            localOrder = NoteListRules.move(shown.map { it.id }, from as Long, to as Long)
        },
        onDrop = {
            dragging = false
            localOrder?.let(onReordered)
        },
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when (viewMode) {
            NotesViewMode.LIST -> 1
            NotesViewMode.GRID -> maxOf(2, webColumns(maxWidth))
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = gridState,
            // El margen lateral va como padding del contenedor (no del
            // contenido) para que el dedo y las tarjetas compartan coordenadas.
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing(4))
                .dragToReorder(dragState),
            contentPadding = PaddingValues(bottom = spacing(24)),
            verticalItemSpacing = spacing(4),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing(4)),
        ) {
            items(shown, key = { it.id }) { note ->
                val isDragged = dragState.draggingKey == note.id
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    modifier = if (isDragged) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                val offset = dragState.translationOf(note.id)
                                translationX = offset.x
                                translationY = offset.y
                                scaleX = DRAG_SCALE
                                scaleY = DRAG_SCALE
                            }
                            .shadow(8.dp, Radius.lgShape)
                    } else {
                        Modifier.animateItem()
                    },
                    actions = { actions(note) },
                )
            }
        }
    }
}

/** `NotesGrid/constants.ts`: <640 -> 1, <1024 -> 2, <1280 -> 3, si no 4. */
private fun webColumns(width: Dp): Int = when {
    width < 640.dp -> 1
    width < 1024.dp -> 2
    width < 1280.dp -> 3
    else -> 4
}

private const val DRAG_SCALE = 1.02f
