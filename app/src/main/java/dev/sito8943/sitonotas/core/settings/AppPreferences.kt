package dev.sito8943.sitonotas.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sito8943.sitonotas.core.sync.PendingReorderStore
import dev.sito8943.sitonotas.core.sync.SyncStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "sito-notas-preferences",
)

/**
 * Preferencias del dispositivo: lo que no es dato de negocio y por tanto no
 * vive en Room ni se sincroniza. Tambien guarda el estado de la sincronizacion
 * (cursor de descarga y ultima sincronizacion correcta).
 */
class AppPreferences(context: Context) : SyncStateStore, PendingReorderStore {

    private val store = context.applicationContext.preferencesStore

    /** Etiqueta BCP 47 ("es", "en") o nulo para seguir al sistema. */
    val languageTag: Flow<String?> = store.data.map { it[LANGUAGE_TAG] }

    /** Nombre de `ThemePreference` ("LIGHT", "DARK", "SYSTEM") o nulo. */
    val themeName: Flow<String?> = store.data.map { it[THEME_NAME] }

    suspend fun setThemeName(name: String?) = setOrRemove(THEME_NAME, name)

    suspend fun setLanguageTag(tag: String?) = setOrRemove(LANGUAGE_TAG, tag)

    override val lastSyncAt: Flow<Long?> = store.data.map { it[LAST_SYNC_AT] }

    override suspend fun setLastSyncAt(epochMillis: Long) {
        store.edit { it[LAST_SYNC_AT] = epochMillis }
    }

    /**
     * El cursor va por usuario: si entra otra cuenta en el mismo telefono, su
     * primera descarga tiene que ser completa, no continuar la de la anterior.
     */
    override suspend fun pullCursor(userId: Long): String? =
        store.data.first()[pullCursorKey(userId)]

    override suspend fun setPullCursor(userId: Long, cursor: String?) =
        setOrRemove(pullCursorKey(userId), cursor)

    /** Vista de la lista de notas: nombre de `NotesViewMode` o nulo (lista). */
    val notesViewMode: Flow<String?> = store.data.map { it[NOTES_VIEW_MODE] }

    suspend fun setNotesViewMode(name: String?) = setOrRemove(NOTES_VIEW_MODE, name)

    /**
     * Consentimiento de analitica de uso, como `consent_notes` en la web
     * (`{analytics, version, decidedAt}`). Nulo = sin decidir (= no). Una
     * version distinta de [CONSENT_VERSION] cuenta como sin decidir.
     * La app no envia analitica: solo se guarda la eleccion.
     */
    val analyticsConsent: Flow<Boolean?> = store.data.map {
        if (it[CONSENT_VERSION_KEY] == CONSENT_VERSION) it[CONSENT_ANALYTICS] else null
    }

    suspend fun setAnalyticsConsent(allowed: Boolean, decidedAt: Long = System.currentTimeMillis()) {
        store.edit {
            it[CONSENT_ANALYTICS] = allowed
            it[CONSENT_VERSION_KEY] = CONSENT_VERSION
            it[CONSENT_DECIDED_AT] = decidedAt
        }
    }

    override suspend fun pendingReorder(): List<Long>? =
        store.data.first()[PENDING_REORDER]?.let(::decodeIds)

    override suspend fun setPendingReorder(localIds: List<Long>) {
        store.edit { it[PENDING_REORDER] = localIds.joinToString(",") }
    }

    override suspend fun clearPendingReorderIf(expected: List<Long>) {
        store.edit { preferences ->
            if (preferences[PENDING_REORDER]?.let(::decodeIds) == expected) preferences.remove(PENDING_REORDER)
        }
    }

    private fun decodeIds(raw: String): List<Long> =
        raw.split(',').mapNotNull { it.trim().toLongOrNull() }

    private suspend fun setOrRemove(key: Preferences.Key<String>, value: String?) {
        store.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }

    private companion object {
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val NOTES_VIEW_MODE = stringPreferencesKey("notes_view_mode")
        val PENDING_REORDER = stringPreferencesKey("pending_reorder")
        val CONSENT_ANALYTICS = booleanPreferencesKey("consent_analytics")
        val CONSENT_VERSION_KEY = intPreferencesKey("consent_version")
        val CONSENT_DECIDED_AT = longPreferencesKey("consent_decided_at")

        /** `CONSENT_VERSION` de la web. */
        const val CONSENT_VERSION = 1

        fun pullCursorKey(userId: Long) = stringPreferencesKey("pull_cursor_$userId")
    }
}
