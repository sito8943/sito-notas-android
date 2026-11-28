package dev.sito8943.sitonotas.feature.notes

import dev.sito8943.sitonotas.feature.categories.Category
import java.text.Collator

/**
 * Reglas puras de la lista de notas portadas de la web: orden manual
 * (`moveId`, `mergeVisibleOrder` de `Notes.tsx`) y contadores de las pestañas
 * (`CategoryTabs.tsx`, `countTagUses`, `sortTagsByUses`).
 */
object NoteListRules {

    /**
     * Mueve [from] a la posicion de [to] (semantica de `arrayMove`): soltar
     * sobre una nota posterior la deja detras de ella; sobre una anterior,
     * delante. Ids desconocidos o iguales no cambian nada.
     */
    fun <T> move(ids: List<T>, from: T, to: T): List<T> {
        val fromIndex = ids.indexOf(from)
        val toIndex = ids.indexOf(to)
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return ids
        val result = ids.toMutableList()
        result.add(toIndex, result.removeAt(fromIndex))
        return result
    }

    /**
     * Orden completo a partir del orden nuevo de las visibles: las ocultas por
     * busqueda o filtro conservan su hueco y las visibles ocupan los suyos en
     * el orden nuevo. `[1,2,3,4,5]` con visibles `[5,1,3]` -> `[5,2,1,4,3]`.
     */
    fun <T> mergeVisibleOrder(allIds: List<T>, visibleIds: List<T>): List<T> {
        val visible = visibleIds.toHashSet()
        var next = 0
        return allIds.map { if (it in visible && next < visibleIds.size) visibleIds[next++] else it }
    }
}

/** Contadores de las pestañas: sobre todas las notas activas, sin busqueda. */
data class TabCounts(
    val all: Int = 0,
    val favorites: Int = 0,
    /** Por id de categoria. */
    val byCategory: Map<Long, Int> = emptyMap(),
) {
    fun of(categoryId: Long): Int = byCategory[categoryId] ?: 0

    companion object {
        fun of(notes: List<Note>): TabCounts {
            val uses = HashMap<Long, Int>()
            notes.forEach { note ->
                note.categories.map { it.id }.distinct().forEach { uses[it] = (uses[it] ?: 0) + 1 }
            }
            return TabCounts(all = notes.size, favorites = notes.count { it.favorite }, byCategory = uses)
        }
    }
}

/** `sortTagsByUses`: las mas usadas primero, luego por nombre; las vacias al final. */
fun List<Category>.sortedByUses(counts: TabCounts, collator: Collator = Collator.getInstance()): List<Category> =
    sortedWith(
        compareByDescending<Category> { counts.of(it.id) }
            .thenComparator { a, b -> collator.compare(a.name, b.name) },
    )
