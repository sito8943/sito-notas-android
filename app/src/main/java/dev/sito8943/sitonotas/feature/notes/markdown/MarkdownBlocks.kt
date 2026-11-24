package dev.sito8943.sitonotas.feature.notes.markdown

/**
 * Bloques de Markdown que entiende la vista previa. Es un subconjunto a
 * proposito (no hay editor WYSIWYG): titulos, listas, tareas, citas, codigo y
 * separadores. Lo que no reconoce se muestra como parrafo, sin perder texto.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val depth: Int) : MdBlock
    data class Numbered(val number: String, val text: String, val depth: Int) : MdBlock
    data class Task(val checked: Boolean, val text: String, val depth: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
}

object MarkdownBlocks {

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        val lines = source.replace("\r\n", "\n").split('\n')

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            val depth = (line.length - trimmed.length) / INDENT

            when {
                trimmed.startsWith("```") -> {
                    flushParagraph()
                    val code = mutableListOf<String>()
                    index++
                    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                        code += lines[index]
                        index++
                    }
                    blocks += MdBlock.Code(code.joinToString("\n"))
                }
                trimmed.isEmpty() -> flushParagraph()
                RULE.matches(trimmed) -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                }
                HEADING.matches(trimmed) -> {
                    flushParagraph()
                    val match = HEADING.matchEntire(trimmed)!!
                    blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
                }
                TASK.matches(trimmed) -> {
                    flushParagraph()
                    val match = TASK.matchEntire(trimmed)!!
                    blocks += MdBlock.Task(match.groupValues[1].isNotBlank(), match.groupValues[2], depth)
                }
                BULLET.matches(trimmed) -> {
                    flushParagraph()
                    blocks += MdBlock.Bullet(BULLET.matchEntire(trimmed)!!.groupValues[1], depth)
                }
                NUMBERED.matches(trimmed) -> {
                    flushParagraph()
                    val match = NUMBERED.matchEntire(trimmed)!!
                    blocks += MdBlock.Numbered(match.groupValues[1], match.groupValues[2], depth)
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    blocks += MdBlock.Quote(trimmed.removePrefix(">").trimStart())
                }
                else -> paragraph += line.trim()
            }
            index++
        }
        flushParagraph()
        return blocks
    }

    private const val INDENT = 2
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val TASK = Regex("^[-*+]\\s+\\[([ xX]?)]\\s?(.*)$")
    private val BULLET = Regex("^[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\d+)[.)]\\s+(.*)$")
    private val RULE = Regex("^([-*_])(\\s*\\1){2,}$")
}
