package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.editor.InlineMarks
import dev.sito8943.sitonotas.feature.notes.editor.MarkType
import dev.sito8943.sitonotas.feature.notes.editor.NotePreviewModel
import dev.sito8943.sitonotas.feature.notes.editor.PreviewItem
import dev.sito8943.sitonotas.feature.notes.editor.PreviewKind
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.theme.NotesColors
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Principio de una nota con formato, de solo lectura, como `MarkdownPreview`
 * de la web en las tarjetas: texto pequeño y apagado, alto maximo
 * (`max-h-40` en la lista, `max-h-28` en la papelera) y, si no cabe, el final
 * se desvanece. Las casillas no se pueden marcar desde aqui (en la web
 * tampoco): tocar la tarjeta abre la nota.
 */
@Composable
fun NotePreview(content: String, modifier: Modifier = Modifier, maxHeight: Dp = LIST_PREVIEW_HEIGHT) {
    val items = remember(content) { NotePreviewModel.build(content) }
    if (items.isEmpty()) return
    val colors = NotesTheme.colors
    val base = NotesTheme.typography.small.copy(
        color = colors.textMuted,
        lineHeight = 23.sp,
        // Glifo centrado en la linea para que casillas y viñetas queden alineadas.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    var overflows by remember { mutableStateOf(false) }

    // Se mide el contenido sin limite y se recorta a [maxHeight]; el
    // desvanecido solo se pinta si sobra (se lee en la fase de dibujo).
    Layout(
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { PreviewBlock(it, base, colors) }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (overflows) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            FADE_START to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) { measurables, constraints ->
        val limit = maxHeight.roundToPx()
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE))
        overflows = placeable.height > limit
        val height = minOf(placeable.height, limit)
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
}

@Composable
private fun PreviewBlock(item: PreviewItem, base: TextStyle, colors: NotesColors) {
    val text = remember(item.text, colors) { inline(item.text, colors) }
    when (item.kind) {
        PreviewKind.PARAGRAPH -> Text(text, style = base)
        PreviewKind.HEADING -> Text(text, style = base.copy(fontWeight = FontWeight.SemiBold, color = colors.textStrong))
        PreviewKind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(colors.border))
            Text(text, style = base.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 12.dp))
        }
        PreviewKind.CODE -> Text(
            item.text,
            style = base.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
            maxLines = CODE_MAX_LINES,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceRecessed, Radius.mdShape)
                .padding(8.dp),
        )
        PreviewKind.RULE -> HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 4.dp))
        PreviewKind.BULLET, PreviewKind.ORDERED, PreviewKind.TASK -> Row(
            modifier = Modifier.padding(start = LIST_INDENT * item.depth.coerceAtMost(MAX_DEPTH)),
        ) {
            val lineHeight = with(LocalDensity.current) { base.lineHeight.toDp() }
            Box(Modifier.width(MARKER_WIDTH).height(lineHeight), contentAlignment = Alignment.CenterStart) {
                when (item.kind) {
                    PreviewKind.TASK -> FaIcon(
                        if (item.checked) R.drawable.fa_square_check else R.drawable.fa_square_regular,
                        contentDescription = null,
                        tint = if (item.checked) colors.accent else colors.textMuted,
                        size = 13.dp,
                    )
                    PreviewKind.ORDERED -> Text(item.number, style = base, maxLines = 1)
                    else -> Text(BULLETS[item.depth % BULLETS.size], style = base)
                }
            }
            // Sin tachar las hechas: la tarjeta de la web tampoco lo hace.
            Text(text, style = base)
        }
    }
}

/**
 * Marcas en linea pintadas y su sintaxis fuera. Los enlaces quedan como texto
 * normal, como en la web (que quita los `<a>` de la tarjeta).
 */
internal fun inline(text: String, colors: NotesColors): AnnotatedString {
    val marks = InlineMarks.parse(text)
    if (marks.isEmpty()) return AnnotatedString(text)
    val hidden = BooleanArray(text.length)
    marks.forEach { mark ->
        for (i in mark.start until mark.contentStart) hidden[i] = true
        for (i in mark.contentEnd until mark.end) hidden[i] = true
    }
    val shifted = IntArray(text.length + 1)
    var visible = 0
    for (i in text.indices) {
        shifted[i] = visible
        if (!hidden[i]) visible++
    }
    shifted[text.length] = visible
    return buildAnnotatedString {
        text.forEachIndexed { i, c -> if (!hidden[i]) append(c) }
        marks.forEach { mark ->
            val style = when (mark.type) {
                MarkType.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textStrong)
                MarkType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkType.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MarkType.CODE -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = colors.surfaceRecessed,
                )
                MarkType.LINK -> null
            }
            val from = shifted[mark.contentStart]
            val to = shifted[mark.contentEnd]
            if (style != null && to > from) addStyle(style, from, to)
        }
    }
}

/** `max-h-40` de la web. */
val LIST_PREVIEW_HEIGHT = 160.dp

/** `max-h-28` de la tarjeta de papelera. */
val TRASH_PREVIEW_HEIGHT = 112.dp

private const val FADE_START = 0.7f
private const val CODE_MAX_LINES = 8
private const val MAX_DEPTH = 4
private val LIST_INDENT = 20.dp
private val MARKER_WIDTH = 20.dp
private val BULLETS = listOf("•", "◦", "▪")
