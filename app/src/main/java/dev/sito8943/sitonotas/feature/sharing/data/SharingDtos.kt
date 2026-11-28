package dev.sito8943.sitonotas.feature.sharing.data

import kotlinx.serialization.Serializable

/** `SharePermission` of servers/notes. */
@Serializable
enum class SharePermission { READ, EDIT }

/** `NoteShareDTO`: one user a note is shared with, as its owner sees it. */
@Serializable
data class NoteShareDto(
    val userId: Long,
    val email: String,
    val username: String? = null,
    val permission: SharePermission = SharePermission.READ,
    val createdAt: String? = null,
) {
    /** Username, else the email (web `getShareName`). */
    val displayName: String get() = username?.takeIf { it.isNotBlank() } ?: email
}

/** `CreateNoteShareRequest`. */
@Serializable
data class CreateShareBody(val email: String, val permission: SharePermission)

/** `UpdateNoteShareRequest`. */
@Serializable
data class UpdateShareBody(val permission: SharePermission)

/** `NoteLinkDTO`: token of the public link, null when there is none. */
@Serializable
data class NoteLinkDto(val token: String? = null)

/** `PublicNoteDTO`. [noteId] only reaches the owner or users the note is shared with. */
@Serializable
data class PublicNoteDto(
    val noteId: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val updatedAt: String? = null,
    val ownerName: String? = null,
)

/** `NoteOwnerDTO`. */
@Serializable
data class NoteOwnerDto(
    val id: Long,
    val email: String? = null,
    val username: String? = null,
) {
    /** Username, else the email (web `getOwnerName`). */
    val displayName: String get() = username?.takeIf { it.isNotBlank() } ?: email.orEmpty()
}

/**
 * `NoteDTO` of a note shared with the caller (`GET /notes/shared`,
 * `GET /notes/{id}`): no tags, favorite or position, but [owner] and
 * [permission]. A note of the caller's own has no [owner].
 */
@Serializable
data class SharedNoteDto(
    val id: Long,
    val title: String? = null,
    val content: String? = null,
    val updatedAt: String? = null,
    val owner: NoteOwnerDto? = null,
    val permission: SharePermission? = null,
) {
    val canEdit: Boolean get() = permission == SharePermission.EDIT
}

/** `PATCH /notes/{id}` as the web sends it for title and content (categories untouched). */
@Serializable
data class SharedNoteUpdateBody(val id: Long, val title: String, val content: String)

/** `UnseenSharesDTO`. */
@Serializable
data class UnseenSharesDto(val count: Long = 0)
