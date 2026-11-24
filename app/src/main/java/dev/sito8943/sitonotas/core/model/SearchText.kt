package dev.sito8943.sitonotas.core.model

import java.text.Normalizer

/**
 * Busqueda de notas, portada de `SearchProvider/utils.ts`: coincide cuando cada
 * palabra buscada esta en el titulo o el contenido, sin importar orden,
 * mayusculas ni tildes. Los enlaces Markdown cuentan por su texto, no por la URL.
 *
 * La web acepta ademas titulos "parecidos" (string-similarity); aqui no, para
 * no traer una dependencia por eso.
 */
object SearchText {

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    fun matches(title: String, content: String, query: String): Boolean {
        val normalized = fold(query.trim())
        if (normalized.isEmpty()) return true
        val haystack = fold(title) + "\n" + fold(content.replace(MARKDOWN_LINK, "$1"))
        return normalized.split(WHITESPACE).all { haystack.contains(it) }
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")

    /** `[texto](url)` e `![alt](url)` -> texto. */
    private val MARKDOWN_LINK = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
}
