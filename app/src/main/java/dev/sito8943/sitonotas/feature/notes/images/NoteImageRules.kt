package dev.sito8943.sitonotas.feature.notes.images

/**
 * Reglas de subida de imagenes, las del backend (`NoteImageStorage`) y la web
 * (`useNoteImageUpload/constants.ts`): jpeg, png, webp o gif de hasta 5 MB.
 *
 * La web rechaza lo demas. En el movil la foto de la camara puede ser HEIC o
 * pesar mas de 5 MB, asi que esas se recomprimen a JPEG (lado mayor
 * [MAX_REENCODE_SIDE_PX]) en vez de rechazarlas. Un GIF grande no se
 * recomprime: perderia la animacion.
 */
object NoteImageRules {

    val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    const val MAX_BYTES = 5L * 1024 * 1024

    const val MAX_REENCODE_SIDE_PX = 2560

    /** Mas que esto ni se intenta leer en memoria. */
    const val MAX_SOURCE_BYTES = 40L * 1024 * 1024

    enum class Decision { UPLOAD_AS_IS, REENCODE_JPEG, INVALID_TYPE, TOO_LARGE }

    fun decide(mimeType: String?, sizeBytes: Long): Decision {
        val type = mimeType?.lowercase()?.substringBefore(';')?.trim()
        return when {
            type == null || !type.startsWith("image/") -> Decision.INVALID_TYPE
            sizeBytes > MAX_SOURCE_BYTES -> Decision.TOO_LARGE
            type in ALLOWED_TYPES && sizeBytes <= MAX_BYTES -> Decision.UPLOAD_AS_IS
            type == "image/gif" -> Decision.TOO_LARGE
            type == "image/svg+xml" -> Decision.INVALID_TYPE
            else -> Decision.REENCODE_JPEG
        }
    }

    /** Extension del nombre del fichero en el multipart (el backend usa el tipo, no el nombre). */
    fun extensionOf(mimeType: String): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    /** Escala para que el lado mayor no pase de [maxSide] (nunca amplia). */
    fun scaleFor(width: Int, height: Int, maxSide: Int = MAX_REENCODE_SIDE_PX): Double {
        val longest = maxOf(width, height)
        return if (longest <= maxSide || longest <= 0) 1.0 else maxSide.toDouble() / longest
    }

    /**
     * La respuesta de `POST /notes/{id}/images` es la ruta relativa
     * `files/note-images/{file}` en texto (a veces entre comillas JSON). Se
     * resuelve contra la URL de la API como `HttpClient.resolveUrl` de la web.
     */
    fun resolveUrl(baseUrl: String, responseBody: String): String? {
        var path = responseBody.trim()
        if (path.length >= 2 && path.startsWith('"') && path.endsWith('"')) {
            path = path.substring(1, path.length - 1).replace("\\/", "/")
        }
        if (path.isEmpty() || path.any { it.isWhitespace() || it == '(' || it == ')' }) return null
        if (path.startsWith("https://", ignoreCase = true) || path.startsWith("http://", ignoreCase = true)) return path
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
