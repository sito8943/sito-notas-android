package dev.sito8943.sitonotas.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
class AppPreferences(context: Context) : SyncStateStore {

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

    private suspend fun setOrRemove(key: Preferences.Key<String>, value: String?) {
        store.edit { preferences ->
            if (value == null) preferences.remove(key) else preferences[key] = value
        }
    }

    private companion object {
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_NAME = stringPreferencesKey("theme_name")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")

        fun pullCursorKey(userId: Long) = stringPreferencesKey("pull_cursor_$userId")
    }
}
