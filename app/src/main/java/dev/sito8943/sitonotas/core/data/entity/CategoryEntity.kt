package dev.sito8943.sitonotas.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Categoria (en la web, "etiqueta"). El `id` local lo genera Room y nunca
 * coincide con el del backend: `remoteId` es la unica direccion valida para
 * PATCH y DELETE.
 *
 * Estado de sincronizacion:
 * - `remoteId == null` -> alta pendiente (`POST /categories/batch`).
 * - `dirty` -> edicion pendiente (`PATCH /categories/batch`).
 * - `purged` -> borrada en local, pendiente de `DELETE /categories/permanent`.
 *   La web borra las categorias de forma definitiva, asi que aqui tambien.
 *
 * `localVersion` sube con cada edicion local: limpiar `dirty` solo si no ha
 * cambiado mientras la peticion estaba en vuelo evita perder esa edicion.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["remote_id"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "remote_id") val remoteId: Long? = null,
    val name: String,
    val description: String? = null,
    /** `#RRGGBB`, como lo espera el backend. */
    val color: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val dirty: Boolean = true,
    val purged: Boolean = false,
    @ColumnInfo(name = "local_version") val localVersion: Long = 0,
)
