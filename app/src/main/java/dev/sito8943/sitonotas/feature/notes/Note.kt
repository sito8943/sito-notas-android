package dev.sito8943.sitonotas.feature.notes

import dev.sito8943.sitonotas.core.data.entity.NoteWithCategories
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.feature.categories.toDomain

/** Nota tal y como la ve la UI. */
data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val favorite: Boolean,
    val updatedAt: Long,
    val deletedAt: Long?,
    /** Ordenadas por nombre, como en la web. */
    val categories: List<Category>,
    /** Aun no subida (o con cambios sin subir). */
    val pendingSync: Boolean,
)

fun NoteWithCategories.toDomain() = Note(
    id = note.id,
    title = note.title,
    content = note.content,
    favorite = note.favorite,
    updatedAt = note.updatedAt,
    deletedAt = note.deletedAt,
    categories = categories.filter { !it.purged }.sortedBy { it.name.lowercase() }.map { it.toDomain() },
    pendingSync = note.pending,
)
