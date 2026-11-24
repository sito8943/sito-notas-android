package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La subida contra una base de datos de verdad y un servidor de mentira. Lo
 * que se comprueba es el orden y el emparejamiento de ids: una nota que sale
 * antes que su categoria apunta a un id que el servidor no conoce, y un
 * `remoteId` mal emparejado hace que el siguiente PATCH pise otra nota.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPushTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private lateinit var categories: CategoryRepository
    private lateinit var api: FakeNotesApi
    private lateinit var push: SyncPush
    private val clock = VersionClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        notes = NoteRepository(database, clock)
        categories = CategoryRepository(database, clock)
        api = FakeNotesApi()
        push = SyncPush(database, api)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `categories go up before notes and notes reference their remote ids`() = runBlocking {
        val work = categories.create("Trabajo", null, "#3B82F6")
        val home = categories.create("Casa", "cosas de casa", "#22C55E")
        val first = notes.create("Primera", "uno", categoryIds = listOf(home))
        val second = notes.create("Segunda", "dos", categoryIds = listOf(work, home))

        val result = push.push()

        assertEquals(listOf("createCategories", "createNotes"), api.calls)
        val workRemote = database.categories().getById(work)!!.remoteId!!
        val homeRemote = database.categories().getById(home)!!.remoteId!!
        // Los ids llegan en orden de entrada y se guardan en ese orden.
        assertEquals(listOf(1000L, 1001L), listOf(workRemote, homeRemote))
        assertEquals(listOf("Primera", "Segunda"), api.createdNoteBodies.map { it.title })
        assertEquals(listOf(homeRemote), api.createdNoteBodies[0].categoryIds)
        assertEquals(setOf(workRemote, homeRemote), api.createdNoteBodies[1].categoryIds.toSet())
        assertEquals(1002L, database.notes().getById(first)!!.remoteId)
        assertEquals(1003L, database.notes().getById(second)!!.remoteId)
        assertEquals(0, database.sync().pendingCount())
        assertEquals(2, result.categoriesCreated)
        assertEquals(2, result.notesCreated)
    }

    @Test
    fun `a favorite created offline is created and then patched with favorite`() = runBlocking {
        val id = notes.create("Fav", "x", favorite = true)

        push.push()

        assertEquals(listOf("createNotes", "updateNotes"), api.calls)
        val remoteId = database.notes().getById(id)!!.remoteId!!
        assertTrue(api.notes.getValue(remoteId).favorite)
        assertFalse(database.notes().getById(id)!!.dirty)
    }

    @Test
    fun `an edit made while the request is in flight stays pending`() = runBlocking {
        val id = notes.create("Antes", "a")
        val row = database.notes().getById(id)!!
        // La subida leyo `row`; mientras tanto ella sigue escribiendo.
        notes.updateText(id, "Despues", "b")
        database.notes().markCreated(id, remoteId = 77, version = row.localVersion, stillDirty = false)

        val after = database.notes().getById(id)!!
        assertEquals(77L, after.remoteId)
        assertTrue("la edicion posterior no puede perderse", after.dirty)
    }

    @Test
    fun `edits go as a batch patch with all fields`() = runBlocking {
        val cat = categories.create("Ideas", null, null)
        val id = notes.create("T", "c")
        push.push()
        api.calls.clear()

        notes.updateText(id, "T2", "c2")
        notes.setCategories(id, listOf(cat))
        notes.setFavorite(id, true)
        push.push()

        assertEquals(listOf("updateNotes"), api.calls)
        val body = api.updatedNoteBodies.single()
        assertEquals("T2", body.title)
        assertEquals("c2", body.content)
        assertTrue(body.favorite)
        assertEquals(listOf(database.categories().getById(cat)!!.remoteId), body.categoryIds)
        assertEquals(0, database.sync().pendingCount())
    }

    @Test
    fun `a 404 in the batch isolates the note the server no longer has`() = runBlocking {
        val kept = notes.create("Sigue", "")
        val gone = notes.create("Purgada", "")
        val trashedGone = notes.create("En papelera", "")
        push.push()
        notes.trash(trashedGone)
        push.push()
        // El servidor las borra para siempre por su cuenta (web, purga de 30 dias).
        api.notes.remove(database.notes().getById(gone)!!.remoteId)
        api.notes.remove(database.notes().getById(trashedGone)!!.remoteId)
        listOf(kept, gone, trashedGone).forEach { notes.updateText(it, "editada $it", "") }
        api.calls.clear()

        push.push()

        assertEquals("updateNotes", api.calls.first())
        // La que sigue existiendo se actualizo.
        assertFalse(database.notes().getById(kept)!!.dirty)
        // La activa que ya no existe se vuelve a crear con lo local...
        val recreated = database.notes().getById(gone)!!
        assertNull(recreated.remoteId)
        push.push()
        assertNotNull(database.notes().getById(gone)!!.remoteId)
        // ...y la que estaba en la papelera desaparece tambien aqui.
        assertNull(database.notes().getById(trashedGone))
    }

    @Test
    fun `trash, restore and permanent delete map to their endpoints`() = runBlocking {
        val a = notes.create("A", "")
        val b = notes.create("B", "")
        val c = notes.create("C", "")
        push.push()
        notes.trash(a)
        notes.trash(b)
        push.push()
        assertTrue(api.notes.getValue(database.notes().getById(a)!!.remoteId!!).deletedAt != null)

        api.calls.clear()
        notes.restore(a)
        notes.deletePermanently(b)
        // Purgar sin pasar antes por la papelera remota: hay que mandarla a la
        // papelera primero, el backend solo borra para siempre lo que esta alli.
        notes.trash(c)
        notes.deletePermanently(c)
        val cRemote = database.notes().getById(c)!!.remoteId!!
        push.push()

        assertEquals(listOf("trashNotes", "restoreNotes", "deleteNotesPermanently"), api.calls)
        assertNull(api.notes.getValue(database.notes().getById(a)!!.remoteId!!).deletedAt)
        assertNull(database.notes().getById(b))
        assertNull(database.notes().getById(c))
        assertFalse(api.notes.containsKey(cRemote))
        assertEquals(0, database.sync().pendingCount())
    }

    @Test
    fun `a note that never reached the server is deleted locally without calls`() = runBlocking {
        val id = notes.create("Borrador", "")
        notes.trash(id)
        notes.deletePermanently(id)

        push.push()

        assertTrue(api.calls.isEmpty())
        assertNull(database.notes().getById(id))
    }

    @Test
    fun `deleting a synced category deletes it on the server and unlinks notes`() = runBlocking {
        val cat = categories.create("Viejo", null, null)
        val note = notes.create("N", "", categoryIds = listOf(cat))
        push.push()
        val remoteCat = database.categories().getById(cat)!!.remoteId!!
        api.calls.clear()

        categories.delete(cat)
        assertTrue(database.notes().categoryIdsOf(note).isEmpty())
        push.push()

        assertEquals(listOf("deleteCategoriesPermanently"), api.calls)
        assertFalse(api.categories.containsKey(remoteCat))
        assertNull(database.categories().getById(cat))
        // La nota no quedo pendiente: el servidor ya quita el enlace.
        assertFalse(database.notes().getById(note)!!.dirty)
    }

    @Test
    fun `a failure halfway keeps what was confirmed`() = runBlocking {
        categories.create("Una", null, null)
        notes.create("N", "")
        api.failOn = mapOf("createNotes" to ApiException(500))

        assertThrows(ApiException::class.java) { runBlocking { push.push() } }

        // La categoria subio y no se repetira; la nota sigue pendiente.
        assertEquals(1, database.sync().pendingCount())
        api.failOn = emptyMap()
        api.calls.clear()
        push.push()
        assertEquals(listOf("createNotes"), api.calls)
    }

    @Test
    fun `an old server that answers a count is rejected`() {
        val message = OutdatedServerException("/notes/batch", "2").message.orEmpty()
        assertTrue(message.contains("/notes/batch"))
    }
}
