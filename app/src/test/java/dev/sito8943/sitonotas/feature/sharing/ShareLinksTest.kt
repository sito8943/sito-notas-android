package dev.sito8943.sitonotas.feature.sharing

import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.NetworkException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinksTest {

    private val share = "https://share.notes.sito8943.com"

    @Test
    fun `public url uses the share host`() {
        assertEquals("$share/abc_-1", ShareLinks.publicUrl("abc_-1", "$share/", "https://api.notes.sito8943.com"))
    }

    @Test
    fun `public url falls back to the API share page`() {
        assertEquals("http://10.0.2.2:8083/s/tok", ShareLinks.publicUrl("tok", "", "http://10.0.2.2:8083/"))
    }

    @Test
    fun `note app url is the web note route`() {
        assertEquals("https://notas.sito8943.com/note/42", ShareLinks.noteAppUrl(42, "https://notas.sito8943.com/"))
    }

    @Test
    fun `reads the token of share links`() {
        assertEquals("abc_-1", ShareLinks.tokenOf("$share/abc_-1", share))
        assertEquals("abc", ShareLinks.tokenOf("https://api.notes.sito8943.com/s/abc", share))
        assertEquals("abc", ShareLinks.tokenOf("https://notas.sito8943.com/s/abc?x=1", share))
    }

    @Test
    fun `ignores links that are not public notes`() {
        assertNull(ShareLinks.tokenOf(share, share))
        assertNull(ShareLinks.tokenOf("$share/", share))
        assertNull(ShareLinks.tokenOf("$share/a/b", share))
        assertNull(ShareLinks.tokenOf("https://example.com/abc", share))
    }

    @Test
    fun `round trips a token`() {
        val token = "Zx9_-AbC"
        assertEquals(token, ShareLinks.tokenOf(ShareLinks.publicUrl(token, share, ""), share))
    }

    @Test
    fun `maps share errors like the web`() {
        assertEquals(SharingError.SELF, SharingError.ofShare(ApiException(400)))
        assertEquals(SharingError.NO_USER, SharingError.ofShare(ApiException(404)))
        assertEquals(SharingError.ALREADY_SHARED, SharingError.ofShare(ApiException(409)))
        assertEquals(SharingError.OFFLINE, SharingError.ofShare(NetworkException("x")))
        assertEquals(SharingError.UNKNOWN, SharingError.ofShare(ApiException(500)))
        assertEquals(SharingError.NOT_FOUND, SharingError.of(ApiException(404)))
    }
}
