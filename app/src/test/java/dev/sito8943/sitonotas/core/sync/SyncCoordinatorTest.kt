package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.auth.AuthSession
import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.network.NetworkException
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Que sincronice sola: al entrar y cuando hay cambios locales. */
@RunWith(RobolectricTestRunner::class)
class SyncCoordinatorTest {

    private lateinit var database: NotesDatabase
    private lateinit var notes: NoteRepository
    private lateinit var api: FakeNotesApi
    private lateinit var scope: CoroutineScope
    private val session = MutableStateFlow<SessionState>(SessionState.Guest)

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        notes = NoteRepository(database, VersionClock())
        api = FakeNotesApi()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun coordinator(): SyncCoordinator {
        val state = InMemorySyncStateStore()
        return SyncCoordinator(
            engine = SyncEngine(SyncPush(database, api), SyncPull(database, api, state), state),
            pendingStats = database.sync().observePendingStats(),
            sessionState = session,
            scope = scope,
            debounceMillis = 50,
        ).also { it.start() }
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(20)
    }

    private fun signIn() {
        session.value = SessionState.Authenticated(AuthSession(1, "ana", "ana@example.com", "token"))
    }

    @Test
    fun `nothing is uploaded without an account`() = runBlocking {
        val sync = coordinator()
        notes.create("Local", "solo aqui")
        delay(300)

        assertTrue(api.calls.isEmpty())
        assertEquals(SyncStatus.Idle, sync.status.value)
    }

    @Test
    fun `signing in uploads what was written offline and downloads the rest`() = runBlocking {
        api.seedNote(
            RemoteNote(id = 5, title = "De la web", updatedAt = "2026-11-20T10:00:00", createdAt = "2026-11-20T10:00:00"),
        )
        val sync = coordinator()
        notes.create("Offline", "x")

        signIn()

        waitUntil { sync.status.value is SyncStatus.UpToDate }
        waitUntil { database.sync().pendingCount() == 0 }
        assertTrue(api.notes.values.any { it.title == "Offline" })
        assertTrue(database.notes().getByRemoteId(5) != null)
    }

    @Test
    fun `local edits are uploaded after the debounce`() = runBlocking {
        signIn()
        val sync = coordinator()
        waitUntil { sync.status.value is SyncStatus.UpToDate }

        notes.create("Nueva", "")

        waitUntil { api.notes.values.any { it.title == "Nueva" } }
    }

    @Test
    fun `an offline failure is reported as such and retried on the next trigger`() = runBlocking {
        api.failOn = mapOf("getCategories" to NetworkException("sin red"))
        signIn()
        val sync = coordinator()
        waitUntil { sync.status.value is SyncStatus.Failed }
        assertTrue((sync.status.value as SyncStatus.Failed).offline)

        api.failOn = emptyMap()
        val result = sync.syncNow()

        assertTrue(result.isSuccess)
        assertTrue(sync.status.value is SyncStatus.UpToDate)
    }
}
