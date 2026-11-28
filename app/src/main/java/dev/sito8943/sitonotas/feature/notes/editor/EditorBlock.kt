package dev.sito8943.sitonotas.feature.notes.editor

/**
 * Bloque del editor enriquecido. El documento es una lista de bloques y cada
 * bloque es, salvo los de codigo, **una linea** del Markdown: `prefix` es la
 * sintaxis de bloque tal y como estaba escrita (`"  - [ ] "`, `"## "`,
 * `"> "`) y `text` lo que edita el usuario.
 *
 * El Markdown nunca se reescribe: [source] devuelve `prefix + text`, asi que
 * leer y volver a escribir una nota da exactamente el mismo texto. Solo cambia
 * la linea que el usuario toca, y solo en lo que toca.
 */
data class EditorBlock(
    val id: Long,
    val type: BlockType,
    val prefix: String,
    val text: String,
    /** Encabezado: 1..6. */
    val level: Int = 0,
    /** Sangria de las listas, en columnas (tab = 4). */
    val indent: Int = 0,
    val checked: Boolean = false,
    /** Codigo: linea de cierre (```` ``` ````), nula si el bloque no se cerro. */
    val closing: String? = null,
    /** Codigo: si hay al menos una linea entre las vallas (`text` vacio es ambiguo). */
    val hasBody: Boolean = true,
    /** Imagen: la URL de `![alt](url)`; el Markdown sigue entero en `text`. */
    val imageUrl: String? = null,
    /** Imagen: el texto alternativo. */
    val imageAlt: String = "",
) {
    val source: String
        get() = when (type) {
            BlockType.CODE -> buildString {
                append(prefix)
                if (hasBody || text.isNotEmpty()) append('\n').append(text)
                if (closing != null) append('\n').append(closing)
            }
            else -> prefix + text
        }

    val isList: Boolean get() = type == BlockType.BULLET || type == BlockType.ORDERED || type == BlockType.TASK
}

enum class BlockType {
    PARAGRAPH,
    HEADING,
    BULLET,
    ORDERED,
    TASK,
    QUOTE,
    CODE,
    RULE,

    /**
     * Imagen sola en su linea (`![alt](url)`), como el nodo `image` de la web.
     * `text` es la linea entera tal cual: se pinta la imagen, no se edita.
     */
    IMAGE,

    /**
     * Linea que el editor no sabe pintar (tabla, HTML, imagen con texto, subrayado de
     * encabezado setext, definicion de enlace). Se muestra y edita como texto
     * Markdown, sin perder nada.
     */
    RAW,
}
