package dev.sito8943.sitonotas.core.model

import java.text.Normalizer

/**
 * Busqueda de notas, portada de `SearchProvider/utils.ts` (`matchesSearch`).
 * Una nota coincide cuando:
 * - su titulo se parece a toda la busqueda (similitud > [SIMILARITY_THRESHOLD],
 *   tolera erratas: `string-similarity.compareTwoStrings`), o
 * - cada palabra buscada esta en el titulo o el contenido, sin importar orden,
 *   mayusculas ni tildes. Los enlaces Markdown cuentan por su texto, no por la URL.
 */
object SearchText {

    /** `SEARCH_SIMILARITY_THRESHOLD` de la web. */
    const val SIMILARITY_THRESHOLD = 0.3

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    fun matches(title: String, content: String, query: String): Boolean {
        val normalized = fold(query.trim())
        if (normalized.isEmpty()) return true
        val foldedTitle = fold(title)
        if (similarity(foldedTitle, normalized) > SIMILARITY_THRESHOLD) return true
        val haystack = foldedTitle + "\n" + fold(content.replace(MARKDOWN_LINK, "$1"))
        return normalized.split(WHITESPACE).all { haystack.contains(it) }
    }

    /**
     * Coeficiente de Dice sobre bigramas, igual que `compareTwoStrings` de
     * `string-similarity` 4.x: quita los espacios, 1 si son iguales, 0 si alguno
     * tiene menos de dos caracteres; cada bigrama cuenta una vez por aparicion.
     */
    fun similarity(first: String, second: String): Double {
        val a = first.replace(WHITESPACE, "")
        val b = second.replace(WHITESPACE, "")
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0
        val bigrams = HashMap<String, Int>()
        for (i in 0 until a.length - 1) {
            val bigram = a.substring(i, i + 2)
            bigrams[bigram] = (bigrams[bigram] ?: 0) + 1
        }
        var intersection = 0
        for (i in 0 until b.length - 1) {
            val bigram = b.substring(i, i + 2)
            val count = bigrams[bigram] ?: 0
            if (count > 0) {
                bigrams[bigram] = count - 1
                intersection++
            }
        }
        return 2.0 * intersection / (a.length + b.length - 2)
    }

    /** `DIACRITICS_PATTERN` de la web: solo las marcas combinantes U+0300-U+036F. */
    private val DIACRITICS = Regex("[\\u0300-\\u036f]")
    private val WHITESPACE = Regex("\\s+")

    /** `[texto](url)` e `![alt](url)` -> texto. */
    private val MARKDOWN_LINK = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
}
