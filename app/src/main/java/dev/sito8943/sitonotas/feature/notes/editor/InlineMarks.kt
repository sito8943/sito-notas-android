package dev.sito8943.sitonotas.feature.notes.editor

/** Marcas en linea que soporta la web: negrita, cursiva, tachado, codigo y enlace. */
enum class MarkType { BOLD, ITALIC, STRIKE, CODE, LINK }

/**
 * Una marca en el texto de un bloque. `[start, start + open)` y
 * `[end - close, end)` son la sintaxis (`**`, `](url)`) y lo de en medio el
 * contenido.
 */
data class InlineMark(
    val type: MarkType,
    val start: Int,
    val end: Int,
    val open: Int,
    val close: Int,
    val url: String? = null,
) {
    val contentStart: Int get() = start + open
    val contentEnd: Int get() = end - close
}

/**
 * Localiza las marcas de una linea con sus posiciones, para pintarlas y para
 * los botones de formato. Solo lee: nunca reescribe el texto. Un caso dudoso
 * se queda como texto normal (se ve la sintaxis), que es lo seguro.
 */
object InlineMarks {

    fun parse(text: String): List<InlineMark> {
        val out = mutableListOf<InlineMark>()
        scan(text, 0, text.length, out)
        return out.sortedBy { it.start }
    }

    private fun scan(text: String, from: Int, to: Int, out: MutableList<InlineMark>) {
        var i = from
        while (i < to) {
            val c = text[i]
            when {
                c == '\\' -> i += 2
                c == '`' -> {
                    val run = runLength(text, i, to, '`')
                    val close = findRun(text, i + run, to, '`', run)
                    if (close > i + run) {
                        out += InlineMark(MarkType.CODE, i, close + run, run, run)
                        i = close + run
                    } else {
                        i += run
                    }
                }
                c == '!' && i + 1 < to && text[i + 1] == '[' -> i += 2
                c == '[' -> {
                    val link = link(text, i, to)
                    if (link != null) {
                        out += link
                        scan(text, link.contentStart, link.contentEnd, out)
                        i = link.end
                    } else {
                        i++
                    }
                }
                c == '*' || c == '_' || c == '~' -> {
                    val mark = emphasis(text, i, to)
                    if (mark != null) {
                        out += mark
                        scan(text, mark.contentStart, mark.contentEnd, out)
                        i = mark.end
                    } else {
                        i += runLength(text, i, to, c)
                    }
                }
                else -> i++
            }
        }
    }

    private fun emphasis(text: String, at: Int, to: Int): InlineMark? {
        val c = text[at]
        val run = runLength(text, at, to, c)
        val (size, type) = when {
            c == '~' && run == 2 -> 2 to MarkType.STRIKE
            c == '~' -> return null
            run >= 2 -> 2 to MarkType.BOLD
            else -> 1 to MarkType.ITALIC
        }
        val contentStart = at + size
        if (contentStart >= to || text[contentStart].isWhitespace()) return null
        if (c == '_' && at > 0 && text[at - 1].isLetterOrDigit()) return null
        var j = contentStart + 1
        while (j <= to - size) {
            if (text[j] == '\\') {
                j += 2
                continue
            }
            if (text[j] == '`') {
                // No se cierra una marca dentro de un trozo de codigo.
                val r = runLength(text, j, to, '`')
                val close = findRun(text, j + r, to, '`', r)
                j = if (close > 0) close + r else j + r
                continue
            }
            if (text[j] == c && text[j - 1] != c && !text[j - 1].isWhitespace() && closes(text, j, to, c, size)) {
                // `***x***`: la negrita cierra con los dos ultimos asteriscos.
                val closeAt = if (size == 2 && runLength(text, j, to, c) == 3) j + 1 else j
                return InlineMark(type, at, closeAt + size, size, size)
            }
            j++
        }
        return null
    }

    /** Cierre valido: misma longitud de racha y, con `_`, no seguido de letra. */
    private fun closes(text: String, at: Int, to: Int, c: Char, size: Int): Boolean {
        val run = runLength(text, at, to, c)
        if (size == 1 && run != 1) return false
        if (size == 2 && run < 2) return false
        val after = at + size
        if (c == '_' && after < to && text[after].isLetterOrDigit()) return false
        return true
    }

    private fun link(text: String, at: Int, to: Int): InlineMark? {
        var depth = 0
        var j = at
        while (j < to) {
            when (text[j]) {
                '\\' -> j++
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            j++
        }
        if (j >= to || j + 1 >= to || text[j + 1] != '(') return null
        val urlEnd = text.indexOf(')', j + 2)
        if (urlEnd < 0 || urlEnd >= to) return null
        val url = text.substring(j + 2, urlEnd).trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        return InlineMark(MarkType.LINK, at, urlEnd + 1, 1, urlEnd + 1 - j, url)
    }

    private fun runLength(text: String, at: Int, to: Int, c: Char): Int {
        var n = 0
        while (at + n < to && text[at + n] == c) n++
        return n
    }

    /** Primera racha de exactamente [length] caracteres [c] desde [from]; -1 si no hay. */
    private fun findRun(text: String, from: Int, to: Int, c: Char, length: Int): Int {
        var j = from
        while (j < to) {
            if (text[j] == c) {
                val r = runLength(text, j, to, c)
                if (r == length) return j
                j += r
            } else {
                j++
            }
        }
        return -1
    }
}
