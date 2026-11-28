package dev.sito8943.sitonotas.feature.sharing.data

import dev.sito8943.sitonotas.core.network.HttpMethod
import dev.sito8943.sitonotas.core.network.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Sharing endpoints of servers/notes (`NoteShareController`,
 * `NoteLinkController`, `SharedNoteController`, `GET/PATCH /notes/{id}`).
 * Online only: nothing here touches Room. Every `noteId` is a **remote** id.
 */
interface SharingEndpoints {
    suspend fun listShares(noteId: Long): List<NoteShareDto>
    suspend fun share(noteId: Long, email: String, permission: SharePermission): NoteShareDto
    suspend fun setPermission(noteId: Long, userId: Long, permission: SharePermission): NoteShareDto
    suspend fun unshare(noteId: Long, userId: Long)

    suspend fun getLink(noteId: Long): String?
    suspend fun createLink(noteId: Long): String?
    suspend fun removeLink(noteId: Long)
    suspend fun getPublic(token: String): PublicNoteDto

    suspend fun listSharedWithMe(): List<SharedNoteDto>
    suspend fun countUnseen(): Long
    suspend fun markSeen()
    suspend fun getNote(noteId: Long): SharedNoteDto
    suspend fun updateNote(noteId: Long, title: String, content: String)
}

class SharingApi(private val client: RestClient) : SharingEndpoints {

    override suspend fun listShares(noteId: Long): List<NoteShareDto> =
        client.get(shares(noteId), auth = true)

    override suspend fun share(noteId: Long, email: String, permission: SharePermission): NoteShareDto =
        client.post(shares(noteId), CreateShareBody(email, permission), auth = true)

    override suspend fun setPermission(noteId: Long, userId: Long, permission: SharePermission): NoteShareDto =
        client.patch("${shares(noteId)}/$userId", UpdateShareBody(permission))

    override suspend fun unshare(noteId: Long, userId: Long) {
        client.request<Unit>("${shares(noteId)}/$userId", HttpMethod.DELETE, null, auth = true)
    }

    override suspend fun getLink(noteId: Long): String? =
        client.get<NoteLinkDto>(link(noteId), auth = true).token

    override suspend fun createLink(noteId: Long): String? =
        client.request<NoteLinkDto>(link(noteId), HttpMethod.POST, null, auth = true).token

    override suspend fun removeLink(noteId: Long) {
        client.request<Unit>(link(noteId), HttpMethod.DELETE, null, auth = true)
    }

    /** No login needed; with a session the owner or a share recipient also gets the note id. */
    override suspend fun getPublic(token: String): PublicNoteDto =
        client.get("/public/notes/${encode(token)}", auth = true)

    override suspend fun listSharedWithMe(): List<SharedNoteDto> =
        client.get("$NOTES/shared", auth = true)

    override suspend fun countUnseen(): Long =
        client.get<UnseenSharesDto>("$NOTES/shared/unseen", auth = true).count

    override suspend fun markSeen() {
        client.request<Unit>("$NOTES/shared/seen", HttpMethod.POST, null, auth = true)
    }

    override suspend fun getNote(noteId: Long): SharedNoteDto =
        client.get("$NOTES/$noteId", auth = true)

    override suspend fun updateNote(noteId: Long, title: String, content: String) {
        client.patch<Long, SharedNoteUpdateBody>("$NOTES/$noteId", SharedNoteUpdateBody(noteId, title, content))
    }

    private fun shares(noteId: Long) = "$NOTES/$noteId/shares"

    private fun link(noteId: Long) = "$NOTES/$noteId/link"

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        const val NOTES = "/notes"
    }
}
