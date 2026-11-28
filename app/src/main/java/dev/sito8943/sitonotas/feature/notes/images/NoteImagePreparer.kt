package dev.sito8943.sitonotas.feature.notes.images

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import dev.sito8943.sitonotas.feature.notes.images.NoteImageRules.Decision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Imagen lista para el multipart. */
class PreparedImage(val bytes: ByteArray, val mimeType: String) {
    val fileName: String get() = "image." + NoteImageRules.extensionOf(mimeType)
}

class ImagePrepareException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { INVALID_TYPE, TOO_LARGE, UNREADABLE }
}

/**
 * Lee la imagen elegida (galeria o portapapeles) y la deja como la acepta el
 * backend (ver [NoteImageRules]): tal cual si ya vale, o recomprimida a JPEG
 * respetando la orientacion EXIF.
 */
class NoteImagePreparer(private val resolver: ContentResolver) {

    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val mimeType = resolver.getType(uri)
        val declaredSize = sizeOf(uri)
        val early = NoteImageRules.decide(mimeType, declaredSize ?: 0)
        if (early == Decision.INVALID_TYPE || early == Decision.TOO_LARGE) throw ImagePrepareException(early.toReason())

        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (error: IOException) {
            throw ImagePrepareException(ImagePrepareException.Reason.UNREADABLE, error)
        } catch (error: SecurityException) {
            throw ImagePrepareException(ImagePrepareException.Reason.UNREADABLE, error)
        } ?: throw ImagePrepareException(ImagePrepareException.Reason.UNREADABLE)

        val type = mimeType!!.lowercase().substringBefore(';').trim()
        when (val decision = NoteImageRules.decide(type, bytes.size.toLong())) {
            Decision.UPLOAD_AS_IS -> PreparedImage(bytes, type)
            Decision.REENCODE_JPEG -> withContext(Dispatchers.Default) { reencode(uri, bytes) }
            else -> throw ImagePrepareException(decision.toReason())
        }
    }

    private fun reencode(uri: Uri, bytes: ByteArray): PreparedImage {
        val decoded = try {
            decode(uri, bytes)
        } catch (error: Exception) {
            throw ImagePrepareException(ImagePrepareException.Reason.UNREADABLE, error)
        } ?: throw ImagePrepareException(ImagePrepareException.Reason.UNREADABLE)

        // JPEG no tiene transparencia: fondo blanco en vez de negro.
        val opaque = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        if (opaque !== decoded) decoded.recycle()

        var quality = START_QUALITY
        while (true) {
            val out = ByteArrayOutputStream()
            opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= NoteImageRules.MAX_BYTES) {
                opaque.recycle()
                return PreparedImage(out.toByteArray(), "image/jpeg")
            }
            if (quality <= MIN_QUALITY) break
            quality -= QUALITY_STEP
        }
        opaque.recycle()
        throw ImagePrepareException(ImagePrepareException.Reason.TOO_LARGE)
    }

    /** Decodificada, girada segun EXIF y con el lado mayor acotado. */
    private fun decode(uri: Uri, bytes: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder ya aplica la orientacion EXIF (y lee HEIC).
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = NoteImageRules.scaleFor(info.size.width, info.size.height)
                if (scale < 1.0) {
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= NoteImageRules.MAX_REENCODE_SIDE_PX) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val matrix = Matrix()
        val scale = NoteImageRules.scaleFor(raw.width, raw.height).toFloat()
        if (scale < 1f) matrix.postScale(scale, scale)
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun Decision.toReason() = when (this) {
        Decision.TOO_LARGE -> ImagePrepareException.Reason.TOO_LARGE
        else -> ImagePrepareException.Reason.INVALID_TYPE
    }

    private companion object {
        const val START_QUALITY = 85
        const val MIN_QUALITY = 55
        const val QUALITY_STEP = 10
    }
}
