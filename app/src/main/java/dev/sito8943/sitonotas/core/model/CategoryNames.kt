package dev.sito8943.sitonotas.core.model

/** Reglas de nombre de categoria, portadas de `apps/notes/src/features/notes/utils.ts`. */
object CategoryNames {
    /** `TAG_NAME_MAX_LENGTH` de la web. */
    const val MAX_LENGTH = 40

    /** `TAG_COLORS` de la web: legibles en claro y oscuro, `#RRGGBB` para la API. */
    val COLORS = listOf(
        "#3B82F6",
        "#22C55E",
        "#F59E0B",
        "#EF4444",
        "#A855F7",
        "#EC4899",
        "#14B8A6",
        "#F97316",
    )

    /** `normalizeTagName`: recortado, espacios internos colapsados, tope de longitud. */
    fun normalize(name: String): String =
        name.trim().replace(WHITESPACE, " ").take(MAX_LENGTH)

    /** `toTagKey`: la comparacion que tambien hace el servidor (sin mayusculas ni tildes). */
    fun key(name: String): String = SearchText.fold(normalize(name))

    /** `pickTagColor`: el color de la paleta que menos categorias usan (el primero si empatan). */
    fun pickColor(existing: List<String?>): String {
        val uses = COLORS.associateWith { color ->
            existing.count { it?.uppercase() == color }
        }
        return COLORS.minBy { uses.getValue(it) }
    }

    private val WHITESPACE = Regex("\\s+")
}
