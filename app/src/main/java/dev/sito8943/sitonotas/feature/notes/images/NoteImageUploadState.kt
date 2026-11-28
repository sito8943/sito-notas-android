package dev.sito8943.sitonotas.feature.notes.images

import android.net.Uri

/** Lo que el editor necesita para subir imagenes. Nulo = sin imagenes (tests). */
class NoteImageSupport(
    val uploader: NoteImageUploader,
    val prepare: suspend (Uri) -> PreparedImage,
    /** Hay cuenta: sin ella no hay servidor al que subir. */
    val isSignedIn: () -> Boolean,
    /** Pide una sincronizacion ya (la nota nueva necesita su id remoto). */
    val requestSync: () -> Unit,
)

/** Subida en curso: imagen [index] de [count], [progress] 0..1 (nulo = preparando). */
data class ImageUploadProgress(val index: Int, val count: Int, val progress: Float?)

/** Imagen subida, pendiente de que el editor la meta en el texto. */
data class UploadedImage(val key: Long, val url: String)

/** Por que no se pudo subir. Siempre visible: nunca se finge una subida. */
enum class ImageUploadError {
    /** Invitado: no hay cuenta en la que guardar la imagen. */
    NEEDS_ACCOUNT,

    /** La nota aun no esta en el servidor (sin red o sincronizacion fallida). */
    NOT_SYNCED,

    /** Sin conexion o el servidor no responde. */
    OFFLINE,

    /** Tipo no admitido (jpeg, png, webp o gif). */
    INVALID_TYPE,

    /** Mas de 5 MB y no se pudo reducir. */
    TOO_LARGE,

    /** No se pudo leer la imagen elegida. */
    UNREADABLE,

    /** El portapapeles no tiene una imagen. */
    NO_CLIPBOARD_IMAGE,

    /** 404: la nota ya no existe en el servidor o no se puede editar. */
    NOT_FOUND,

    /** Otro error del servidor. */
    SERVER,
}
