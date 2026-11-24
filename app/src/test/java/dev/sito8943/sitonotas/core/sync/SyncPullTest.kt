package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La descarga y su regla de conflicto: con algo pendiente gana lo local; sin
 * nada pendiente gana el servidor.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPullTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private lateinit var categories: CategoryRepository
    private lateinit var api: FakeNotesApi
    private lateinit var state: InMemorySyncStateStore
    private lateinit var pull: SyncPull
    private lateinit var engine: SyncEngine
    private val clock = VersionClock()
    private val user = 7L

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        notes = NoteRepository(database, clock)
        categories = CategoryRepository(database, clock)
        api = FakeNotesApi()
        state = InMemorySyncStateStore()
        pull = SyncPull(database, api, state, pageSize = 2)
        engine = SyncEngine(SyncPush(database, api), pull, state)
    }

    @After
    fun tearDown() = database.close()

    private fun remoteNote(
        id: Long,
        title: String,
        updatedAt: String,
        categories: List<Long> = emptyList(),
        deletedAt: String? = null,
        favorite: Boolean = false,
        position: Int? = null,
    ) = RemoteNote(
        id = id,
        title = title,
        content = "contenido $title",
        favorite = favorite,
        position = position,
        categories = categories.map { RemoteCategoryRef(it) },
        createdAt = "2026-11-01T09:00:00",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    @Test
    fun `first pull downloads everything, page by page, and saves the newest updatedAt`() = runBlocking {
        api.seedCategory(RemoteCategory(10, "Trabajo", "desc", "#3B82F6", "2026-11-01T09:00:00"))
        api.seedNote(remoteNote(1, "Uno", "2026-11-20T10:00:00", categories = listOf(10), position = 0))
        api.seedNote(remoteNote(2, "Dos", "2026-11-22T08:30:00.123456"))
        api.seedNote(remoteNote(3, "Tres", "2026-11-21T10:00:00", deletedAt = "2026-11-21T10:00:00"))

        pull.pullCategories()
        val result = pull.pullNotes(user)

        assertEquals(3, result.notesUpserted)
        assertEquals(listOf<String?>(null), api.noteCursors)
        assertEquals("2026-11-22T08:30:00.123456", state.cursors[user])

        val active = notes.observeNotes().first()
        assertEquals(listOf("Uno", "Dos"), active.map { it.title })
        assertEquals(listOf("Trabajo"), active.first().categories.map { it.name })
        assertFalse(active.any { it.pendingSync })

        val trashed = notes.observeTrash().first().single()
        assertEquals("Tres", trashed.title)
        val row = database.notes().getByRemoteId(3)!!
        assertTrue(row.remoteDeleted)
        assertFalse("una nota bajada de la papelera no queda pendiente", row.pending)
        assertEquals(0, database.sync().pendingCount())
    }

    @Test
    fun `next pulls ask only for what changed since the cursor`() = runBlocking {
        api.seedNote(remoteNote(1, "Uno", "2026-11-20T10:00:00"))
        pull.pullNotes(user)
        api.seedNote(remoteNote(2, "Nueva", "2026-11-23T10:00:00"))

        val result = pull.pullNotes(user)

        assertEquals(listOf(null, "2026-11-20T10:00:00"), api.noteCursors)
        // El cursor es inclusivo: la del limite vuelve, y mezclarla otra vez no duplica.
        assertEquals(2, result.notesUpserted)
        assertEquals(2, notes.observeNotes().first().size)
        assertEquals("2026-11-23T10:00:00", state.cursors[user])
    }

    @Test
    fun `server wins over a clean local row`() = runBlocking {
        api.seedNote(remoteNote(1, "Original", "2026-11-20T10:00:00"))
        pull.pullNotes(user)
        api.seedNote(remoteNote(1, "Editada en la web", "2026-11-21T10:00:00", favorite = true))

        pull.pullNotes(user)

        val note = notes.observeNotes().first().single()
        assertEquals("Editada en la web", note.title)
        assertTrue(note.favorite)
    }

    @Test
    fun `local wins when the row has pending changes, and then it is pushed`() = runBlocking {
        api.seedNote(remoteNote(1, "Original", "2026-11-20T10:00:00"))
        pull.pullNotes(user)
        val local = database.notes().getByRemoteId(1)!!
        notes.updateText(local.id, "Editada en el movil", "nuevo")
        api.seedNote(remoteNote(1, "Editada en la web", "2026-11-21T10:00:00"))

        val result = pull.pullNotes(user)

        assertEquals(1, result.notesSkippedPending)
        assertEquals("Editada en el movil", database.notes().getById(local.id)!!.title)

        engine.sync(user)
        assertEquals("Editada en el movil", api.notes.getValue(1).title)
        assertEquals("Editada en el movil", database.notes().getById(local.id)!!.title)
        assertEquals(0, database.sync().pendingCount())
    }

    @Test
    fun `a local trash not yet pushed is not undone by the pull`() = runBlocking {
        api.seedNote(remoteNote(1, "N", "2026-11-20T10:00:00"))
        pull.pullNotes(user)
        val local = database.notes().getByRemoteId(1)!!
        notes.trash(local.id)
        api.seedNote(remoteNote(1, "N", "2026-11-21T10:00:00"))

        pull.pullNotes(user)

        assertNotNull(database.notes().getById(local.id)!!.deletedAt)
    }

    @Test
    fun `a note trashed or restored on the web follows the server`() = runBlocking {
        api.seedNote(remoteNote(1, "N", "2026-11-20T10:00:00"))
        pull.pullNotes(user)
        api.seedNote(remoteNote(1, "N", "2026-11-21T10:00:00", deletedAt = "2026-11-21T10:00:00"))
        pull.pullNotes(user)
        assertEquals(1, notes.observeTrash().first().size)

        api.seedNote(remoteNote(1, "N", "2026-11-22T10:00:00"))
        pull.pullNotes(user)
        assertEquals(1, notes.observeNotes().first().size)
        assertEquals(0, database.sync().pendingCount())
    }

    @Test
    fun `a note pointing to an unknown category pulls categories first`() = runBlocking {
        api.seedCategory(RemoteCategory(10, "Nueva", null, null, "2026-11-20T10:00:00"))
        api.seedNote(remoteNote(1, "N", "2026-11-20T10:00:00", categories = listOf(10)))

        pull.pullNotes(user)

        assertTrue(api.calls.contains("getCategories"))
        assertEquals(listOf("Nueva"), notes.observeNotes().first().single().categories.map { it.name })
    }

    @Test
    fun `an offline category with the same name as a remote one is linked, not duplicated`() = runBlocking {
        val local = categories.create("trabajo", "mia", "#EF4444")
        api.seedCategory(RemoteCategory(10, "Trabajo", null, "#3B82F6", "2026-11-20T10:00:00"))

        engine.sync(user)

        assertEquals(10L, database.categories().getById(local)!!.remoteId)
        assertFalse(api.calls.contains("createCategories"))
        // Gana lo local: se subio como edicion.
        assertEquals("trabajo", api.categories.getValue(10).name)
        assertEquals(1, database.categories().getAll().size)
    }

    @Test
    fun `categories deleted on the server disappear unless they have local changes`() = runBlocking {
        api.seedCategory(RemoteCategory(10, "Se va", null, null, "2026-11-20T10:00:00"))
        api.seedCategory(RemoteCategory(11, "Editada aqui", null, null, "2026-11-20T10:00:00"))
        api.seedNote(remoteNote(1, "N", "2026-11-20T10:00:00", categories = listOf(10)))
        pull.pullCategories()
        pull.pullNotes(user)
        val edited = database.categories().getByRemoteId(11)!!
        categories.update(edited.id, "Editada aqui 2", null, null)
        api.categories.remove(10)
        api.categories.remove(11)

        val result = pull.pullCategories()

        assertEquals(1, result.categoriesRemoved)
        assertNull(database.categories().getByRemoteId(10))
        assertNotNull(database.categories().getById(edited.id))
        assertTrue(notes.observeNotes().first().single().categories.isEmpty())
    }

    @Test
    fun `synced notes older than the server trash retention are dropped`() = runBlocking {
        var now = 0L
        val old = SyncPull(database, api, state, now = { now })
        api.seedNote(remoteNote(1, "Vieja", "2026-01-01T10:00:00", deletedAt = "2026-01-01T10:00:00"))
        old.pullNotes(user)
        assertEquals(1, notes.observeTrash().first().size)

        now = ServerTime.toEpochMillis("2026-01-01T10:00:00")!! + SyncPull.TRASH_RETENTION_MILLIS + 1
        old.pullNotes(user)

        assertTrue(notes.observeTrash().first().isEmpty())
    }
}
