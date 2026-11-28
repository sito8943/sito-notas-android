package dev.sito8943.sitonotas.core.sync

import kotlinx.coroutines.flow.Flow

/** Cursores de descarga, todos por usuario y en el reloj del servidor. */
enum class SyncCursor {
    /** `updatedAt` mas alto de las notas descargadas. */
    NOTES,

    /** Hora del servidor (`until`) desde la que bajar categorias cambiadas. */
    CATEGORIES,

    /** `until` de `GET /notes/deleted`. */
    NOTE_TOMBSTONES,

    /** `until` de `GET /categories/deleted`. */
    CATEGORY_TOMBSTONES,
}

/**
 * Estado persistente de la sincronizacion. Lo implementa `AppPreferences`
 * (DataStore); los tests usan uno en memoria.
 */
interface SyncStateStore {
    suspend fun cursor(userId: Long, kind: SyncCursor): String?
    suspend fun setCursor(userId: Long, kind: SyncCursor, value: String?)

    /** `updatedAt` (texto del servidor) mas alto descargado para ese usuario. */
    suspend fun pullCursor(userId: Long): String? = cursor(userId, SyncCursor.NOTES)
    suspend fun setPullCursor(userId: Long, cursor: String?) = setCursor(userId, SyncCursor.NOTES, cursor)

    suspend fun clearCursors(userId: Long) {
        SyncCursor.entries.forEach { setCursor(userId, it, null) }
    }

    /** Cuenta duena de lo sincronizado en este telefono; nulo = ninguna aun. */
    suspend fun dataOwner(): Long?
    suspend fun setDataOwner(userId: Long)

    val lastSyncAt: Flow<Long?>
    suspend fun setLastSyncAt(epochMillis: Long)
}
