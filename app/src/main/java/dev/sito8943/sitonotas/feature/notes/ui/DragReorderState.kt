package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Arrastrar para reordenar en una rejilla escalonada (sirve tambien de lista
 * con una columna), como `useGridDrag` en la web: mantener pulsado arranca el
 * arrastre y la tarjeta bajo el dedo es el destino.
 *
 * Todo va en coordenadas del viewport: la tarjeta arrastrada se pinta donde
 * esta el dedo aunque la lista cambie de orden o se desplace sola.
 */
@Stable
class DragReorderState(
    private val gridState: LazyStaggeredGridState,
    private val scope: CoroutineScope,
    private val onMove: (from: Any, to: Any) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** Esquina de la tarjeta al empezar, en el viewport. */
    private var startOffset = Offset.Zero
    private var delta by mutableStateOf(Offset.Zero)
    private var autoScroll: Job? = null
    private var lastTarget: Any? = null

    /** Desplazamiento a aplicar a la tarjeta [key] para que siga al dedo. */
    fun translationOf(key: Any): Offset {
        if (key != draggingKey) return Offset.Zero
        val info = itemInfo(key) ?: return delta
        return startOffset + delta - info.offset.toOffset()
    }

    internal fun start(position: Offset): Boolean {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(position) } ?: return false
        draggingKey = item.key
        lastTarget = null
        startOffset = item.offset.toOffset()
        delta = Offset.Zero
        return true
    }

    internal fun drag(amount: Offset) {
        val key = draggingKey ?: return
        delta += amount
        val info = itemInfo(key) ?: return
        val center = startOffset + delta + Offset(info.size.width / 2f, info.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key != key && it.contains(center) }
            ?.key
        // Tras cambiar con una tarjeta de otro alto el centro puede seguir
        // encima de ella: no se vuelve a cambiar hasta salir.
        if (target != null && target != lastTarget) onMove(key, target)
        lastTarget = target
        if (overscroll() != 0f && autoScroll?.isActive != true) {
            autoScroll = scope.launch {
                while (isActive && draggingKey != null) {
                    val amountToScroll = overscroll()
                    if (amountToScroll == 0f) break
                    gridState.scrollBy(amountToScroll * AUTO_SCROLL_FACTOR)
                    // Al desplazar cambia lo que hay bajo la tarjeta.
                    drag(Offset.Zero)
                    delay(FRAME_MILLIS)
                }
            }
        }
    }

    internal fun end(drop: Boolean) {
        val wasDragging = draggingKey != null
        autoScroll?.cancel()
        draggingKey = null
        delta = Offset.Zero
        if (wasDragging && drop) onDrop()
    }

    /** Cuanto se sale la tarjeta arrastrada por arriba (<0) o por abajo (>0). */
    private fun overscroll(): Float {
        val key = draggingKey ?: return 0f
        val info = itemInfo(key) ?: return 0f
        val layout = gridState.layoutInfo
        val top = (startOffset + delta).y
        val bottom = top + info.size.height
        return when {
            bottom > layout.viewportEndOffset -> bottom - layout.viewportEndOffset
            top < layout.viewportStartOffset -> top - layout.viewportStartOffset
            else -> 0f
        }
    }

    private fun itemInfo(key: Any): LazyStaggeredGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun LazyStaggeredGridItemInfo.contains(point: Offset): Boolean =
        point.x >= offset.x && point.x <= offset.x + size.width &&
            point.y >= offset.y && point.y <= offset.y + size.height

    private companion object {
        const val AUTO_SCROLL_FACTOR = 0.25f
        const val FRAME_MILLIS = 16L
    }
}

@Composable
fun rememberDragReorderState(
    gridState: LazyStaggeredGridState,
    onMove: (from: Any, to: Any) -> Unit,
    onDrop: () -> Unit,
): DragReorderState {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    return remember(gridState) {
        DragReorderState(gridState, scope, onMove = { a, b -> move(a, b) }, onDrop = { drop() })
    }
}

/**
 * Mantener pulsado y arrastrar. Va en el contenedor de la rejilla, no en cada
 * tarjeta, para que las coordenadas sean las del viewport.
 */
@Composable
fun Modifier.dragToReorder(state: DragReorderState, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val haptics = LocalHapticFeedback.current
    return pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { if (state.start(it)) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            onDrag = { change, amount ->
                if (state.draggingKey != null) {
                    change.consume()
                    state.drag(amount)
                }
            },
            onDragEnd = { state.end(drop = true) },
            onDragCancel = { state.end(drop = true) },
        )
    }
}
