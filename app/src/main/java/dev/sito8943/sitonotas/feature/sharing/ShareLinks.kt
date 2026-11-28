package dev.sito8943.sitonotas.feature.sharing

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Links the web produces (`shared/routes.ts`):
 * - public link: `<shareUrl>/<token>` (production share.notes.sito8943.com),
 *   or the API's own share page `<apiUrl>/s/<token>` without a share host;
 * - no public link: the note's app URL `<webUrl>/note/<id>`, which opens for
 *   the people with access.
 */
object ShareLinks {

    fun publicUrl(token: String, shareBaseUrl: String, apiBaseUrl: String): String {
        val encoded = encode(token)
        val share = shareBaseUrl.trimEnd('/')
        return if (share.isNotEmpty()) "$share/$encoded" else "${apiBaseUrl.trimEnd('/')}/s/$encoded"
    }

    fun noteAppUrl(remoteId: Long, webBaseUrl: String): String = "${webBaseUrl.trimEnd('/')}/note/$remoteId"

    /**
     * Token of an opened share link: `https://<share host>/<token>`, or any
     * `.../s/<token>` (API share page, web PublicNote route). Null otherwise.
     */
    fun tokenOf(url: String, shareBaseUrl: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        val shareHost = runCatching { URI(shareBaseUrl).host }.getOrNull()
        val raw = when {
            shareHost != null && uri.host.equals(shareHost, ignoreCase = true) && segments.size == 1 -> segments[0]
            segments.size >= 2 && segments[segments.size - 2] == "s" -> segments.last()
            else -> null
        } ?: return null
        return URLDecoder.decode(raw, StandardCharsets.UTF_8.name()).takeIf { it.isNotBlank() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
