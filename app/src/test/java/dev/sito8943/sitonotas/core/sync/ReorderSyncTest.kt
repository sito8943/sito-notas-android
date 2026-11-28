package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.network.NetworkException
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReorderSyncTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private val store = InMemoryReorderStore()
    private val sent = mutableListOf<List<Long>>()
    private var offline = false
    private var syncRequests = 0
    private lateinit var reorder: ReorderSync

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        notes = NoteRepository(database, VersionClock())
        reorder = ReorderSync(
            database = database,
            store = store,
            api = { ids ->
                if (offline) throw NetworkException("sin red")
                sent += ids
                ids.size
            },
            requestSync = { syncRequests++ },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `reorder applies locally without dirtying and queues the order`() = runBlocking {
        val a = synced("A", remoteId = 10)
        val b = synced("B", remoteId = 11)

        reorder.reorder(listOf(b, a))

        assertEquals(0, database.notes().getById(b)!!.position)
        assertEquals(1, database.notes().getById(a)!!.position)
        assertFalse(database.notes().getById(a)!!.dirty)
        assertEquals(listOf(b, a), store.pendingReorder())
        assertEquals(1, syncRequests)
    }

    @Test
    fun `the step sends remote ids top first and clears the queue`() = runBlocking {
        val a = synced("A", remoteId = 10)
        val local = notes.create("Sin subir", "")
        val trashed = synced("T", remoteId = 12)
        notes.trash(trashed)
        reorder.reorder(listOf(local, a, trashed))

        reorder.run(userId = 1)

        assertEquals(listOf(listOf(10L)), sent)
        assertNull(store.pendingReorder())
    }

    @Test
    fun `offline keeps the order pending for the next sync`() = runBlocking {
        val a = synced("A", remoteId = 10)
        reorder.reorder(listOf(a))
        offline = true

        assertThrows(NetworkException::class.java) { runBlocking { reorder.run(1) } }
        assertEquals(listOf(a), store.pendingReorder())

        offline = false
        reorder.run(1)
        assertTrue(store.pendingReorder() == null && sent.size == 1)
    }

    @Test
    fun `a newer order queued while sending is not cleared`() = runBlocking {
        val a = synced("A", remoteId = 10)
        val b = synced("B", remoteId = 11)
        reorder.reorder(listOf(a, b))
        store.onRead = { store.value = listOf(b, a) }

        reorder.run(1)

        assertEquals(listOf(b, a), store.pendingReorder())
    }

    private suspend fun synced(title: String, remoteId: Long): Long {
        val id = notes.create(title, "")
        val row = database.notes().getById(id)!!
        database.notes().update(row.copy(remoteId = remoteId, dirty = false))
        return id
    }

    private class InMemoryReorderStore : PendingReorderStore {
        var value: List<Long>? = null
        var onRead: (() -> Unit)? = null

        override suspend fun pendingReorder(): List<Long>? = value.also {
            onRead?.invoke()
            onRead = null
        }

        override suspend fun setPendingReorder(localIds: List<Long>) {
            value = localIds
        }

        override suspend fun clearPendingReorderIf(expected: List<Long>) {
            if (value == expected) value = null
        }
    }
}
