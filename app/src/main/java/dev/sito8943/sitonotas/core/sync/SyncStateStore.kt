package dev.sito8943.sitonotas.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * Estado persistente de la sincronizacion. Lo implementa `AppPreferences`
 * (DataStore); los tests usan uno en memoria.
 */
interface SyncStateStore {
    /** `updatedAt` (texto del servidor) mas alto descargado para ese usuario. */
    suspend fun pullCursor(userId: Long): String?
    suspend fun setPullCursor(userId: Long, cursor: String?)

    val lastSyncAt: Flow<Long?>
    suspend fun setLastSyncAt(epochMillis: Long)
}
