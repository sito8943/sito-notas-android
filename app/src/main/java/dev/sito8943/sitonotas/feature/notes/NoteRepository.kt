package dev.sito8943.sitonotas.feature.notes

import androidx.room.withTransaction
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Notas. Room es la fuente de verdad: cada escritura sube `local_version` y
 * deja la fila pendiente; `core/sync` la sube sola.
 */
class NoteRepository(
    private val database: NotesDatabase,
    private val clock: VersionClock,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.notes()

    fun observeNotes(): Flow<List<Note>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    fun observeTrash(): Flow<List<Note>> = dao.observeTrash().map { rows -> rows.map { it.toDomain() } }

    fun observeNote(id: Long): Flow<Note?> = dao.observeById(id).map { it?.toDomain() }

    /** Server id of a local note; null while it was never uploaded (sharing needs it). */
    suspend fun remoteIdOf(id: Long): Long? = dao.getById(id)?.takeIf { !it.purged }?.remoteId

    /** Local id of an active note the server knows as [remoteId], if it is on this phone. */
    suspend fun localIdOf(remoteId: Long): Long? =
        dao.getByRemoteId(remoteId)?.takeIf { !it.purged && it.deletedAt == null }?.id

    /**
     * Nueva nota arriba del todo si ya hay orden manual (`min - 1`), igual que
     * hace el backend al crearla.
     */
    suspend fun create(
        title: String,
        content: String,
        favorite: Boolean = false,
        categoryIds: Collection<Long> = emptyList(),
    ): Long = database.withTransaction {
        val timestamp = now()
        val id = dao.insert(
            NoteEntity(
                title = title,
                content = content,
                favorite = favorite,
                position = dao.minActivePosition()?.let { it - 1 },
                createdAt = timestamp,
                updatedAt = timestamp,
                dirty = true,
                localVersion = clock.next(),
            ),
        )
        if (categoryIds.isNotEmpty()) dao.replaceCategories(id, categoryIds)
        id
    }

    suspend fun updateText(id: Long, title: String, content: String) = edit(id) { note ->
        if (note.title == title && note.content == content) null else note.copy(title = title, content = content)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = edit(id) { note ->
        if (note.favorite == favorite) null else note.copy(favorite = favorite)
    }

    suspend fun setCategories(id: Long, categoryIds: Collection<Long>) {
        database.withTransaction {
            val note = dao.getById(id) ?: return@withTransaction
            if (dao.categoryIdsOf(id).toSet() == categoryIds.toSet()) return@withTransaction
            dao.replaceCategories(id, categoryIds)
            dao.update(note.copy(updatedAt = now(), dirty = true, localVersion = clock.next()))
        }
    }

    /** A la papelera. El estado de papelera se sube aparte (no marca `dirty`). */
    suspend fun trash(id: Long) = setTrashed(id, trashed = true)

    suspend fun restore(id: Long) = setTrashed(id, trashed = false)

    /**
     * Borrado definitivo. Si nunca se subio, se borra ya; si no, queda oculta
     * hasta que el servidor confirme `DELETE /notes/permanent`.
     */
    suspend fun deletePermanently(id: Long) {
        database.withTransaction {
            val note = dao.getById(id) ?: return@withTransaction
            purge(note)
        }
    }

    suspend fun emptyTrash() {
        database.withTransaction {
            dao.getAll().filter { it.deletedAt != null && !it.purged }.forEach { purge(it) }
        }
    }

    private suspend fun purge(note: NoteEntity) {
        if (note.remoteId == null) {
            dao.deleteByIds(listOf(note.id))
        } else {
            dao.update(
                note.copy(
                    purged = true,
                    deletedAt = note.deletedAt ?: now(),
                    localVersion = clock.next(),
                ),
            )
        }
    }

    private suspend fun setTrashed(id: Long, trashed: Boolean) {
        database.withTransaction {
            val note = dao.getById(id) ?: return@withTransaction
            if ((note.deletedAt != null) == trashed) return@withTransaction
            val timestamp = now()
            dao.update(
                note.copy(
                    deletedAt = if (trashed) timestamp else null,
                    updatedAt = timestamp,
                    localVersion = clock.next(),
                ),
            )
        }
    }

    private suspend fun edit(id: Long, change: (NoteEntity) -> NoteEntity?) {
        database.withTransaction {
            val note = dao.getById(id) ?: return@withTransaction
            val changed = change(note) ?: return@withTransaction
            dao.update(changed.copy(updatedAt = now(), dirty = true, localVersion = clock.next()))
        }
    }
}
