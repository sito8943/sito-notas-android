package dev.sito8943.sitonotas.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.sito8943.sitonotas.core.data.entity.CategoryEntity
import dev.sito8943.sitonotas.core.data.entity.CategoryUse
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE purged = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE purged = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getActive(): List<CategoryEntity>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE remote_id = :remoteId")
    suspend fun getByRemoteId(remoteId: Long): CategoryEntity?

    @Query(
        """
        SELECT nc.category_id AS category_id, COUNT(*) AS uses
        FROM note_categories nc JOIN notes n ON n.id = nc.note_id
        WHERE n.deleted_at IS NULL AND n.purged = 0
        GROUP BY nc.category_id
        """,
    )
    fun observeUses(): Flow<List<CategoryUse>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    // --- Sync -------------------------------------------------------------

    @Query("SELECT * FROM categories WHERE remote_id IS NULL AND purged = 0 ORDER BY id")
    suspend fun getPendingCreates(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE remote_id IS NOT NULL AND dirty = 1 AND purged = 0 ORDER BY id")
    suspend fun getPendingUpdates(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE remote_id IS NOT NULL AND purged = 1 ORDER BY id")
    suspend fun getPendingPurges(): List<CategoryEntity>

    /**
     * Guarda el id del servidor. `dirty` solo se limpia si nadie la edito
     * mientras el alta estaba en vuelo.
     */
    @Query(
        """
        UPDATE categories SET remote_id = :remoteId,
            dirty = CASE WHEN local_version = :version THEN 0 ELSE 1 END
        WHERE id = :id
        """,
    )
    suspend fun markCreated(id: Long, remoteId: Long, version: Long)

    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND local_version = :version")
    suspend fun markUpdated(id: Long, version: Long)

    /** El servidor ya no la tiene: se vuelve a crear en la proxima subida. */
    @Query("UPDATE categories SET remote_id = NULL, dirty = 1 WHERE id = :id")
    suspend fun unlinkRemote(id: Long)
}
