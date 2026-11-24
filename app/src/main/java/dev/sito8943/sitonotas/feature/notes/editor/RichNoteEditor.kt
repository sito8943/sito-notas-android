package dev.sito8943.sitonotas.feature.notes.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesColors
import dev.sito8943.sitonotas.ui.theme.NotesTheme
import dev.sito8943.sitonotas.ui.theme.Poppins

/**
 * Editor de texto con formato que lee y escribe Markdown, como el
 * `NoteEditor` (Tiptap) de la web. Cada bloque es un campo: los encabezados se
 * ven grandes, las listas con viñeta o numero, las tareas con su casilla y la
 * sintaxis en linea (`**`, `[](...)`) se oculta salvo donde esta el cursor.
 */
@Composable
fun RichNoteEditor(
    state: RichEditorState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val requesters = remember(state) { mutableMapOf<Long, FocusRequester>() }
    val blocks = state.blocks

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            val requester = requesters.getOrPut(block.id) { FocusRequester() }
            EditorBlockRow(
                block = block,
                value = state.valueOf(block),
                focused = state.focusedId == block.id,
                placeholder = if (blocks.size == 1 && index == 0) placeholder else null,
                requester = requester,
                onValueChange = { state.onValueChange(block.id, it) },
                // Se recuerda el ultimo bloque con foco aunque lo pierda un
                // instante: la barra de formato actua sobre el.
                onFocus = { focused -> if (focused) state.focusedId = block.id },
                onToggleChecked = { state.toggleChecked(block.id) },
                modifier = Modifier.padding(top = topSpacing(block, blocks.getOrNull(index - 1))),
            )
        }
        // Tocar bajo el texto lleva el cursor al final, como en la web.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    state.focusEnd()
                },
        )
    }

    val request = state.focusRequest
    LaunchedEffect(request, blocks) {
        if (request != null) {
            requesters[request]?.let { runCatching { it.requestFocus() } }
            state.consumeFocusRequest()
        }
    }
}

private fun topSpacing(block: EditorBlock, previous: EditorBlock?) = when {
    previous == null -> 0.dp
    block.type == BlockType.HEADING -> spacing(3)
    block.type == BlockType.CODE || previous.type == BlockType.CODE -> spacing(2)
    else -> 0.dp
}

@Composable
private fun EditorBlockRow(
    block: EditorBlock,
    value: TextFieldValue,
    focused: Boolean,
    placeholder: String?,
    requester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: (Boolean) -> Unit,
    onToggleChecked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NotesTheme.colors
    val style = blockStyle(block, colors)
    val transformation = remember(value.text, value.selection, focused, block.type, colors) {
        BlockTransformation(
            inline = block.supportsInline,
            reveal = if (focused) value.selection else null,
            colors = colors,
        )
    }

    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .focusRequester(requester)
                .onFocusChanged { onFocus(it.isFocused) },
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(
                capitalization = if (block.type == BlockType.CODE || block.type == BlockType.RAW) {
                    KeyboardCapitalization.None
                } else {
                    KeyboardCapitalization.Sentences
                },
                autoCorrectEnabled = block.type != BlockType.CODE && block.type != BlockType.RAW,
            ),
            decorationBox = { inner ->
                Box {
                    if (placeholder != null && block.text.isEmpty()) {
                        NotesText(placeholder, style = style.copy(color = colors.textMuted))
                    }
                    inner()
                }
            },
        )
    }

    // Una sola estructura para todos los tipos: el campo ocupa siempre el
    // mismo sitio en el arbol de composicion. Si cambiara (parrafo -> tarea al
    // escribir "[ ] "), Compose crearia otro campo y el foco se perderia.
    val indent = INDENT_STEP * (block.indent / 2).coerceAtMost(MAX_INDENT_LEVELS)
    val container = when (block.type) {
        BlockType.CODE -> Modifier
            .background(colors.surfaceRecessed, Radius.lgShape)
            .padding(spacing(4))
        BlockType.QUOTE -> Modifier.height(IntrinsicSize.Min)
        BlockType.BULLET, BlockType.ORDERED, BlockType.TASK -> Modifier.padding(start = indent)
        else -> Modifier
    }
    Row(
        modifier = modifier.fillMaxWidth().then(container),
        verticalAlignment = if (block.type == BlockType.RULE) Alignment.CenterVertically else Alignment.Top,
    ) {
        Leading(block, style, onToggleChecked)
        field(
            when (block.type) {
                BlockType.RULE -> Modifier.width(IntrinsicSize.Min)
                BlockType.QUOTE -> Modifier.weight(1f).padding(start = spacing(4))
                else -> Modifier.weight(1f)
            },
        )
        if (block.type == BlockType.RULE) {
            HorizontalDivider(modifier = Modifier.weight(1f).padding(start = spacing(2)), color = colors.border)
        }
    }
}

/** Lo que va antes del texto: viñeta, numero, casilla o la barra de la cita. */
@Composable
private fun Leading(block: EditorBlock, style: TextStyle, onToggleChecked: () -> Unit) {
    when (block.type) {
        BlockType.BULLET, BlockType.ORDERED, BlockType.TASK -> ListMarker(block, style, onToggleChecked)
        BlockType.QUOTE -> Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(NotesTheme.colors.border, Radius.smShape),
        )
        else -> Unit
    }
}

@Composable
private fun ListMarker(block: EditorBlock, style: TextStyle, onToggleChecked: () -> Unit) {
    val colors = NotesTheme.colors
    val lineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { style.lineHeight.toDp() }
    Box(
        modifier = Modifier.width(if (block.type == BlockType.TASK) TASK_MARKER_WIDTH else MARKER_WIDTH).height(lineHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (block.type) {
            BlockType.TASK -> {
                val label = stringResource(R.string.editor_task)
                val state = stringResource(if (block.checked) R.string.editor_task_done else R.string.editor_task_pending)
                // Toda la columna de la casilla es pulsable, no solo el icono.
                Box(
                    modifier = Modifier
                        .width(TASK_MARKER_WIDTH)
                        .height(lineHeight)
                        .focusProperties { canFocus = false }
                        .clickable(role = Role.Checkbox, onClickLabel = label, onClick = onToggleChecked)
                        .semantics { stateDescription = state },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    FaIcon(
                        if (block.checked) R.drawable.fa_square_check else R.drawable.fa_square_regular,
                        contentDescription = label,
                        tint = if (block.checked) colors.accent else colors.textMuted,
                        size = 18.dp,
                    )
                }
            }
            BlockType.ORDERED -> NotesText(
                block.prefix.trim(),
                style = style.copy(color = colors.textMuted),
                maxLines = 1,
            )
            else -> NotesText(
                BULLETS[(block.indent / 2) % BULLETS.size],
                style = style.copy(color = colors.textMuted),
            )
        }
    }
}

@Composable
private fun blockStyle(block: EditorBlock, colors: NotesColors): TextStyle {
    val body = NotesTheme.typography.body.copy(color = colors.textStrong, lineHeight = 26.sp)
    return when (block.type) {
        BlockType.HEADING -> {
            val size = HEADING_SIZES.getOrElse(block.level - 1) { 16 }
            body.copy(
                fontSize = size.sp,
                lineHeight = (size + 8).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (block.level <= 3) Poppins else null,
            )
        }
        BlockType.TASK -> if (block.checked) {
            body.copy(color = colors.textMuted, textDecoration = TextDecoration.LineThrough)
        } else {
            body
        }
        BlockType.QUOTE -> body.copy(color = colors.textMuted, fontStyle = FontStyle.Italic)
        BlockType.CODE -> body.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
        BlockType.RAW -> body.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            color = colors.textMuted,
        )
        BlockType.RULE -> body.copy(color = colors.border)
        else -> body
    }
}

/**
 * Pinta las marcas en linea y oculta su sintaxis, salvo la de la marca donde
 * esta el cursor (asi se puede editar). Tambien oculta el centinela del
 * principio. Solo cambia lo que se ve: el texto del campo sigue siendo el
 * Markdown de la linea.
 */
private class BlockTransformation(
    private val inline: Boolean,
    private val reveal: androidx.compose.ui.text.TextRange?,
    private val colors: NotesColors,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val offset = if (raw.startsWith(RichEditorState.SENTINEL)) 1 else 0
        val content = raw.substring(offset)
        val marks = if (inline) InlineMarks.parse(content) else emptyList()

        val hidden = BooleanArray(raw.length)
        if (offset == 1) hidden[0] = true
        val revealed = mutableSetOf<InlineMark>()
        marks.forEach { mark ->
            val start = mark.start + offset
            val end = mark.end + offset
            val show = reveal != null && reveal.min <= end && reveal.max >= start
            if (show) {
                revealed += mark
            } else {
                for (i in start until start + mark.open) hidden[i] = true
                for (i in end - mark.close until end) hidden[i] = true
            }
        }

        val toTransformed = IntArray(raw.length + 1)
        val visible = ArrayList<Int>(raw.length)
        for (i in raw.indices) {
            toTransformed[i] = visible.size
            if (!hidden[i]) visible += i
        }
        toTransformed[raw.length] = visible.size

        val transformed = buildAnnotatedString {
            visible.forEach { append(raw[it]) }
            marks.forEach { mark ->
                val from = toTransformed[mark.contentStart + offset]
                val to = toTransformed[mark.contentEnd + offset]
                if (to > from) addStyle(styleOf(mark), from, to)
                if (mark in revealed) {
                    val syntax = SpanStyle(color = colors.textMuted.copy(alpha = 0.6f))
                    addStyle(syntax, toTransformed[mark.start + offset], toTransformed[mark.contentStart + offset])
                    addStyle(syntax, toTransformed[mark.contentEnd + offset], toTransformed[mark.end + offset])
                }
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toTransformed[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                if (offset >= visible.size) raw.length else visible[offset.coerceAtLeast(0)]
        }
        return TransformedText(transformed, mapping)
    }

    private fun styleOf(mark: InlineMark): SpanStyle = when (mark.type) {
        MarkType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        MarkType.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MarkType.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceRecessed)
        MarkType.LINK -> SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
    }

    override fun equals(other: Any?): Boolean =
        other is BlockTransformation && other.inline == inline && other.reveal == reveal && other.colors == colors

    override fun hashCode(): Int = (inline.hashCode() * 31 + reveal.hashCode()) * 31 + colors.hashCode()
}

private val HEADING_SIZES = listOf(24, 20, 18, 16, 16, 16)
private val BULLETS = listOf("•", "◦", "▪")
private val MARKER_WIDTH = 28.dp
private val TASK_MARKER_WIDTH = 34.dp
private val INDENT_STEP = 20.dp
private const val MAX_INDENT_LEVELS = 6
