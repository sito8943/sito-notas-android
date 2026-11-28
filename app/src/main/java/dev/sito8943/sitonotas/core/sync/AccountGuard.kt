package dev.sito8943.sitonotas.core.sync

import androidx.room.withTransaction
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.entity.newClientId

/**
 * Que las notas de una cuenta no acaben en otra al cambiar de cuenta en el
 * mismo telefono.
 *
 * Se guarda la cuenta duena de lo sincronizado. Lo creado sin cuenta sube a la
 * primera que entra (como siempre). Si entra **otra** cuenta:
 * - sin nada pendiente, lo de la anterior se borra de aqui (sigue en el
 *   servidor) y la nueva baja lo suyo desde cero;
 * - con cambios sin subir, no se toca nada hasta que la usuaria decida en un
 *   dialogo: subirlos a la cuenta nueva o descartarlos ([resolve]).
 *
 * Lo llama `SyncCoordinator` dentro de su mutex: nunca a la vez que una pasada.
 */
class AccountGuard(
    private val database: NotesDatabase,
    private val state: SyncStateStore,
) {

    /**
     * @return cuantos cambios pendientes de la cuenta anterior esperan una
     *   decision; 0 = se puede sincronizar.
     */
    suspend fun check(userId: Long): Int {
        val owner = state.dataOwner()
        if (owner == null) {
            state.setDataOwner(userId)
            return 0
        }
        if (owner == userId) return 0
        val pending = database.sync().pendingCount()
        if (pending > 0) return pending
        switchTo(userId, owner) {
            database.notes().deleteSynced()
            database.categories().deleteSynced()
        }
        return 0
    }

    /**
     * La decision del dialogo. [upload]: lo pendiente pasa a ser alta en la
     * cuenta nueva (con las categorias que use); si no, se descarta todo.
     */
    suspend fun resolve(userId: Long, upload: Boolean) {
        val owner = state.dataOwner() ?: return state.setDataOwner(userId)
        if (owner == userId) return
        switchTo(userId, owner) {
            if (upload) adoptPending() else {
                database.notes().deleteAll()
                database.categories().deleteAll()
            }
        }
    }

    private suspend fun adoptPending() {
        val notes = database.notes()
        val categories = database.categories()

        val keptNotes = notes.getAll().filter { it.pending && !it.purged }
        // Borrar para siempre algo de la otra cuenta no tiene sentido en esta.
        notes.deleteByIds(notes.getAll().filter { it.purged }.map { it.id })
        keptNotes.filter { it.remoteId != null }.forEach { notes.unlinkRemote(it.id, newClientId()) }

        val usedByKept = keptNotes.flatMap { notes.categoryIdsOf(it.id) }.toSet()
        val allCategories = categories.getAll()
        categories.deleteByIds(allCategories.filter { it.purged }.map { it.id })
        allCategories
            .filter { !it.purged && it.remoteId != null && (it.dirty || it.id in usedByKept) }
            .forEach { categories.unlinkRemote(it.id, newClientId()) }

        notes.deleteSynced()
        categories.deleteSynced()
    }

    private suspend fun switchTo(userId: Long, previous: Long, wipe: suspend () -> Unit) {
        database.withTransaction { wipe() }
        state.clearCursors(previous)
        state.clearCursors(userId)
        state.setDataOwner(userId)
    }
}
