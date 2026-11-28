package dev.sito8943.sitonotas.feature.notes.images

import dev.sito8943.sitonotas.feature.notes.images.NoteImageRules.Decision
import dev.sito8943.sitonotas.feature.notes.ui.appendImageLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Limites del backend (`NoteImageStorage`) y de la web (`useNoteImageUpload`). */
class NoteImageRulesTest {

    private val mb = 1024L * 1024

    @Test
    fun `allowed types up to 5 MB go as they are`() {
        listOf("image/jpeg", "image/png", "image/webp", "image/gif", "IMAGE/PNG").forEach {
            assertEquals(it, Decision.UPLOAD_AS_IS, NoteImageRules.decide(it, 5 * mb))
        }
    }

    @Test
    fun `large photos and other image types are re-encoded, never sent invalid`() {
        assertEquals(Decision.REENCODE_JPEG, NoteImageRules.decide("image/jpeg", 5 * mb + 1))
        assertEquals(Decision.REENCODE_JPEG, NoteImageRules.decide("image/heic", 2 * mb))
        assertEquals(Decision.REENCODE_JPEG, NoteImageRules.decide("image/bmp", 1 * mb))
    }

    @Test
    fun `non images svg huge files and big gifs are rejected`() {
        assertEquals(Decision.INVALID_TYPE, NoteImageRules.decide("application/pdf", 10))
        assertEquals(Decision.INVALID_TYPE, NoteImageRules.decide(null, 10))
        assertEquals(Decision.INVALID_TYPE, NoteImageRules.decide("image/svg+xml", 10))
        assertEquals(Decision.TOO_LARGE, NoteImageRules.decide("image/gif", 6 * mb))
        assertEquals(Decision.TOO_LARGE, NoteImageRules.decide("image/jpeg", 41 * mb))
    }

    @Test
    fun `the returned path resolves against the API like the web`() {
        val path = "files/note-images/12_0f8fad5b-d9cb-469f-a165-70867728950e.png"
        val expected = "https://api.example.com/$path"
        assertEquals(expected, NoteImageRules.resolveUrl("https://api.example.com/", path))
        assertEquals(expected, NoteImageRules.resolveUrl("https://api.example.com", "\"$path\"\n"))
        assertEquals(expected, NoteImageRules.resolveUrl("https://api.example.com", "/$path"))
        assertEquals("https://cdn.x/a.png", NoteImageRules.resolveUrl("https://api.example.com", "https://cdn.x/a.png"))
        assertNull(NoteImageRules.resolveUrl("https://api.example.com", ""))
        assertNull(NoteImageRules.resolveUrl("https://api.example.com", "{\"error\": \"x\"}"))
    }

    @Test
    fun `re-encoding caps the longest side and never upscales`() {
        assertEquals(1.0, NoteImageRules.scaleFor(1200, 800), 0.0)
        assertEquals(0.5, NoteImageRules.scaleFor(5120, 3000), 0.0)
        assertEquals(0.5, NoteImageRules.scaleFor(3000, 5120), 0.0)
    }

    @Test
    fun `markdown mode appends one image per line`() {
        assertEquals("![](u1)", appendImageLines("", listOf("u1")))
        assertEquals("a\n![](u1)\n![](u2)", appendImageLines("a", listOf("u1", "u2")))
        assertEquals("a\n![](u1)", appendImageLines("a\n", listOf("u1")))
    }
}
