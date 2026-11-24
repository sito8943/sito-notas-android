package dev.sito8943.sitonotas.feature.notes.editor

/**
 * Markdown <-> bloques del editor. La garantia es `serialize(parse(md)) == md`
 * para cualquier texto: cada linea acaba en exactamente un bloque y cada bloque
 * conserva su sintaxis original (ver [EditorBlock]).
 *
 * Reconoce lo mismo que el editor de la web (Tiptap: StarterKit + TaskList):
 * parrafos, encabezados, listas con viñetas y numeradas, tareas, citas,
 * bloques de codigo con vallas y separadores. Lo demas queda como
 * [BlockType.RAW] y se edita como texto.
 */
object MarkdownDocument {

    fun parse(markdown: String, ids: IdSource = IdSource()): List<EditorBlock> {
        val lines = markdown.split('\n')
        val blocks = mutableListOf<EditorBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE.matchEntire(line)
            if (fence != null) {
                val marker = fence.groupValues[2]
                val close = closingFence(marker)
                var end = i + 1
                while (end < lines.size && !close.matches(lines[end])) end++
                val closed = end < lines.size
                val body = lines.subList(i + 1, if (closed) end else lines.size)
                blocks += EditorBlock(
                    id = ids.next(),
                    type = BlockType.CODE,
                    prefix = line,
                    text = body.joinToString("\n"),
                    closing = if (closed) lines[end] else null,
                    hasBody = body.isNotEmpty(),
                )
                i = if (closed) end + 1 else lines.size
                continue
            }
            val previous = blocks.lastOrNull()
            blocks += parseLine(line, ids.next(), previousIsText = previous?.type == BlockType.PARAGRAPH && previous.text.isNotBlank())
            i++
        }
        return blocks
    }

    fun serialize(blocks: List<EditorBlock>): String = blocks.joinToString("\n") { it.source }

    /**
     * Una linea suelta (fuera de un bloque de codigo). [previousIsText] marca
     * el caso setext: `---` o `===` bajo un parrafo es un encabezado para
     * Markdown, no un separador.
     */
    fun parseLine(line: String, id: Long, previousIsText: Boolean = false): EditorBlock {
        if (previousIsText && SETEXT.matches(line)) return EditorBlock(id, BlockType.RAW, "", line)
        if (RAW_PATTERNS.any { it.containsMatchIn(line) }) return EditorBlock(id, BlockType.RAW, "", line)

        HEADING.matchEntire(line)?.let { m ->
            val hashes = m.groupValues[1]
            return EditorBlock(
                id, BlockType.HEADING, hashes + m.groupValues[2], m.groupValues[3],
                level = hashes.count { it == '#' },
            )
        }
        if (RULE.matches(line)) return EditorBlock(id, BlockType.RULE, "", line)
        TASK.matchEntire(line)?.let { m ->
            val prefix = line.substring(0, line.length - m.groupValues[6].length)
            return EditorBlock(
                id, BlockType.TASK, prefix, m.groupValues[6],
                indent = columns(m.groupValues[1]),
                checked = m.groupValues[4] != " ",
            )
        }
        ORDERED.matchEntire(line)?.let { m ->
            return EditorBlock(
                id, BlockType.ORDERED, line.substring(0, line.length - m.groupValues[5].length), m.groupValues[5],
                indent = columns(m.groupValues[1]),
            )
        }
        BULLET.matchEntire(line)?.let { m ->
            return EditorBlock(
                id, BlockType.BULLET, line.substring(0, line.length - m.groupValues[4].length), m.groupValues[4],
                indent = columns(m.groupValues[1]),
            )
        }
        QUOTE.matchEntire(line)?.let { m ->
            return EditorBlock(id, BlockType.QUOTE, m.groupValues[1], m.groupValues[2])
        }
        return EditorBlock(id, BlockType.PARAGRAPH, "", line)
    }

    /** Hay algo que el editor solo puede mostrar como Markdown. */
    fun hasRawContent(blocks: List<EditorBlock>): Boolean = blocks.any { it.type == BlockType.RAW }

    /** Linea de valla de apertura (```` ``` ````, `~~~`). */
    fun isFence(line: String): Boolean = FENCE.matches(line)

    private fun closingFence(marker: String): Regex {
        val char = Regex.escape(marker.first().toString())
        return Regex("^ {0,3}$char{${marker.length},}[ \\t]*$")
    }

    private fun columns(indent: String): Int = indent.sumOf { if (it == '\t') TAB_COLUMNS else 1 }

    private const val TAB_COLUMNS = 4

    private val FENCE = Regex("^( {0,3})(`{3,}|~{3,})(.*)$")
    private val HEADING = Regex("^( {0,3}#{1,6})([ \\t]+|$)(.*)$")
    private val RULE = Regex("^ {0,3}([-*_])([ \\t]*\\1){2,}[ \\t]*$")
    // grupos: 1 sangria, 2 marca, 3 espacio, 4 estado, 5 separador, 6 texto
    private val TASK = Regex("^([ \\t]*)([-*+]|\\d{1,9}[.)])([ \\t]+)\\[([ xX])]([ \\t]+|$)(.*)$")
    private val ORDERED = Regex("^([ \\t]*)(\\d{1,9})([.)])([ \\t]+|$)(.*)$")
    private val BULLET = Regex("^([ \\t]*)([-*+])([ \\t]+|$)(.*)$")
    private val QUOTE = Regex("^( {0,3}>[ \\t]?)(.*)$")
    private val SETEXT = Regex("^ {0,3}(=+|-+)[ \\t]*$")

    /** Tablas, HTML, imagenes solas en su linea, definiciones de enlace. */
    private val RAW_PATTERNS = listOf(
        Regex("^\\s*\\|.*\\|\\s*$"),
        Regex("^\\s*</?[A-Za-z][A-Za-z0-9-]*(\\s[^>]*)?/?>"),
        Regex("^\\s*<!--"),
        Regex("!\\[[^\\]]*]\\([^)]*\\)"),
        Regex("^ {0,3}\\[[^\\]]+]:\\s"),
    )
}

/** Ids estables de bloque para claves de Compose y foco. */
class IdSource(private var next: Long = 1) {
    fun next(): Long = next++
}
