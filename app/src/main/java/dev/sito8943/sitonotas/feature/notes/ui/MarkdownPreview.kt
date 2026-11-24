package dev.sito8943.sitonotas.feature.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.sito8943.sitonotas.feature.notes.markdown.MarkdownBlocks
import dev.sito8943.sitonotas.feature.notes.markdown.MarkdownInline
import dev.sito8943.sitonotas.feature.notes.markdown.MdBlock
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesColors
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** Vista previa del Markdown de una nota. Solo lectura. */
@Composable
fun MarkdownPreview(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { MarkdownBlocks.parse(source) }
    val colors = NotesTheme.colors
    val body = NotesTheme.typography.body.copy(color = colors.textStrong, lineHeight = 24.sp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing(2))) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> InlineText(
                    block.text,
                    body.copy(
                        fontSize = HEADING_SIZES.getOrElse(block.level - 1) { 16 }.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (HEADING_SIZES.getOrElse(block.level - 1) { 16 } + 6).sp,
                    ),
                    colors,
                )
                is MdBlock.Paragraph -> InlineText(block.text, body, colors)
                is MdBlock.Bullet -> ListItem("•", block.depth, block.text, body, colors)
                is MdBlock.Numbered -> ListItem("${block.number}.", block.depth, block.text, body, colors)
                is MdBlock.Task -> ListItem(
                    if (block.checked) "☑" else "☐",
                    block.depth,
                    block.text,
                    if (block.checked) body.copy(color = colors.textMuted, textDecoration = TextDecoration.LineThrough) else body,
                    colors,
                )
                is MdBlock.Quote -> Row {
                    Box(
                        Modifier
                            .width(spacing(1))
                            .height(spacing(6))
                            .background(colors.border),
                    )
                    InlineText(
                        block.text,
                        body.copy(color = colors.textMuted, fontStyle = FontStyle.Italic),
                        colors,
                        Modifier.padding(start = spacing(3)),
                    )
                }
                is MdBlock.Code -> Text(
                    text = block.text,
                    style = body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background, Radius.smShape)
                        .padding(spacing(3)),
                )
                MdBlock.Rule -> HorizontalDivider(color = colors.border)
            }
        }
    }
}

@Composable
private fun ListItem(marker: String, depth: Int, text: String, style: TextStyle, colors: NotesColors) {
    Row(modifier = Modifier.padding(start = spacing(4) * depth)) {
        Text(marker, style = style, modifier = Modifier.width(spacing(6)))
        InlineText(text, style, colors)
    }
}

@Composable
private fun InlineText(text: String, style: TextStyle, colors: NotesColors, modifier: Modifier = Modifier) {
    val annotated = remember(text, colors) { inline(text, colors) }
    Text(text = annotated, style = style, modifier = modifier)
}

private fun inline(text: String, colors: NotesColors): AnnotatedString = buildAnnotatedString {
    MarkdownInline.parse(text).forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) colors.border.copy(alpha = 0.5f) else Color.Unspecified,
            textDecoration = if (span.strike) TextDecoration.LineThrough else null,
        )
        if (span.link != null && span.link.startsWith("http")) {
            withLink(
                LinkAnnotation.Url(
                    span.link,
                    TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)),
                ),
            ) { withStyle(style) { append(span.text) } }
        } else {
            withStyle(style) { append(span.text) }
        }
    }
}

private val HEADING_SIZES = listOf(24, 20, 18, 16, 16, 16)
