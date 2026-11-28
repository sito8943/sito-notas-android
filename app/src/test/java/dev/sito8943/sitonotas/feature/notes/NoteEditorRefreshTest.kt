package dev.sito8943.sitonotas.feature.notes

import android.os.Looper
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.sync.inMemoryDatabase
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** El editor abierto sigue a la nota si la cambia una descarga y aqui no se edita. */
@RunWith(RobolectricTestRunner::class)
class NoteEditorRefreshTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private lateinit var categories: CategoryRepository
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var noteId = 0L

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        val clock = VersionClock()
        notes = NoteRepository(database, clock)
        categories = CategoryRepository(database, clock)
        noteId = runBlocking {
            val id = notes.create("Uno", "texto")
            val dao = database.notes()
            dao.update(dao.getById(id)!!.copy(remoteId = 50, dirty = false))
            id
        }
    }

    @After
    fun tearDown() {
        saveScope.cancel()
        database.close()
    }

    /** Lo que haria una descarga: el servidor pisa una fila sin nada pendiente. */
    private fun serverOverwrite(title: String, content: String) = runBlocking {
        val dao = database.notes()
        dao.update(dao.getById(noteId)!!.copy(title = title, content = content, dirty = false))
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < end) { "timeout" }
            Thread.sleep(10)
        }
    }

    private fun settle() = repeat(30) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
    }

    private fun editor(autosaveMillis: Long = 10_000) =
        NoteEditorViewModel(noteId, notes, categories, saveScope, autosaveMillis)

    @Test
    fun `a pulled change replaces the content when there are no local edits`() {
        val vm = editor()
        waitFor { !vm.state.value.loading }

        serverOverwrite("Remoto", "nuevo")

        waitFor { vm.state.value.content == "nuevo" }
        assertEquals("Remoto", vm.state.value.title)
        assertEquals(1, vm.state.value.externalRevision)
    }

    @Test
    fun `unsaved local edits win over a pulled change`() {
        val vm = editor()
        waitFor { !vm.state.value.loading }
        vm.onContentChange("local")

        serverOverwrite("Remoto", "nuevo")
        settle()

        assertEquals("local", vm.state.value.content)
        assertEquals(0, vm.state.value.externalRevision)
    }

    @Test
    fun `the echo of an own save does not touch the editor`() {
        val vm = editor()
        waitFor { !vm.state.value.loading }
        vm.onContentChange("local")
        vm.flush()
        waitFor { runBlocking { database.notes().getById(noteId)!!.content } == "local" }
        settle()

        assertEquals("local", vm.state.value.content)
        assertEquals(0, vm.state.value.externalRevision)
    }
}
