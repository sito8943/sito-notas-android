package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El contrato nuevo del servidor (tombstones, categorias incrementales,
 * PATCH por lotes parcial, fechas con offset, `clientId`) y que con el
 * servidor anterior todo siga como antes.
 */
@RunWith(RobolectricTestRunner::class)
class SyncContractTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private lateinit var categories: CategoryRepository
    private lateinit var api: FakeNotesApi
    private lateinit var state: InMemorySyncStateStore
    private lateinit var engine: SyncEngine
    private val user = 7L

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        val clock = VersionClock()
        notes = NoteRepository(database, clock)
        categories = CategoryRepository(database, clock)
        api = FakeNotesApi()
        state = InMemorySyncStateStore()
        engine = SyncEngine(SyncPush(database, api), SyncPull(database, api, state, pageSize = 2), state)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun remoteIdOf(localId: Long) = database.notes().getById(localId)?.remoteId

    @Test
    fun `a note deleted forever on the web disappears here`() = runBlocking {
        api.newServer = true
        val id = notes.create("Uno", "texto")
        engine.sync(user)
        val remoteId = remoteIdOf(id)!!

        api.hardDeleteNote(remoteId)
        val result = engine.sync(user)

        assertNull(database.notes().getById(id))
        assertEquals(1, result.notesRemoved)
        assertNotNull(state.cursor(user, SyncCursor.NOTE_TOMBSTONES))
    }

    @Test
    fun `tombstones are asked from the last until, not from the start`() = runBlocking {
        api.newServer = true
        engine.sync(user)
        val first = state.cursor(user, SyncCursor.NOTE_TOMBSTONES)!!
        assertTrue(first.endsWith("Z"))

        api.hardDeleteNote(api.nextId + 50) // ajena a este telefono
        engine.sync(user)

        assertNotEquals(first, state.cursor(user, SyncCursor.NOTE_TOMBSTONES))
    }

    @Test
    fun `a pending edit of a note deleted forever is re-created with a new client id`() = runBlocking {
        api.newServer = true
        val id = notes.create("Uno", "texto")
        engine.sync(user)
        val before = database.notes().getById(id)!!
        api.hardDeleteNote(before.remoteId!!)

        // Editada aqui, pero la subida llega cuando el servidor ya no la tiene:
        // el lote nuevo la salta y la nota se vuelve a crear.
        notes.updateText(id, "Uno editada", "texto")
        engine.sync(user)
        // Desenlazada en esa pasada; el alta va en la siguiente (la dispara
        // lo pendiente en `SyncCoordinator`).
        assertNull(remoteIdOf(id))
        engine.sync(user)

        val after = database.notes().getById(id)!!
        assertNotNull(after.remoteId)
        assertNotEquals(before.remoteId, after.remoteId)
        assertNotEquals(before.clientId, after.clientId)
        assertEquals("Uno editada", api.notes.getValue(after.remoteId!!).title)
        assertFalse("sin reintento fila a fila", "updateNote" in api.calls)
    }

    @Test
    fun `a trashed note missing from the partial batch is dropped`() = runBlocking {
        api.newServer = true
        val id = notes.create("Uno", "texto")
        engine.sync(user)
        notes.trash(id)
        engine.sync(user)
        api.notes.remove(remoteIdOf(id)!!) // purgada sin tombstone (caso limite)

        notes.updateText(id, "otra", "cosa")
        engine.sync(user)

        assertNull(database.notes().getById(id))
    }

    @Test
    fun `the current server keeps the full pulls and the 404 fallback`() = runBlocking {
        val id = notes.create("Uno", "texto")
        engine.sync(user)
        val remoteId = remoteIdOf(id)!!
        api.notes.remove(remoteId)

        notes.updateText(id, "Uno editada", "texto")
        engine.sync(user)
        engine.sync(user)

        assertTrue("updateNote" in api.calls)
        assertNotEquals(remoteId, remoteIdOf(id))
        assertEquals(listOf<String?>(null, null, null), api.categoryCursors)
        assertNull(state.cursor(user, SyncCursor.NOTE_TOMBSTONES))
        assertNull(state.cursor(user, SyncCursor.CATEGORIES))
    }

    @Test
    fun `categories are pulled in full once and then incrementally`() = runBlocking {
        api.newServer = true
        api.seedCategory(RemoteCategory(10, "Trabajo", updatedAt = "2026-11-01T09:00:00Z"))
        api.seedCategory(RemoteCategory(11, "Casa", updatedAt = "2026-11-01T09:00:00Z"))
        engine.sync(user)
        assertEquals(2, database.categories().getAll().size)

        api.seedCategory(RemoteCategory(12, "Viajes", updatedAt = "2026-11-30T09:00:00Z"))
        api.hardDeleteCategory(10)
        engine.sync(user)

        assertNull(api.categoryCursors[0])
        assertNotNull(api.categoryCursors[1])
        assertEquals(setOf(11L, 12L), database.categories().getAll().mapNotNull { it.remoteId }.toSet())
    }

    @Test
    fun `a category moved to the trash on the server goes away on an incremental pull`() = runBlocking {
        api.newServer = true
        api.seedCategory(RemoteCategory(10, "Trabajo", updatedAt = "2026-11-01T09:00:00Z"))
        engine.sync(user)

        api.seedCategory(
            RemoteCategory(10, "Trabajo", updatedAt = "2026-11-30T09:00:00Z", deletedAt = "2026-11-30T09:00:00Z"),
        )
        engine.sync(user)

        assertTrue(database.categories().getAll().isEmpty())
    }

    @Test
    fun `creates carry the local client id`() = runBlocking {
        val categoryId = categories.create("Trabajo", null, "#3B82F6")
        val noteId = notes.create("Uno", "texto", categoryIds = listOf(categoryId))
        engine.sync(user)

        val note = database.notes().getById(noteId)!!
        val category = database.categories().getById(categoryId)!!
        assertNotNull(note.clientId)
        assertEquals(note.clientId, api.createdNoteBodies.single().clientId)
        assertEquals(category.clientId, api.createdCategoryBodies.single().clientId)
    }

    @Test
    fun `notes cursor keeps the offset form the server sent`() = runBlocking {
        api.newServer = true
        notes.create("Uno", "texto")
        engine.sync(user)

        val cursor = state.cursor(user, SyncCursor.NOTES)!!
        assertTrue(cursor, cursor.endsWith("Z"))
        engine.sync(user)
        assertEquals(cursor, api.noteCursors.last())
    }
}
