package dev.sito8943.sitonotas.feature.backup

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.sync.inMemoryDatabase
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesBackupTest {

    private lateinit var database: NotesDatabase
    private lateinit var backup: NotesBackup
    private val clock = VersionClock()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        backup = NotesBackup(database, clock, json, now = { 1_700_000_000_000 })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `export uses the server ExportNoteDTO shape and round-trips into another device`() = runBlocking {
        val categories = CategoryRepository(database, clock)
        val notes = NoteRepository(database, clock)
        val work = categories.create("Trabajo", "oficina", "#3B82F6")
        notes.create("Plan", "**hola**", categoryIds = listOf(work))
        val trashed = notes.create("Vieja", "")
        notes.trash(trashed)

        val (text, summary) = backup.export()
        assertEquals(ExportSummary(notes = 1, categories = 1), summary)
        assertTrue(text.startsWith("""{"categories":[{"name":"Trabajo","description":"oficina","color":"#3B82F6""""))
        assertTrue(text.contains(""""notes":[{"title":"Plan","content":"**hola**","categories":[{"name":"Trabajo""""))

        val other = inMemoryDatabase()
        try {
            val result = NotesBackup(other, VersionClock(), json).import(text)
            assertEquals(ImportSummary(notesCreated = 1, notesSkipped = 0, categoriesCreated = 1), result)
            val imported = other.notes().getAll().single()
            assertEquals("Plan", imported.title)
            assertNull(imported.remoteId)
            assertTrue(imported.dirty)
            assertEquals(listOf("Trabajo"), other.categories().getActive().map { it.name })

            // Volver a importar no duplica: mismo titulo y createdAt.
            val again = NotesBackup(other, VersionClock(), json).import(text)
            assertEquals(ImportSummary(notesCreated = 0, notesSkipped = 1, categoriesCreated = 0), again)
        } finally {
            other.close()
        }
    }

    @Test
    fun `a bare NoteDTO array links categories by name and ignores favorite`() = runBlocking {
        CategoryRepository(database, clock).create("Trabajo", null, "#3B82F6")
        val text = """
            [{"id":9,"title":"A","content":"x","favorite":true,"position":3,
              "categories":[{"id":4,"name":"TRABAJO"},{"name":"Nueva","color":"#abc"}],
              "createdAt":"2026-03-21T09:30:00","updatedAt":"2026-03-21T09:30:00"}]
        """.trimIndent()

        val result = backup.import(text)

        assertEquals(ImportSummary(notesCreated = 1, notesSkipped = 0, categoriesCreated = 1), result)
        val note = database.notes().getAll().single()
        assertEquals(false, note.favorite)
        assertNull(note.position)
        assertEquals(NotesBackup.parse("2026-03-21T09:30:00"), note.createdAt)
        assertEquals(2, database.notes().categoryIdsOf(note.id).size)
        assertEquals("#ABC", database.categories().getActive().first { it.name == "Nueva" }.color)
    }

    @Test
    fun `garbage is rejected`() {
        assertThrows(InvalidBackupException::class.java) { runBlocking { backup.import("hola") } }
        assertThrows(InvalidBackupException::class.java) { runBlocking { backup.import("{}") } }
    }
}
