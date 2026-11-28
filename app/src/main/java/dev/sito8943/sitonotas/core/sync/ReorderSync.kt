package dev.sito8943.sitonotas.core.sync

import androidx.room.withTransaction
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.network.RestClient

/**
 * Orden manual pendiente de subir. Guarda ids **locales** (de arriba abajo):
 * una nota creada sin conexion aun no tiene `remoteId` cuando se reordena.
 * Lo implementa `AppPreferences` (DataStore); los tests usan uno en memoria.
 */
interface PendingReorderStore {
    suspend fun pendingReorder(): List<Long>?
    suspend fun setPendingReorder(localIds: List<Long>)

    /** Borra el pendiente solo si sigue siendo [expected] (no hubo otro reorden mientras se subia). */
    suspend fun clearPendingReorderIf(expected: List<Long>)
}

/** `PATCH /notes/reorder`. */
fun interface ReorderEndpoint {
    /**
     * @param remoteIds todas las notas activas, de arriba abajo. El servidor
     *   pone `position = indice`, ignora las de otros, borradas o inexistentes y
     *   devuelve cuantas eran del usuario.
     */
    suspend fun reorder(remoteIds: List<Long>): Int
}

class ReorderApi(private val client: RestClient) : ReorderEndpoint {
    override suspend fun reorder(remoteIds: List<Long>): Int =
        client.patch<Int, List<Long>>("/notes/reorder", remoteIds)
}

/**
 * Orden manual de las notas, como `useReorderNotes` en la web: se aplica en
 * local al momento (`position = indice`) y se sube con `PATCH /notes/reorder`.
 *
 * Sin conexion (o sin cuenta) el orden queda en [store] y se manda en la
 * siguiente sincronizacion como un [SyncStep]; solo se guarda el ultimo orden,
 * que ya contiene a los anteriores.
 */
class ReorderSync(
    private val database: NotesDatabase,
    private val store: PendingReorderStore,
    private val api: ReorderEndpoint,
    private val requestSync: () -> Unit,
) : SyncStep {

    private val dao get() = database.notes()

    /** [localIds]: todas las notas activas en el orden nuevo, de arriba abajo. */
    suspend fun reorder(localIds: List<Long>) {
        applyLocally(localIds)
        store.setPendingReorder(localIds)
        requestSync()
    }

    override suspend fun run(userId: Long) {
        val pending = store.pendingReorder() ?: return
        val remoteIds = pending.mapNotNull { id ->
            dao.getById(id)?.takeIf { it.deletedAt == null && !it.purged }?.remoteId
        }
        if (remoteIds.isNotEmpty()) api.reorder(remoteIds)
        // Por si una descarga de una pasada anterior piso el orden local antes
        // de que este llegara al servidor.
        applyLocally(pending)
        store.clearPendingReorderIf(pending)
    }

    /** No marca `dirty` ni toca `updatedAt`: el orden viaja por su endpoint. */
    private suspend fun applyLocally(localIds: List<Long>) {
        database.withTransaction {
            localIds.forEachIndexed { index, id -> dao.setPosition(id, index) }
        }
    }
}
