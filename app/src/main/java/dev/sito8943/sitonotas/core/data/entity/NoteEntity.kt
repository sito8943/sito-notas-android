package dev.sito8943.sitonotas.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Nota. Room es la fuente de verdad de la UI; el servidor manda en lo ya
 * sincronizado.
 *
 * Estado de sincronizacion (ver `core/sync`):
 * - `remoteId == null` -> alta pendiente.
 * - `dirty` -> titulo, contenido, favorita o categorias pendientes de PATCH.
 * - `deletedAt != null` vs `remoteDeleted` -> si difieren, falta mandar a la
 *   papelera (`DELETE /notes`) o restaurar (`PATCH /notes/restore`).
 * - `purged` -> borrada para siempre en local, pendiente de
 *   `DELETE /notes/permanent`. Oculta en la UI hasta que el servidor confirme.
 *
 * Tiempos en epoch ms (UTC). Los del servidor llegan como `LocalDateTime` sin
 * zona y se leen como UTC, igual que la web (`parseApiDate`).
 */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["remote_id"], unique = true)],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: Long? = null,
    val title: String = "",
    /** Markdown. */
    val content: String = "",
    val favorite: Boolean = false,
    /** Orden manual; nulo = sin orden manual (va detras, por fecha). */
    val position: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    /** Lo que el servidor cree del estado de papelera de esta nota. */
    @ColumnInfo(name = "remote_deleted") val remoteDeleted: Boolean = false,
    val dirty: Boolean = true,
    val purged: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
) {
    /**
     * Tiene algo que el servidor aun no sabe. Al descargar, una fila asi no se
     * pisa: gana lo local, que se subira.
     */
    val pending: Boolean
        get() = remoteId == null || dirty || purged || (deletedAt != null) != remoteDeleted
}
