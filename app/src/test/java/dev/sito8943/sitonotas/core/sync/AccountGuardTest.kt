package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Cambio de cuenta: nada de la cuenta A se copia a la B sin preguntar. */
@RunWith(RobolectricTestRunner::class)
class AccountGuardTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private lateinit var categories: CategoryRepository
    private lateinit var api: FakeNotesApi
    private lateinit var state: InMemorySyncStateStore
    private lateinit var engine: SyncEngine
    private lateinit var guard: AccountGuard
    private val ana = 1L
    private val bea = 2L

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        val clock = VersionClock()
        notes = NoteRepository(database, clock)
        categories = CategoryRepository(database, clock)
        api = FakeNotesApi()
        state = InMemorySyncStateStore()
        engine = SyncEngine(SyncPush(database, api), SyncPull(database, api, state), state)
        guard = AccountGuard(database, state)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun syncAs(userId: Long): Int = guard.check(userId).also { if (it == 0) engine.sync(userId) }

    @Test
    fun `notes made without an account go to the first account`() = runBlocking {
        val id = notes.create("Sin cuenta", "texto")

        assertEquals(0, syncAs(ana))

        assertNotNull(database.notes().getById(id)!!.remoteId)
        assertEquals(ana, state.owner)
    }

    @Test
    fun `another account with nothing pending wipes the previous account's synced data`() = runBlocking {
        categories.create("Trabajo", null, null)
        notes.create("De Ana", "texto")
        syncAs(ana)
        val created = api.createdNoteBodies.size

        // Solo el guardia: el servidor de mentira es el mismo para las dos.
        assertEquals(0, guard.check(bea))

        assertTrue(database.notes().getAll().isEmpty())
        assertTrue(database.categories().getAll().isEmpty())
        assertEquals(created, api.createdNoteBodies.size)
        assertEquals(bea, state.owner)
        assertNull(state.cursor(ana, SyncCursor.NOTES))
    }

    @Test
    fun `pending changes of the previous account wait for a decision`() = runBlocking {
        val id = notes.create("De Ana", "texto")
        syncAs(ana)
        notes.updateText(id, "De Ana editada", "texto")

        assertEquals(1, syncAs(bea))

        assertEquals("nada se toca aun", "De Ana editada", database.notes().getById(id)!!.title)
        assertEquals(ana, state.owner)
    }

    @Test
    fun `upload turns the pending changes into creates in the new account`() = runBlocking {
        val categoryId = categories.create("Trabajo", null, null)
        val edited = notes.create("Editada", "texto", categoryIds = listOf(categoryId))
        val untouched = notes.create("Sin tocar", "texto")
        syncAs(ana)
        val anaRemote = database.notes().getById(edited)!!.remoteId
        notes.updateText(edited, "Editada otra vez", "texto")

        guard.resolve(bea, upload = true)

        assertNull(database.notes().getById(untouched))
        val kept = database.notes().getById(edited)!!
        assertNull(kept.remoteId)
        assertEquals(listOf(categoryId), database.notes().categoryIdsOf(edited))
        assertNull(database.categories().getById(categoryId)!!.remoteId)

        assertEquals(0, syncAs(bea))
        val beaRemote = database.notes().getById(edited)!!.remoteId
        assertNotNull(beaRemote)
        assertTrue(beaRemote != anaRemote)
    }

    @Test
    fun `discard drops everything of the previous account`() = runBlocking {
        val id = notes.create("De Ana", "texto")
        syncAs(ana)
        notes.updateText(id, "editada", "texto")

        guard.resolve(bea, upload = false)

        assertTrue(database.notes().getAll().isEmpty())
        assertEquals(bea, state.owner)
        assertEquals(0, syncAs(bea))
    }
}
