package dev.sito8943.sitonotas.feature.notes.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Estado del editor enriquecido: los bloques, el valor de cada campo (texto,
 * seleccion y composicion del teclado) y el foco.
 *
 * Solo avisa con [onChange] cuando el usuario cambia algo y el Markdown
 * resultante es distinto del que habia: abrir una nota nunca la marca como
 * modificada.
 */
class RichEditorState(markdown: String, private val onChange: (String) -> Unit) {

    private val ids = IdSource()
    private var lastMarkdown = markdown

    var blocks by mutableStateOf(MarkdownDocument.parse(markdown, ids))
        private set

    /** Ultimo bloque que tuvo el foco, para la barra de formato. */
    var focusedId by mutableStateOf<Long?>(null)
        internal set

    /** Foco pendiente de pedir tras una edicion que cambia de bloque. */
    var focusRequest by mutableStateOf<Long?>(null)
        internal set

    private val values = mutableStateMapOf<Long, TextFieldValue>()

    val hasRawContent: Boolean get() = MarkdownDocument.hasRawContent(blocks)

    val focusedBlock: EditorBlock? get() = blocks.firstOrNull { it.id == focusedId }

    /** Valor del campo de un bloque. Lleva [SENTINEL] delante (ver [onValueChange]). */
    fun valueOf(block: EditorBlock): TextFieldValue {
        val expected = SENTINEL + block.text
        val current = values[block.id]
        return if (current != null && current.text == expected) current else TextFieldValue(expected, TextRange(expected.length))
    }

    /**
     * Cambio en el campo de un bloque. El campo empieza siempre por un
     * caracter invisible: si desaparece, el usuario borro con el cursor al
     * principio (los teclados en pantalla no mandan esa tecla si no hay nada
     * que borrar).
     */
    fun onValueChange(blockId: Long, value: TextFieldValue) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = blocks[index]
        val hadSentinel = value.text.startsWith(SENTINEL)
        // Solo se quita el centinela: un U+200B que ya estuviera en la nota se respeta.
        val newText = if (hadSentinel) value.text.substring(1) else value.text
        if (!hadSentinel && newText == block.text) {
            apply(EditorOps.backspaceAtStart(blocks, index))
            return
        }
        val caret = (value.selection.end - if (hadSentinel) 1 else 0).coerceIn(0, newText.length)
        if (newText == block.text) {
            values[blockId] = value.clampToSentinel()
            return
        }
        val result = EditorOps.editText(blocks, index, newText, caret, ids)
        val updated = result.blocks.firstOrNull { it.id == blockId }
        if (result.focusId == blockId && updated != null && updated.text == newText && hadSentinel) {
            // Escritura normal: se conserva el valor del teclado (composicion).
            blocks = result.blocks
            values[blockId] = value.clampToSentinel()
            emit()
        } else {
            apply(result)
            // El tipo de bloque pudo cambiar ("[ ] " -> tarea): se asegura el foco.
            focusRequest = result.focusId
        }
    }

    fun toggleChecked(blockId: Long) {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        blocks = EditorOps.toggleChecked(blocks, index)
        emit()
    }

    fun setBlockType(type: BlockType, level: Int = 1) {
        val id = focusedId ?: return
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0) return
        val caret = caretOf(blocks[index])
        blocks = EditorOps.setBlockType(blocks, index, type, level)
        moveCaret(id, caret)
        focusRequest = id
        emit()
    }

    /** Siguiente nivel de encabezado: parrafo -> H1 -> H2 -> H3 -> parrafo. */
    fun cycleHeading() {
        val block = focusedBlock ?: return
        when {
            block.type != BlockType.HEADING -> setBlockType(BlockType.HEADING, 1)
            block.level >= MAX_TOOLBAR_HEADING -> setBlockType(BlockType.HEADING, block.level)
            else -> setBlockType(BlockType.HEADING, block.level + 1)
        }
    }

    fun toggleMark(type: MarkType) = editSelection { text, start, end -> EditorOps.toggleMark(text, start, end, type) }

    fun insertLink(url: String) = editSelection { text, start, end -> EditorOps.insertLink(text, start, end, url) }

    /** Marcas que tocan el cursor del bloque con foco (botones activos). */
    fun activeMarks(): Set<MarkType> {
        val block = focusedBlock ?: return emptySet()
        if (!block.supportsInline) return emptySet()
        val selection = selectionOf(block)
        return InlineMarks.parse(block.text)
            .filter { it.start <= selection.min && selection.max <= it.end }
            .mapTo(mutableSetOf()) { it.type }
    }

    fun selectionOf(block: EditorBlock): TextRange {
        val selection = valueOf(block).selection
        return TextRange((selection.start - 1).coerceAtLeast(0), (selection.end - 1).coerceAtLeast(0))
    }

    /** Pone el foco en un bloque (al final si no se indica). */
    fun focus(blockId: Long, caret: Int? = null) {
        val block = blocks.firstOrNull { it.id == blockId } ?: return
        moveCaret(blockId, caret ?: block.text.length)
        focusRequest = blockId
    }

    /** Toque bajo el ultimo bloque: foco al final, o un parrafo nuevo si alli no se escribe. */
    fun focusEnd() {
        val last = blocks.lastOrNull()
        if (last == null || last.type == BlockType.CODE || last.type == BlockType.RULE) {
            val paragraph = EditorBlock(ids.next(), BlockType.PARAGRAPH, "", "")
            blocks = blocks + paragraph
            focus(paragraph.id)
            emit()
        } else {
            focus(last.id)
        }
    }

    internal fun consumeFocusRequest() {
        focusRequest = null
    }

    private fun editSelection(edit: (String, Int, Int) -> TextEdit) {
        val block = focusedBlock ?: return
        if (!block.supportsInline) return
        val index = blocks.indexOf(block)
        val selection = selectionOf(block)
        val result = edit(block.text, selection.min, selection.max)
        if (result.text != block.text) {
            blocks = blocks.toMutableList().apply { set(index, block.copy(text = result.text)) }
        }
        values[block.id] = TextFieldValue(SENTINEL + result.text, TextRange(result.selectionStart + 1, result.selectionEnd + 1))
        focusRequest = block.id
        emit()
    }

    private fun apply(result: EditResult) {
        blocks = result.blocks
        moveCaret(result.focusId, result.caret)
        if (result.focusId != focusedId) focusRequest = result.focusId
        values.keys.retainAll(blocks.mapTo(mutableSetOf()) { it.id })
        emit()
    }

    private fun moveCaret(blockId: Long, caret: Int) {
        val block = blocks.firstOrNull { it.id == blockId } ?: return
        val position = caret.coerceIn(0, block.text.length) + 1
        values[blockId] = TextFieldValue(SENTINEL + block.text, TextRange(position))
    }

    private fun caretOf(block: EditorBlock): Int = selectionOf(block).end

    private fun emit() {
        val markdown = MarkdownDocument.serialize(blocks)
        if (markdown == lastMarkdown) return
        lastMarkdown = markdown
        onChange(markdown)
    }

    private fun TextFieldValue.clampToSentinel(): TextFieldValue {
        if (selection.min >= 1) return this
        return copy(selection = TextRange(selection.start.coerceAtLeast(1), selection.end.coerceAtLeast(1)))
    }

    companion object {
        /** Espacio de ancho cero: nunca se guarda, solo detecta el borrado al principio. */
        const val SENTINEL = "​"
        private const val MAX_TOOLBAR_HEADING = 3
    }
}

/** Bloques donde se pintan negrita, enlaces, etc. (no en codigo ni en lo crudo). */
val EditorBlock.supportsInline: Boolean
    get() = type != BlockType.CODE && type != BlockType.RAW && type != BlockType.RULE
