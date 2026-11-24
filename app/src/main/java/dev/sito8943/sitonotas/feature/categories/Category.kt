package dev.sito8943.sitonotas.feature.categories

import dev.sito8943.sitonotas.core.data.entity.CategoryEntity

/** Categoria tal y como la ve la UI. */
data class Category(
    val id: Long,
    val name: String,
    val description: String?,
    /** `#RRGGBB` o nulo. */
    val color: String?,
)

fun CategoryEntity.toDomain() = Category(id = id, name = name, description = description, color = color)
