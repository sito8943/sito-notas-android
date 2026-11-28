package dev.sito8943.sitonotas.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.auth.SessionRepository
import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.core.data.entity.PendingStats
import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.NetworkException
import dev.sito8943.sitonotas.core.settings.AppLanguage
import dev.sito8943.sitonotas.core.settings.AppPreferences
import dev.sito8943.sitonotas.core.sync.SyncCoordinator
import dev.sito8943.sitonotas.core.sync.SyncStatus
import dev.sito8943.sitonotas.feature.auth.MIN_PASSWORD_LENGTH
import dev.sito8943.sitonotas.feature.auth.data.AuthApi
import dev.sito8943.sitonotas.feature.backup.ExportSummary
import dev.sito8943.sitonotas.feature.backup.ImportSummary
import dev.sito8943.sitonotas.feature.backup.InvalidBackupException
import dev.sito8943.sitonotas.feature.backup.NotesBackup
import dev.sito8943.sitonotas.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** Correo de la cuenta, o nulo sin sesion. */
    val accountEmail: String? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val lastSyncAt: Long? = null,
    val pending: Int = 0,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val language: AppLanguage? = null,
)

/** Leer y escribir el archivo elegido con el selector del sistema (SAF). */
interface BackupFiles {
    suspend fun read(uri: Uri): String
    suspend fun write(uri: Uri, text: String)
}

enum class PasswordError { CURRENT_REQUIRED, TOO_SHORT, MISMATCH, WRONG_CURRENT, NETWORK, OTHER }

/** Formulario de `Password.tsx`: actual, nueva y repeticion. */
data class PasswordFormState(
    val current: String = "",
    val new: String = "",
    val repeat: String = "",
    val loading: Boolean = false,
    val error: PasswordError? = null,
    /** Detalle del servidor para [PasswordError.OTHER]. */
    val errorDetail: String? = null,
    /** Cambiada: la sesion se cierra en [SIGN_OUT_DELAY_MILLIS]. */
    val done: Boolean = false,
)

sealed interface BackupStatus {
    data object Working : BackupStatus
    data class Exported(val summary: ExportSummary) : BackupStatus
    data class Imported(val summary: ImportSummary) : BackupStatus
    data object Invalid : BackupStatus
    data class Failed(val message: String) : BackupStatus
}

class SettingsViewModel(
    private val session: SessionRepository,
    private val preferences: AppPreferences,
    private val syncCoordinator: SyncCoordinator,
    pendingCount: Flow<PendingStats>,
    private val authApi: AuthApi,
    private val backup: NotesBackup,
    private val files: BackupFiles,
) : ViewModel() {

    private val _password = MutableStateFlow(PasswordFormState())
    val password: StateFlow<PasswordFormState> = _password.asStateFlow()

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    /** Sin decidir cuenta como "no", como en la web. */
    val analyticsAllowed: StateFlow<Boolean> = preferences.analyticsConsent
        .map { it == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val state: StateFlow<SettingsUiState> = combine(
        session.state,
        syncCoordinator.status,
        preferences.lastSyncAt,
        pendingCount,
        combine(preferences.themeName, preferences.languageTag) { theme, language -> theme to language },
    ) { sessionState, status, lastSync, pending, (theme, language) ->
        SettingsUiState(
            accountEmail = (sessionState as? SessionState.Authenticated)?.session?.let {
                it.email.ifBlank { it.username }
            },
            syncStatus = status,
            lastSyncAt = lastSync,
            pending = pending.count,
            theme = ThemePreference.fromName(theme),
            language = AppLanguage.fromTag(language),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun syncNow() {
        viewModelScope.launch { syncCoordinator.syncNow() }
    }

    /** Cierra la sesion; las notas locales se quedan en el telefono. */
    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferences.setThemeName(theme.name) }
    }

    fun setLanguage(language: AppLanguage?) {
        viewModelScope.launch { preferences.setLanguageTag(language?.tag) }
    }

    /** Solo se guarda: la app no envia analitica. */
    fun setAnalytics(allowed: Boolean) {
        viewModelScope.launch { preferences.setAnalyticsConsent(allowed) }
    }

    fun onCurrentPasswordChange(value: String) = _password.update { it.copy(current = value, error = null) }

    fun onNewPasswordChange(value: String) = _password.update { it.copy(new = value, error = null) }

    fun onRepeatPasswordChange(value: String) = _password.update { it.copy(repeat = value, error = null) }

    /**
     * `POST /auth/password/change`. El servidor revoca todas las sesiones, asi
     * que tras el aviso se cierra la de este telefono, como la web.
     */
    fun changePassword() {
        val form = _password.value
        val invalid = when {
            form.current.isEmpty() -> PasswordError.CURRENT_REQUIRED
            form.new.length < MIN_PASSWORD_LENGTH -> PasswordError.TOO_SHORT
            form.new != form.repeat -> PasswordError.MISMATCH
            else -> null
        }
        if (invalid != null) {
            _password.update { it.copy(error = invalid) }
            return
        }
        _password.update { it.copy(loading = true, error = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                // Renueva el token si hizo falta: el cambio no reintenta ante
                // 401, porque ahi 401 significa "contraseña actual incorrecta".
                authApi.session()
                authApi.changePassword(form.current, form.new)
                _password.value = PasswordFormState(done = true)
                delay(SIGN_OUT_DELAY_MILLIS)
                session.signOut()
                _password.value = PasswordFormState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val (kind, detail) = when {
                    error is ApiException && error.status == 401 -> PasswordError.WRONG_CURRENT to null
                    error is NetworkException -> PasswordError.NETWORK to null
                    error is ApiException -> PasswordError.OTHER to (error.body ?: error.message)
                    else -> PasswordError.OTHER to error.message
                }
                _password.update { it.copy(loading = false, error = kind, errorDetail = detail) }
            }
        }
    }

    fun export(uri: Uri) = runBackup {
        val (text, summary) = backup.export()
        files.write(uri, text)
        BackupStatus.Exported(summary)
    }

    fun import(uri: Uri) = runBackup { BackupStatus.Imported(backup.import(files.read(uri))) }

    private fun runBackup(block: suspend () -> BackupStatus) {
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            _backupStatus.value = try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: InvalidBackupException) {
                BackupStatus.Invalid
            } catch (error: Exception) {
                BackupStatus.Failed(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    companion object {
        /** `SIGN_OUT_DELAY_MS` de la web. */
        const val SIGN_OUT_DELAY_MILLIS = 2_000L
    }
}
