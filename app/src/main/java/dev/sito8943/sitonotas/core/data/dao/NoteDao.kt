package dev.sito8943.sitonotas.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.sito8943.sitonotas.core.data.entity.NoteCategoryCrossRef
import dev.sito8943.sitonotas.core.data.entity.NoteEntity
import dev.sito8943.sitonotas.core.data.entity.NoteWithCategories
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Orden de la web (`sortByPosition`): manual primero, luego lo mas reciente. */
    @Transaction
    @Query(
        """
        SELECT * FROM notes WHERE deleted_at IS NULL AND purged = 0
        ORDER BY position IS NULL, position, updated_at DESC
        """,
    )
    fun observeActive(): Flow<List<NoteWithCategories>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes WHERE deleted_at IS NOT NULL AND purged = 0
        ORDER BY deleted_at DESC
        """,
    )
    fun observeTrash(): Flow<List<NoteWithCategories>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id AND purged = 0")
    fun observeById(id: Long): Flow<NoteWithCategories?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE remote_id = :remoteId")
    suspend fun getByRemoteId(remoteId: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT MIN(position) FROM notes WHERE deleted_at IS NULL AND purged = 0")
    suspend fun minActivePosition(): Int?

    /** Orden manual: sin `dirty` ni `updated_at`, viaja por `PATCH /notes/reorder`. */
    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    // --- Categorias de la nota -------------------------------------------

    @Query("SELECT category_id FROM note_categories WHERE note_id = :noteId")
    suspend fun categoryIdsOf(noteId: Long): List<Long>

    @Query("DELETE FROM note_categories WHERE note_id = :noteId")
    suspend fun clearCategories(noteId: Long)

    @Query("DELETE FROM note_categories WHERE category_id = :categoryId")
    suspend fun unlinkCategory(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoryRefs(refs: List<NoteCategoryCrossRef>)

    @Transaction
    suspend fun replaceCategories(noteId: Long, categoryIds: Collection<Long>) {
        clearCategories(noteId)
        insertCategoryRefs(categoryIds.distinct().map { NoteCategoryCrossRef(noteId, it) })
    }

    // --- Sync -------------------------------------------------------------

    @Query("SELECT * FROM notes WHERE remote_id IS NULL AND purged = 0 ORDER BY id")
    suspend fun getPendingCreates(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE remote_id IS NOT NULL AND dirty = 1 AND purged = 0 ORDER BY id")
    suspend fun getPendingUpdates(): List<NoteEntity>

    /** Incluye las purgadas: el backend solo borra para siempre lo que ya esta en la papelera. */
    @Query(
        """
        SELECT * FROM notes
        WHERE remote_id IS NOT NULL AND deleted_at IS NOT NULL AND remote_deleted = 0
        ORDER BY id
        """,
    )
    suspend fun getPendingTrash(): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes
        WHERE remote_id IS NOT NULL AND deleted_at IS NULL AND remote_deleted = 1 AND purged = 0
        ORDER BY id
        """,
    )
    suspend fun getPendingRestores(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE remote_id IS NOT NULL AND purged = 1 ORDER BY id")
    suspend fun getPendingPurges(): List<NoteEntity>

    /**
     * Guarda el id del servidor. `stillDirty` cubre lo que el alta no manda
     * (`favorite` no esta en `CreateNoteRequest`): queda para el PATCH.
     */
    @Query(
        """
        UPDATE notes SET remote_id = :remoteId, remote_deleted = 0,
            dirty = CASE WHEN local_version = :version AND :stillDirty = 0 THEN 0 ELSE 1 END
        WHERE id = :id
        """,
    )
    suspend fun markCreated(id: Long, remoteId: Long, version: Long, stillDirty: Boolean)

    @Query("UPDATE notes SET dirty = 0 WHERE id = :id AND local_version = :version")
    suspend fun markUpdated(id: Long, version: Long)

    @Query("UPDATE notes SET remote_deleted = :deleted WHERE id IN (:ids)")
    suspend fun markRemoteDeleted(ids: List<Long>, deleted: Boolean)

    /** El servidor ya no la tiene: se vuelve a crear en la proxima subida. */
    @Query("UPDATE notes SET remote_id = NULL, remote_deleted = 0, dirty = 1 WHERE id = :id")
    suspend fun unlinkRemote(id: Long)

    /**
     * Lo que el servidor ya habra purgado de su papelera (30 dias por defecto,
     * `app.notes.trash.retention-days`). Solo filas sincronizadas y sin nada
     * pendiente: un borrado definitivo en el servidor no se puede descargar.
     */
    @Query(
        """
        DELETE FROM notes
        WHERE remote_id IS NOT NULL AND remote_deleted = 1 AND deleted_at IS NOT NULL
            AND deleted_at < :before AND dirty = 0 AND purged = 0
        """,
    )
    suspend fun deleteExpiredTrash(before: Long): Int
}
