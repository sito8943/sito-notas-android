package dev.sito8943.sitonotas.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import dev.sito8943.sitonotas.core.data.entity.PendingStats
import kotlinx.coroutines.flow.Flow

/**
 * Lo que la sincronizacion necesita saber de las dos tablas a la vez. Las
 * condiciones de "pendiente" son las de `NoteEntity.pending` y las consultas
 * `getPending*` de cada DAO: si cambian alli, cambian aqui.
 */
@Dao
interface SyncDao {

    /**
     * Cuantas filas faltan por subir y la version mas alta entre ellas. La
     * version cambia con cada edicion aunque el conteo no lo haga (seguir
     * escribiendo en una nota ya pendiente), y eso es lo que reprograma la
     * subida automatica.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM notes WHERE $NOTE_PENDING)
                + (SELECT COUNT(*) FROM categories WHERE $CATEGORY_PENDING) AS count,
            MAX(
                COALESCE((SELECT MAX(local_version) FROM notes WHERE $NOTE_PENDING), 0),
                COALESCE((SELECT MAX(local_version) FROM categories WHERE $CATEGORY_PENDING), 0)
            ) AS version
        """,
    )
    fun observePendingStats(): Flow<PendingStats>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM notes WHERE $NOTE_PENDING)
            + (SELECT COUNT(*) FROM categories WHERE $CATEGORY_PENDING)
        """,
    )
    suspend fun pendingCount(): Int

    companion object {
        const val NOTE_PENDING =
            "(remote_id IS NULL OR dirty = 1 OR purged = 1 OR (deleted_at IS NOT NULL) <> remote_deleted)"
        const val CATEGORY_PENDING = "(remote_id IS NULL OR dirty = 1 OR purged = 1)"
    }
}
