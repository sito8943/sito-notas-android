package dev.sito8943.sitonotas.core.di

import android.content.Context
import android.net.Uri
import dev.sito8943.sitonotas.BuildConfig
import dev.sito8943.sitonotas.core.auth.AndroidKeystoreSessionStore
import dev.sito8943.sitonotas.core.auth.SessionRepository
import dev.sito8943.sitonotas.core.auth.SessionStore
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.network.RestClient
import dev.sito8943.sitonotas.core.settings.AppPreferences
import dev.sito8943.sitonotas.core.sync.NotesApi
import dev.sito8943.sitonotas.core.sync.ReorderApi
import dev.sito8943.sitonotas.core.sync.ReorderSync
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
import dev.sito8943.sitonotas.feature.backup.NotesBackup
import dev.sito8943.sitonotas.feature.settings.BackupFiles
import dev.sito8943.sitonotas.feature.settings.SettingsViewModel
import dev.sito8943.sitonotas.feature.trash.TrashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

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

    /** Orden manual: se aplica en local y sube como paso extra de la sync. */
    val reorderSync: ReorderSync by lazy {
        ReorderSync(
            database = database,
            store = preferences,
            api = ReorderApi(notesClient),
            requestSync = { syncCoordinator.requestSync() },
        )
    }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            engine = SyncEngine(
                push = SyncPush(database, notesApi),
                pull = SyncPull(database, notesApi, preferences),
                state = preferences,
                extraSteps = listOf(reorderSync),
            ),
            pendingStats = database.sync().observePendingStats(),
            sessionState = sessionRepository.state,
            scope = applicationScope,
        )
    }

    fun createAuthViewModel() = AuthViewModel(authApi, sessionRepository)

    fun createNotesViewModel() = NotesViewModel(
        notes = noteRepository,
        categories = categoryRepository,
        preferences = preferences,
        reorder = reorderSync::reorder,
    )

    fun createNoteEditorViewModel(noteId: Long?) = NoteEditorViewModel(
        noteId = noteId,
        notes = noteRepository,
        categories = categoryRepository,
        saveScope = applicationScope,
    )

    fun createCategoriesViewModel() = CategoriesViewModel(categoryRepository)

    fun createTrashViewModel() = TrashViewModel(noteRepository)

    val notesBackup: NotesBackup by lazy { NotesBackup(database, versionClock, json) }

    private val backupFiles = object : BackupFiles {
        override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: throw IOException("No se pudo abrir el archivo")
        }

        override suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
            // "wt": trunca si el usuario eligio un archivo existente.
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: throw IOException("No se pudo escribir el archivo")
        }
    }

    fun createSettingsViewModel() = SettingsViewModel(
        session = sessionRepository,
        preferences = preferences,
        syncCoordinator = syncCoordinator,
        pendingCount = database.sync().observePendingStats(),
        authApi = authApi,
        backup = notesBackup,
        files = backupFiles,
    )
}
