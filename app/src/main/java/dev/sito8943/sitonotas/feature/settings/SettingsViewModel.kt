package dev.sito8943.sitonotas.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.auth.SessionRepository
import dev.sito8943.sitonotas.core.auth.SessionState
import dev.sito8943.sitonotas.core.data.entity.PendingStats
import dev.sito8943.sitonotas.core.settings.AppLanguage
import dev.sito8943.sitonotas.core.settings.AppPreferences
import dev.sito8943.sitonotas.core.sync.SyncCoordinator
import dev.sito8943.sitonotas.core.sync.SyncStatus
import dev.sito8943.sitonotas.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
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

class SettingsViewModel(
    private val session: SessionRepository,
    private val preferences: AppPreferences,
    private val syncCoordinator: SyncCoordinator,
    pendingCount: Flow<PendingStats>,
) : ViewModel() {

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
}
