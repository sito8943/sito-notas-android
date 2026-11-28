package dev.sito8943.sitonotas.feature.notes.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.network.ImageLoadException
import dev.sito8943.sitonotas.core.network.ImageLoader
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data object Failed : ImageLoad
    data class Ready(val bitmap: Bitmap) : ImageLoad
}

/** Carga [url] a [widthPx] de ancho; [attempt] cambia para reintentar. */
@Composable
private fun rememberImage(loader: ImageLoader, url: String, widthPx: Int, attempt: Int): ImageLoad {
    val initial = loader.cached(url, widthPx)?.let { ImageLoad.Ready(it) } ?: ImageLoad.Loading
    val state by produceState<ImageLoad>(initial, loader, url, widthPx, attempt) {
        if (value is ImageLoad.Ready) return@produceState
        value = ImageLoad.Loading
        value = try {
            ImageLoad.Ready(loader.load(url, widthPx))
        } catch (error: ImageLoadException) {
            ImageLoad.Failed
        }
    }
    return state
}

/**
 * Bloque de imagen del editor, como `.note-editor-image` de la web: la imagen
 * a todo el ancho con las esquinas redondeadas. Mientras carga, un hueco con
 * indicador; si falla, un aviso que reintenta al tocarlo. Tocar la imagen la
 * abre a pantalla completa; la X la quita de la nota.
 */
@Composable
fun NoteImageBlock(
    url: String,
    alt: String,
    loader: ImageLoader,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NotesTheme.colors
    var attempt by remember(url) { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val load = rememberImage(loader, url, widthPx, attempt)
        val openLabel = stringResource(R.string.editor_image_open)
        when (load) {
            is ImageLoad.Ready -> Image(
                bitmap = remember(load.bitmap) { load.bitmap.asImageBitmap() },
                contentDescription = alt.ifBlank { stringResource(R.string.editor_image) },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radius.lgShape)
                    .focusProperties { canFocus = false }
                    .clickable(role = Role.Image, onClickLabel = openLabel, onClick = onOpen),
            )
            ImageLoad.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .background(colors.surfaceRecessed, Radius.lgShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            ImageLoad.Failed -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .background(colors.surfaceRecessed, Radius.lgShape)
                    .focusProperties { canFocus = false }
                    .clickable { attempt++ }
                    .padding(spacing(4)),
                contentAlignment = Alignment.Center,
            ) {
                NotesText(
                    stringResource(R.string.editor_image_failed),
                    style = NotesTheme.typography.small,
                    color = colors.textMuted,
                )
            }
        }
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing(2))
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .focusProperties { canFocus = false }
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.editor_image_remove), onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(R.drawable.fa_xmark, contentDescription = stringResource(R.string.editor_image_remove), tint = Color.White, size = 14.dp)
            }
        }
    }
}

/** Imagen a pantalla completa, con pellizco para ampliar y doble toque. */
@Composable
fun ImageViewerDialog(url: String, alt: String, loader: ImageLoader, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val widthPx = LocalWindowInfo.current.containerSize.width.coerceAtLeast(1)
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (val load = rememberImage(loader, url, widthPx, 0)) {
                is ImageLoad.Ready -> Image(
                    bitmap = remember(load.bitmap) { load.bitmap.asImageBitmap() },
                    contentDescription = alt.ifBlank { stringResource(R.string.editor_image) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                                offset = Offset.Zero
                            })
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
                ImageLoad.Loading -> CircularProgressIndicator(color = Color.White)
                ImageLoad.Failed -> NotesText(stringResource(R.string.editor_image_failed), color = Color.White)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(spacing(3))
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(R.drawable.fa_xmark, contentDescription = stringResource(R.string.common_back), tint = Color.White, size = 18.dp)
            }
        }
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
