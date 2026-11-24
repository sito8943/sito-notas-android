package dev.sito8943.sitonotas.feature.notes.markdown

/** Tramo de texto con su formato en linea. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    /** Destino de un enlace `[texto](url)`; la imagen muestra su texto alternativo. */
    val link: String? = null,
)

/**
 * Formato en linea: `**negrita**`, `*cursiva*`/`_cursiva_`, `` `codigo` ``,
 * `~~tachado~~` y enlaces. Sin anidar codigo dentro de otros formatos.
 */
object MarkdownInline {

    fun parse(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        parseInto(text, MdSpan(""), spans)
        return merge(spans)
    }

    private fun parseInto(text: String, style: MdSpan, out: MutableList<MdSpan>) {
        var rest = text
        while (rest.isNotEmpty()) {
            val match = TOKEN.find(rest)
            if (match == null) {
                out += style.copy(text = rest)
                return
            }
            if (match.range.first > 0) out += style.copy(text = rest.substring(0, match.range.first))
            val groups = match.groups
            when {
                groups[CODE] != null -> out += style.copy(text = groups[CODE]!!.value, code = true)
                groups[LINK_TEXT] != null -> parseInto(
                    groups[LINK_TEXT]!!.value,
                    style.copy(link = groups[LINK_URL]!!.value),
                    out,
                )
                groups[BOLD] != null -> parseInto(groups[BOLD]!!.value, style.copy(bold = true), out)
                groups[BOLD_ALT] != null -> parseInto(groups[BOLD_ALT]!!.value, style.copy(bold = true), out)
                groups[STRIKE] != null -> parseInto(groups[STRIKE]!!.value, style.copy(strike = true), out)
                groups[ITALIC] != null -> parseInto(groups[ITALIC]!!.value, style.copy(italic = true), out)
                groups[ITALIC_ALT] != null -> parseInto(groups[ITALIC_ALT]!!.value, style.copy(italic = true), out)
            }
            rest = rest.substring(match.range.last + 1)
        }
    }

    /** Une tramos contiguos con el mismo formato. */
    private fun merge(spans: List<MdSpan>): List<MdSpan> = spans
        .filter { it.text.isNotEmpty() }
        .fold(mutableListOf()) { acc, span ->
            val last = acc.lastOrNull()
            if (last != null && last.copy(text = "") == span.copy(text = "")) {
                acc[acc.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                acc += span
            }
            acc
        }

    private const val CODE = 1
    private const val LINK_TEXT = 2
    private const val LINK_URL = 3
    private const val BOLD = 4
    private const val BOLD_ALT = 5
    private const val STRIKE = 6
    private const val ITALIC = 7
    private const val ITALIC_ALT = 8

    private val TOKEN = Regex(
        "`([^`]+)`" +
            "|!?\\[([^\\]]*)]\\(([^)]*)\\)" +
            "|\\*\\*(.+?)\\*\\*" +
            "|__(.+?)__" +
            "|~~(.+?)~~" +
            "|\\*([^*\\s](?:[^*]*[^*\\s])?)\\*" +
            "|(?<![\\w])_([^_\\s](?:[^_]*[^_\\s])?)_(?![\\w])",
    )
}
