package dev.sito8943.sitonotas.feature.notes.images

import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.RestClient

/** Sube imagenes de notas. Interfaz para probar el ViewModel sin red. */
fun interface NoteImageUploader {
    /**
     * @param remoteId id de la nota **en el servidor**.
     * @return URL absoluta, publica y permanente de la imagen.
     */
    suspend fun upload(remoteId: Long, image: PreparedImage, onProgress: (Float) -> Unit): String
}

/**
 * `POST /notes/{id}/images` (multipart, campo `file`), como
 * `NoteApiClient.uploadImage` de la web. Solo el dueño o quien la tenga
 * compartida con permiso de edicion; si no, 404. Un fichero invalido da 400.
 */
class NoteImagesApi(private val client: RestClient, private val baseUrl: String) : NoteImageUploader {

    override suspend fun upload(remoteId: Long, image: PreparedImage, onProgress: (Float) -> Unit): String {
        var lastPercent = -1
        val body = client.postMultipartForRawBody(
            path = "/notes/$remoteId/images",
            field = FIELD,
            fileName = image.fileName,
            mediaType = image.mimeType,
            bytes = image.bytes,
            onProgress = { sent, total ->
                // Solo cuando cambia el porcentaje: el sink avisa cada pocos KB.
                val percent = if (total > 0) (sent * 100 / total).toInt() else -1
                if (percent >= 0 && percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent / 100f)
                }
            },
        )
        return NoteImageRules.resolveUrl(baseUrl, body)
            ?: throw ApiException(status = 200, message = "Respuesta de subida de imagen no valida")
    }

    private companion object {
        /** `NOTE_IMAGE_FIELD` de la web, `@RequestParam("file")` del backend. */
        const val FIELD = "file"
    }
}
