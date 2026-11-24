package dev.sito8943.sitonotas.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

/** Una nota puede tener varias categorias (`categoryIds` en el backend). */
@Entity(
    tableName = "note_categories",
    primaryKeys = ["note_id", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class NoteCategoryCrossRef(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
)

data class NoteWithCategories(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteCategoryCrossRef::class,
            parentColumn = "note_id",
            entityColumn = "category_id",
        ),
    )
    val categories: List<CategoryEntity>,
)

/** Cuantas notas activas usan cada categoria. */
data class CategoryUse(
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val uses: Int,
)

/** Firma de lo pendiente de subir: cambia con cada edicion local. */
data class PendingStats(
    val count: Int,
    val version: Long,
)
