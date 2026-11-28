package dev.sito8943.sitonotas.feature.notes.editor

/** Un bloque del extracto de una tarjeta. */
data class PreviewItem(
    val kind: PreviewKind,
    /** Texto con su Markdown en linea (negrita, codigo...), ya sin imagenes ni HTML. */
    val text: String,
    /** Profundidad de lista (0 = primer nivel). */
    val depth: Int = 0,
    /** Numero de la lista numerada, tal cual (`"3."`). */
    val number: String = "",
    val checked: Boolean = false,
)

enum class PreviewKind { PARAGRAPH, HEADING, BULLET, ORDERED, TASK, QUOTE, CODE, RULE }

/**
 * Extracto de solo lectura de una nota para las tarjetas, como
 * `MarkdownPreview` de la web: se corta el Markdown a [PREVIEW_CONTENT_LENGTH]
 * caracteres en un salto de linea (`truncateMarkdown`) y se convierte a
 * bloques con el mismo analizador del editor.
 *
 * Igual que la web (marked sin `breaks` + DOMPurify sin `img`):
 * - las lineas seguidas de un parrafo o una cita se unen con un espacio;
 * - las imagenes desaparecen y de las etiquetas HTML queda solo su texto;
 * - una linea `===`/`---` bajo un parrafo lo vuelve encabezado (setext);
 * - las entidades HTML se ven como su caracter;
 * - de una tabla quedan los textos de sus celdas; las definiciones de enlace
 *   no se ven.
 */
object NotePreviewModel {

    const val PREVIEW_CONTENT_LENGTH = 600

    /** `truncateMarkdown` de la web: el principio, cortado en un salto de linea. */
    fun truncate(markdown: String): String {
        if (markdown.length <= PREVIEW_CONTENT_LENGTH) return markdown
        val cut = markdown.substring(0, PREVIEW_CONTENT_LENGTH)
        val lastBreak = cut.lastIndexOf('\n')
        return if (lastBreak > 0) cut.substring(0, lastBreak) else cut
    }

    fun build(markdown: String): List<PreviewItem> {
        val items = mutableListOf<PreviewItem>()
        // Se puede seguir uniendo lineas al ultimo bloque (parrafo, cita, elemento de lista).
        var open = false
        for (block in MarkdownDocument.parse(truncate(markdown))) {
            val raw = block.source
            if (raw.isBlank()) {
                open = false
                continue
            }
            when (block.type) {
                BlockType.PARAGRAPH -> {
                    val text = clean(block.text.trim())
                    val last = items.lastOrNull()
                    if (open && last != null && last.kind != PreviewKind.HEADING) {
                        items[items.lastIndex] = last.copy(text = join(last.text, text))
                    } else if (text.isNotEmpty()) {
                        items += PreviewItem(PreviewKind.PARAGRAPH, text)
                        open = true
                    }
                }
                BlockType.QUOTE -> {
                    val text = clean(block.text.trim())
                    val last = items.lastOrNull()
                    if (open && last?.kind == PreviewKind.QUOTE) {
                        items[items.lastIndex] = last.copy(text = join(last.text, text))
                    } else {
                        items += PreviewItem(PreviewKind.QUOTE, text)
                        open = true
                    }
                }
                BlockType.HEADING -> {
                    items += PreviewItem(PreviewKind.HEADING, clean(block.text.trim().trimEnd('#').trim()))
                    open = false
                }
                BlockType.BULLET, BlockType.ORDERED, BlockType.TASK -> {
                    items += PreviewItem(
                        kind = when (block.type) {
                            BlockType.TASK -> PreviewKind.TASK
                            BlockType.ORDERED -> PreviewKind.ORDERED
                            else -> PreviewKind.BULLET
                        },
                        text = clean(block.text.trim()),
                        depth = block.indent / 2,
                        number = if (block.type == BlockType.ORDERED) block.prefix.trim() else "",
                        checked = block.checked,
                    )
                    open = true
                }
                BlockType.CODE -> {
                    items += PreviewItem(PreviewKind.CODE, block.text)
                    open = false
                }
                BlockType.RULE -> {
                    items += PreviewItem(PreviewKind.RULE, "")
                    open = false
                }
                // Como la web: la imagen no sale en la tarjeta (DOMPurify sin `img`).
                BlockType.RAW, BlockType.IMAGE -> open = raw(raw, items, open)
            }
        }
        return items.filter { it.kind == PreviewKind.RULE || it.kind == PreviewKind.CODE || it.text.isNotBlank() }
    }

    /** Lineas que el editor no pinta: se tratan como la web. @return si el ultimo bloque sigue abierto. */
    private fun raw(line: String, items: MutableList<PreviewItem>, open: Boolean): Boolean {
        val last = items.lastOrNull()
        return when {
            SETEXT.matches(line) && open && last?.kind == PreviewKind.PARAGRAPH -> {
                items[items.lastIndex] = last.copy(kind = PreviewKind.HEADING)
                false
            }
            LINK_DEFINITION.containsMatchIn(line) -> open
            TABLE_DELIMITER.matches(line) -> open
            TABLE_ROW.matches(line) -> {
                val cells = line.trim().trim('|').split('|').map { clean(it.trim()) }.filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) items += PreviewItem(PreviewKind.PARAGRAPH, cells.joinToString("  "))
                false
            }
            else -> {
                val text = clean(line.trim())
                if (text.isEmpty()) return open
                if (open && last != null && last.kind != PreviewKind.HEADING) {
                    items[items.lastIndex] = last.copy(text = join(last.text, text))
                } else {
                    items += PreviewItem(PreviewKind.PARAGRAPH, text)
                }
                true
            }
        }
    }

    /**
     * Fuera imagenes y etiquetas HTML (su texto se queda) y las entidades
     * (`&gt;`, `&#39;`) convertidas en su caracter, como hace el navegador.
     */
    fun clean(text: String): String = decodeEntities(
        text
            .replace(IMAGE, "")
            .replace(HTML_TAG, ""),
    ).trim()

    private fun decodeEntities(text: String): String = ENTITY.replace(text) { m ->
        val name = m.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED_ENTITIES[name]
        } ?: m.value
    }

    private fun codePoint(value: Int): String? =
        if (Character.isValidCodePoint(value) && value != 0) String(Character.toChars(value)) else null

    private fun join(a: String, b: String) = if (a.isEmpty()) b else if (b.isEmpty()) a else "$a $b"

    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
    )
    private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val HTML_TAG = Regex("</?[A-Za-z][A-Za-z0-9-]*(\\s[^>]*)?/?>|<!--.*?-->")
    private val SETEXT = Regex("^ {0,3}(=+|-+)[ \\t]*$")
    private val LINK_DEFINITION = Regex("^ {0,3}\\[[^\\]]+]:\\s")
    private val TABLE_ROW = Regex("^\\s*\\|.*\\|\\s*$")
    private val TABLE_DELIMITER = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
}
