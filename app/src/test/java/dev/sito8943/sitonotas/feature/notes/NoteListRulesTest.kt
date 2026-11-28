package dev.sito8943.sitonotas.feature.notes

import dev.sito8943.sitonotas.feature.categories.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteListRulesTest {

    @Test
    fun `move follows array-move semantics`() {
        assertEquals(listOf(2, 3, 1, 4), NoteListRules.move(listOf(1, 2, 3, 4), 1, 3))
        assertEquals(listOf(4, 1, 2, 3), NoteListRules.move(listOf(1, 2, 3, 4), 4, 1))
        assertEquals(listOf(1, 2), NoteListRules.move(listOf(1, 2), 1, 9))
        assertEquals(listOf(1, 2), NoteListRules.move(listOf(1, 2), 2, 2))
    }

    @Test
    fun `hidden notes keep their slots like the web`() {
        assertEquals(
            listOf(5, 2, 1, 4, 3),
            NoteListRules.mergeVisibleOrder(listOf(1, 2, 3, 4, 5), listOf(5, 1, 3)),
        )
        assertEquals(listOf(1, 2), NoteListRules.mergeVisibleOrder(listOf(1, 2), emptyList()))
    }

    @Test
    fun `counts cover every active note and sort tags by use`() {
        val work = Category(1, "Trabajo", null, null)
        val home = Category(2, "Casa", null, null)
        val empty = Category(3, "Aaa", null, null)
        val notes = listOf(
            note(1, favorite = true, work),
            note(2, favorite = false, work, home),
            note(3, favorite = false),
        )
        val counts = TabCounts.of(notes)
        assertEquals(3, counts.all)
        assertEquals(1, counts.favorites)
        assertEquals(2, counts.of(1))
        assertEquals(1, counts.of(2))
        assertEquals(0, counts.of(3))
        assertEquals(listOf(work, home, empty), listOf(empty, home, work).sortedByUses(counts))
    }

    private fun note(id: Long, favorite: Boolean, vararg categories: Category) = Note(
        id = id,
        title = "",
        content = "",
        favorite = favorite,
        updatedAt = 0,
        deletedAt = null,
        categories = categories.toList(),
        pendingSync = false,
    )
}
