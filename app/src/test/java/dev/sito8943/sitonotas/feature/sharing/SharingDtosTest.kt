package dev.sito8943.sitonotas.feature.sharing

import dev.sito8943.sitonotas.feature.sharing.data.CreateShareBody
import dev.sito8943.sitonotas.feature.sharing.data.NoteLinkDto
import dev.sito8943.sitonotas.feature.sharing.data.NoteShareDto
import dev.sito8943.sitonotas.feature.sharing.data.PublicNoteDto
import dev.sito8943.sitonotas.feature.sharing.data.SharePermission
import dev.sito8943.sitonotas.feature.sharing.data.SharedNoteDto
import dev.sito8943.sitonotas.feature.sharing.data.SharedNoteUpdateBody
import dev.sito8943.sitonotas.feature.sharing.data.UnseenSharesDto
import dev.sito8943.sitonotas.feature.sharing.data.UpdateShareBody
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payloads as servers/notes sends them (Jackson, `LocalDateTime` without zone). */
class SharingDtosTest {

    // Same configuration as AppContainer.json.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `parses the shares of a note`() {
        val raw = """
            [{"userId":7,"email":"ana@example.com","username":"ana","permission":"EDIT","createdAt":"2026-11-20T10:15:30.123"},
             {"userId":8,"email":"bob@example.com","username":null,"permission":"READ","createdAt":"2026-11-21T08:00:00"}]
        """.trimIndent()

        val shares = json.decodeFromString<List<NoteShareDto>>(raw)

        assertEquals(2, shares.size)
        assertEquals(SharePermission.EDIT, shares[0].permission)
        assertEquals("ana", shares[0].displayName)
        assertEquals(SharePermission.READ, shares[1].permission)
        assertNull(shares[1].username)
        assertEquals("bob@example.com", shares[1].displayName)
    }

    @Test
    fun `blank username falls back to the email`() {
        val share = json.decodeFromString<NoteShareDto>("""{"userId":1,"email":"x@y.z","username":" ","permission":"READ"}""")
        assertEquals("x@y.z", share.displayName)
    }

    @Test
    fun `parses the public link with and without token`() {
        assertEquals("abc_-12", json.decodeFromString<NoteLinkDto>("""{"token":"abc_-12"}""").token)
        assertNull(json.decodeFromString<NoteLinkDto>("""{"token":null}""").token)
    }

    @Test
    fun `parses a public note with and without note id`() {
        val anonymous = json.decodeFromString<PublicNoteDto>(
            """{"title":"Lista","content":"- leche","updatedAt":"2026-11-27T19:00:00","ownerName":"ana"}""",
        )
        assertNull(anonymous.noteId)
        assertEquals("ana", anonymous.ownerName)

        val withAccess = json.decodeFromString<PublicNoteDto>("""{"noteId":42,"title":"t","content":"c","ownerName":"ana"}""")
        assertEquals(42L, withAccess.noteId)
    }

    @Test
    fun `parses notes shared with me`() {
        val raw = """
            [{"id":5,"categories":[],"content":"hola","title":"T","favorite":false,"position":null,
              "createdAt":"2026-11-01T00:00:00","updatedAt":"2026-11-02T00:00:00","deletedAt":null,
              "owner":{"id":3,"email":"ana@example.com","username":"ana"},"permission":"EDIT"},
             {"id":6,"content":"x","title":"U","owner":{"id":4,"email":"bob@example.com"},"permission":"READ"}]
        """.trimIndent()

        val notes = json.decodeFromString<List<SharedNoteDto>>(raw)

        assertTrue(notes[0].canEdit)
        assertEquals("ana", notes[0].owner?.displayName)
        assertFalse(notes[1].canEdit)
        assertEquals("bob@example.com", notes[1].owner?.displayName)
    }

    @Test
    fun `own note has no owner nor permission`() {
        val own = json.decodeFromString<SharedNoteDto>(
            """{"id":9,"categories":[{"id":1,"name":"a"}],"title":"t","content":"c","favorite":true}""",
        )
        assertNull(own.owner)
        assertFalse(own.canEdit)
    }

    @Test
    fun `parses the unseen count`() {
        assertEquals(3L, json.decodeFromString<UnseenSharesDto>("""{"count":3}""").count)
    }

    @Test
    fun `encodes the request bodies as the server expects`() {
        assertEquals(
            """{"email":"ana@example.com","permission":"READ"}""",
            json.encodeToString(CreateShareBody("ana@example.com", SharePermission.READ)),
        )
        assertEquals("""{"permission":"EDIT"}""", json.encodeToString(UpdateShareBody(SharePermission.EDIT)))
        assertEquals(
            """{"id":5,"title":"T","content":"c"}""",
            json.encodeToString(SharedNoteUpdateBody(5, "T", "c")),
        )
    }
}
