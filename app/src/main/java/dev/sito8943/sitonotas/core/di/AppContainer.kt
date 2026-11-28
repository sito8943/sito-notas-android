package dev.sito8943.sitonotas.core.di

import android.content.Context
import dev.sito8943.sitonotas.BuildConfig
import dev.sito8943.sitonotas.core.auth.AndroidKeystoreSessionStore
import dev.sito8943.sitonotas.core.auth.SessionRepository
import dev.sito8943.sitonotas.core.auth.SessionStore
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.network.RestClient
import dev.sito8943.sitonotas.core.settings.AppPreferences
import dev.sito8943.sitonotas.core.sync.AccountGuard
import dev.sito8943.sitonotas.core.sync.NotesApi
import dev.sito8943.sitonotas.core.sync.RealtimeClient
import dev.sito8943.sitonotas.core.sync.SyncCoordinator
import dev.sito8943.sitonotas.core.sync.SyncEngine
import dev.sito8943.sitonotas.core.sync.SyncPull
import dev.sito8943.sitonotas.core.sync.SyncPush
import dev.sito8943.sitonotas.feature.auth.AuthViewModel
import dev.sito8943.sitonotas.feature.auth.data.AuthApi
import dev.sito8943.sitonotas.feature.categories.CategoriesViewModel
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.NoteEditorViewModel
import dev.sito8943.sitonotas.feature.notes.NoteRepository
import dev.sito8943.sitonotas.feature.notes.NotesViewModel
import dev.sito8943.sitonotas.feature.settings.SettingsViewModel
import dev.sito8943.sitonotas.feature.trash.TrashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/**
 * Unica raiz de construccion de dependencias, perezosa. Sin Hilt, igual que
 * SitoWallet.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Vive lo que el proceso: refresco de sesion, sync y autoguardado final. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        // Un campo nuevo del backend no debe romper una version que no lo conoce.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val database: NotesDatabase by lazy { NotesDatabase.build(appContext) }

    private val versionClock = VersionClock()

    val preferences: AppPreferences by lazy { AppPreferences(appContext) }

    private val sessionStore: SessionStore by lazy { AndroidKeystoreSessionStore(appContext, json) }

    /**
     * Dos clientes, una sesion: sito-auth y la API de notas son hosts
     * distintos, pero los dos leen el token y refrescan por `SessionRepository`.
     * El ciclo cliente <-> sesion se rompe con thunks, como en SitoWallet.
     */
    private val authClient: RestClient by lazy {
        RestClient(baseUrl = BuildConfig.AUTH_BASE_URL, json = json, tokens = { sessionRepository })
    }

    private val notesClient: RestClient by lazy {
        RestClient(baseUrl = BuildConfig.API_BASE_URL, json = json, tokens = { sessionRepository })
    }

    val authApi: AuthApi by lazy { AuthApi(authClient) }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(store = sessionStore, api = { authApi }, scope = applicationScope)
    }

    val noteRepository: NoteRepository by lazy { NoteRepository(database, versionClock) }

    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database, versionClock) }

    private val notesApi by lazy { NotesApi(notesClient) }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            engine = SyncEngine(
                push = SyncPush(database, notesApi),
                pull = SyncPull(database, notesApi, preferences),
                state = preferences,
            ),
            pendingStats = database.sync().observePendingStats(),
            sessionState = sessionRepository.state,
            scope = applicationScope,
            accountGuard = AccountGuard(database, preferences),
        )
    }

    /** Eventos del servidor -> sincronizacion. Arranca con la app; conecta solo en primer plano. */
    val realtimeClient: RealtimeClient by lazy {
        RealtimeClient(
            socketUrl = RealtimeClient.socketUrlOf(BuildConfig.API_BASE_URL),
            tokens = sessionRepository,
            sessionState = sessionRepository.state,
            scope = applicationScope,
            onRemoteChange = { syncCoordinator.requestSync() },
        )
    }

    fun createAuthViewModel() = AuthViewModel(authApi, sessionRepository)

    fun createNotesViewModel() = NotesViewModel(noteRepository, categoryRepository)

    fun createNoteEditorViewModel(noteId: Long?) = NoteEditorViewModel(
        noteId = noteId,
        notes = noteRepository,
        categories = categoryRepository,
        saveScope = applicationScope,
    )

    fun createCategoriesViewModel() = CategoriesViewModel(categoryRepository)

    fun createTrashViewModel() = TrashViewModel(noteRepository)

    fun createSettingsViewModel() = SettingsViewModel(
        session = sessionRepository,
        preferences = preferences,
        syncCoordinator = syncCoordinator,
        pendingCount = database.sync().observePendingStats(),
    )
}
