package dev.sito8943.sitonotas.feature.notes.editor

import kotlin.math.max
import kotlin.math.min

/** Resultado de una edicion: los bloques nuevos y donde queda el cursor. */
data class EditResult(val blocks: List<EditorBlock>, val focusId: Long, val caret: Int)

/** Texto y seleccion tras aplicar un formato en linea. */
data class TextEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * Operaciones del editor, sin Compose, para poder probarlas. Todas tocan solo
 * el bloque afectado: el resto conserva su Markdown original letra a letra.
 *
 * Imitan las del editor de la web (Tiptap): Intro continua la lista o sale de
 * ella si el elemento esta vacio, borrar al principio quita el formato de
 * bloque o une con la linea anterior, y escribir `- `, `1. `, `[ ] `, `# `,
 * `> ` o `---` al empezar una linea la convierte.
 */
object EditorOps {

    /**
     * El usuario cambio el texto de un bloque. [newText] es el contenido del
     * campo y [caret] la posicion del cursor en el.
     */
    fun editText(blocks: List<EditorBlock>, index: Int, newText: String, caret: Int, ids: IdSource): EditResult {
        val block = blocks[index]
        if (block.type == BlockType.CODE) {
            val updated = block.copy(text = newText, hasBody = block.hasBody || newText.isNotEmpty())
            return EditResult(blocks.replace(index, updated), block.id, caret)
        }
        if ('\n' in newText) {
            val typedEnter = newText.count { it == '\n' } == 1 &&
                newText.length == block.text.length + 1 &&
                newText.indexOf('\n') == caret - 1
            return if (typedEnter) {
                enter(blocks, index, newText.substring(0, caret - 1), newText.substring(caret), ids)
            } else {
                paste(blocks, index, newText, caret, ids)
            }
        }

        // `[ ] ` en un parrafo es una tarea, como en la web.
        if (block.type == BlockType.PARAGRAPH) {
            TASK_SHORTCUT.find(newText)?.let { m ->
                val checked = m.groupValues[1].isNotBlank()
                val rest = newText.substring(m.range.last + 1)
                val prefix = if (checked) "- [x] " else "- [ ] "
                val task = MarkdownDocument.parseLine(prefix + rest, block.id)
                return EditResult(blocks.replace(index, task), block.id, max(0, caret - m.value.length))
            }
        }

        val previous = blocks.getOrNull(index - 1)
        val reparsed = MarkdownDocument.parseLine(
            block.prefix + newText,
            block.id,
            previousIsText = previous?.type == BlockType.PARAGRAPH && previous.text.isNotBlank(),
        )
        // Un parrafo no se convierte mientras la marca no lleve su espacio:
        // escribir "-5 grados" no debe pasar por una viñeta.
        val next = if (
            block.type == BlockType.PARAGRAPH &&
            reparsed.type != BlockType.PARAGRAPH &&
            reparsed.type != BlockType.RULE &&
            reparsed.type != BlockType.RAW &&
            !reparsed.prefix.endsWith(' ') && !reparsed.prefix.endsWith('\t')
        ) {
            block.copy(text = newText)
        } else {
            reparsed
        }
        val sourceCaret = block.prefix.length + caret
        return EditResult(blocks.replace(index, next), block.id, (sourceCaret - next.prefix.length).coerceIn(0, next.text.length))
    }

    /** Intro: parte el bloque en el cursor. */
    fun enter(blocks: List<EditorBlock>, index: Int, before: String, after: String, ids: IdSource): EditResult {
        val block = blocks[index]
        if (block.type == BlockType.PARAGRAPH && after.isEmpty() && MarkdownDocument.isFence(before)) {
            val fence = before.trimStart().takeWhile { it == '`' || it == '~' }
            val code = EditorBlock(block.id, BlockType.CODE, before, "", closing = fence, hasBody = true)
            return EditResult(blocks.replace(index, code), block.id, 0)
        }
        val continues = block.isList || block.type == BlockType.QUOTE
        if (continues && before.isEmpty() && after.isEmpty()) {
            // Elemento vacio: se sale de la lista.
            val plain = block.copy(type = BlockType.PARAGRAPH, prefix = "", text = "", checked = false, indent = 0)
            return EditResult(blocks.replace(index, plain), block.id, 0)
        }
        val current = block.copy(text = before)
        val created = if (continues) {
            MarkdownDocument.parseLine(continuationPrefix(block) + after, ids.next())
        } else {
            EditorBlock(ids.next(), BlockType.PARAGRAPH, "", after)
        }
        val result = blocks.toMutableList().apply {
            set(index, current)
            add(index + 1, created)
        }
        return EditResult(result, created.id, 0)
    }

    /** Borrar con el cursor al principio del bloque. */
    fun backspaceAtStart(blocks: List<EditorBlock>, index: Int): EditResult {
        val block = blocks[index]
        when (block.type) {
            BlockType.HEADING, BlockType.QUOTE, BlockType.BULLET, BlockType.ORDERED, BlockType.TASK -> {
                val leading = block.prefix.takeWhile { it == ' ' || it == '\t' }
                val updated = if (block.isList && leading.isNotEmpty()) {
                    // Elemento anidado: sube un nivel.
                    val drop = if (leading.first() == '\t') 1 else min(2, leading.length)
                    MarkdownDocument.parseLine(block.prefix.drop(drop) + block.text, block.id)
                } else {
                    block.copy(type = BlockType.PARAGRAPH, prefix = "", checked = false, indent = 0, level = 0)
                }
                return EditResult(blocks.replace(index, updated), block.id, 0)
            }
            else -> Unit
        }
        val previous = blocks.getOrNull(index - 1) ?: return EditResult(blocks, block.id, 0)
        return when {
            previous.type == BlockType.RULE ->
                EditResult(blocks.filterIndexed { i, _ -> i != index - 1 }, block.id, 0)
            previous.type == BlockType.CODE || block.type == BlockType.RULE || block.type == BlockType.CODE -> {
                val withoutEmpty = if (block.text.isEmpty() || block.type == BlockType.RULE) {
                    blocks.filterIndexed { i, _ -> i != index }
                } else {
                    blocks
                }
                EditResult(withoutEmpty, previous.id, previous.text.length)
            }
            else -> {
                val merged = previous.copy(text = previous.text + block.text)
                val result = blocks.toMutableList().apply {
                    set(index - 1, merged)
                    removeAt(index)
                }
                EditResult(result, previous.id, previous.text.length)
            }
        }
    }

    /** Marca o desmarca una tarea. Solo cambia el caracter de la casilla. */
    fun toggleChecked(blocks: List<EditorBlock>, index: Int): List<EditorBlock> {
        val block = blocks[index]
        if (block.type != BlockType.TASK) return blocks
        val box = CHECKBOX.findAll(block.prefix).last()
        val prefix = block.prefix.replaceRange(box.range, if (block.checked) "[ ]" else "[x]")
        return blocks.replace(index, block.copy(prefix = prefix, checked = !block.checked))
    }

    /**
     * Cambia el tipo de bloque desde la barra. Pedir el tipo que ya tiene lo
     * vuelve parrafo (los botones de lista son interruptores, como en la web).
     */
    fun setBlockType(blocks: List<EditorBlock>, index: Int, target: BlockType, level: Int = 1): List<EditorBlock> {
        val block = blocks[index]
        if (block.type == BlockType.CODE || block.type == BlockType.RULE) return blocks
        val same = block.type == target && (target != BlockType.HEADING || block.level == level)
        val indent = if (block.isList) block.prefix.takeWhile { it == ' ' || it == '\t' } else ""
        val prefix = when {
            same -> ""
            target == BlockType.HEADING -> "#".repeat(level) + " "
            target == BlockType.BULLET -> "$indent- "
            target == BlockType.TASK -> "$indent- [ ] "
            target == BlockType.ORDERED -> {
                val previous = blocks.getOrNull(index - 1)
                val number = if (previous?.type == BlockType.ORDERED && previous.indent == block.indent) {
                    orderedNumber(previous) + 1
                } else {
                    1
                }
                "$indent$number. "
            }
            target == BlockType.QUOTE -> "> "
            else -> ""
        }
        val updated = if (prefix.isEmpty()) {
            block.copy(type = BlockType.PARAGRAPH, prefix = "", checked = false, indent = 0, level = 0)
        } else {
            MarkdownDocument.parseLine(prefix + block.text, block.id)
        }
        return blocks.replace(index, updated)
    }

    /**
     * Negrita, cursiva, tachado o codigo sobre la seleccion; si la seleccion
     * ya lo tiene, lo quita. Sin seleccion y con el cursor dentro de la marca,
     * no se toca el texto: el cursor sale de ella y lo que se escriba despues va
     * sin formato, como el boton de la web.
     */
    fun toggleMark(text: String, start: Int, end: Int, type: MarkType): TextEdit {
        val marks = InlineMarks.parse(text).filter { it.type == type }
        if (start == end) {
            marks.firstOrNull { it.start < start && start <= it.end }?.let { return TextEdit(text, it.end, it.end) }
        }
        val existing = marks.firstOrNull { it.start <= start && end <= it.end }
        if (existing != null) {
            val without = text.removeRange(existing.contentEnd, existing.end).removeRange(existing.start, existing.contentStart)
            val shift = existing.open
            return TextEdit(
                without,
                (start - shift).coerceIn(existing.start, existing.contentEnd - shift),
                (end - shift).coerceIn(existing.start, existing.contentEnd - shift),
            )
        }
        val marker = when (type) {
            MarkType.BOLD -> "**"
            MarkType.ITALIC -> "*"
            MarkType.STRIKE -> "~~"
            MarkType.CODE -> "`"
            MarkType.LINK -> return TextEdit(text, start, end)
        }
        val wrapped = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
        return TextEdit(wrapped, start + marker.length, end + marker.length)
    }

    /** Enlace sobre la seleccion; sin seleccion, la URL hace de texto. */
    fun insertLink(text: String, start: Int, end: Int, url: String): TextEdit {
        val label = text.substring(start, end).ifEmpty { url }
        val link = "[$label]($url)"
        val result = text.substring(0, start) + link + text.substring(end)
        return TextEdit(result, start + link.length, start + link.length)
    }

    private fun paste(blocks: List<EditorBlock>, index: Int, newText: String, caret: Int, ids: IdSource): EditResult {
        val block = blocks[index]
        val parsed = MarkdownDocument.parse(block.prefix + newText, ids)
        // El primer bloque conserva el id para no perder el foco.
        val first = parsed.first().copy(id = block.id)
        val pasted = listOf(first) + parsed.drop(1)
        var sourceCaret = block.prefix.length + caret
        var target = pasted.last()
        var offset = target.text.length
        for (candidate in pasted) {
            val length = candidate.source.length
            if (sourceCaret <= length) {
                target = candidate
                val head = if (candidate.type == BlockType.CODE) candidate.prefix.length + 1 else candidate.prefix.length
                offset = (sourceCaret - head).coerceIn(0, candidate.text.length)
                break
            }
            sourceCaret -= length + 1
        }
        val result = blocks.toMutableList().apply {
            removeAt(index)
            addAll(index, pasted)
        }
        return EditResult(result, target.id, offset)
    }

    private fun continuationPrefix(block: EditorBlock): String = when (block.type) {
        BlockType.TASK -> {
            val box = CHECKBOX.findAll(block.prefix).last()
            block.prefix.replaceRange(box.range, "[ ]")
        }
        BlockType.ORDERED -> {
            val m = ORDERED_PREFIX.find(block.prefix)
            if (m == null) {
                block.prefix
            } else {
                val number = m.groupValues[2].toLong() + 1
                m.groupValues[1] + number + m.groupValues[3] + m.groupValues[4]
            }
        }
        else -> block.prefix
    }

    private fun orderedNumber(block: EditorBlock): Long =
        ORDERED_PREFIX.find(block.prefix)?.groupValues?.get(2)?.toLongOrNull() ?: 0

    private fun List<EditorBlock>.replace(index: Int, block: EditorBlock): List<EditorBlock> =
        toMutableList().apply { set(index, block) }

    private val CHECKBOX = Regex("\\[[ xX]]")
    private val ORDERED_PREFIX = Regex("^([ \\t]*)(\\d{1,9})([.)])([ \\t]*)")
    private val TASK_SHORTCUT = Regex("^\\[( |x|X)?] ")
}
