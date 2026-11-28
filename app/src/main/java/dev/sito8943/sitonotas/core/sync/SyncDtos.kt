package dev.sito8943.sitonotas.core.sync

import kotlinx.serialization.Serializable

/**
 * Contrato de `servers/notes`. Copiado de los DTO y requests del backend
 * (`NoteDTO`, `CategoryDTO`, `PageResponse`, `CreateNoteRequest`,
 * `UpdateNoteRequest`, `CreateCategoryRequest`, `UpdateCategoryRequest`).
 * No inventar campos. `userId` no se manda: el backend lo toma del token.
 */

@Serializable
data class PageResponse<T>(
    val items: List<T> = emptyList(),
    val currentPage: Int = 0,
    val pageSize: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

/** `NoteDTO`. Las fechas son `LocalDateTime` sin zona (se leen como UTC). */
@Serializable
data class RemoteNote(
    val id: Long,
    val title: String? = null,
    val content: String? = null,
    val favorite: Boolean = false,
    val position: Int? = null,
    /** Solo las categorias activas: el backend oculta las borradas. */
    val categories: List<RemoteCategoryRef> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
)

/** `CommonCategoryDTO` dentro de una nota. */
@Serializable
data class RemoteCategoryRef(
    val id: Long,
    val name: String? = null,
    val color: String? = null,
)

/** `CategoryDTO`. */
@Serializable
data class RemoteCategory(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
)

/**
 * `CreateNoteRequest`: no lleva `favorite` (va despues por PATCH).
 * `clientId` hace el alta idempotente en el servidor nuevo; el anterior lo
 * ignora (Spring Boot no falla con campos desconocidos).
 */
@Serializable
data class CreateNoteBody(
    val title: String,
    val content: String,
    val categoryIds: List<Long>,
    val clientId: String? = null,
)

/**
 * `UpdateNoteRequest`. Todos los campos viajan: el PATCH es parcial y un
 * `categoryIds` vacio borra las categorias, que es lo que se quiere.
 */
@Serializable
data class UpdateNoteBody(
    val id: Long,
    val title: String,
    val content: String,
    val favorite: Boolean,
    val categoryIds: List<Long>,
)

@Serializable
data class CreateCategoryBody(
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val clientId: String? = null,
)

/** `UpdateCategoryRequest`: el backend reemplaza la fila entera, asi que va todo. */
@Serializable
data class UpdateCategoryBody(
    val id: Long,
    val name: String,
    val description: String? = null,
    val color: String? = null,
)

/**
 * `DeletedIdsResponse`: ids borrados para siempre desde `since` y `until`, la
 * hora del servidor que hay que mandar como siguiente `since`.
 */
@Serializable
data class DeletedIds(
    val ids: List<Long> = emptyList(),
    val until: String? = null,
)
