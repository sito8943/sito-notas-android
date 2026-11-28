package dev.sito8943.sitonotas.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cargador de imagenes de las notas: OkHttp con cache en disco + LRU en
 * memoria. Sin Coil: son imagenes publicas (`GET /files/note-images/{file}`)
 * que el backend sirve con `Cache-Control: public, max-age=31536000,
 * immutable`, asi que la cache HTTP de OkHttp ya hace de cache de disco sin
 * codigo propio; una dependencia mas no aportaria nada que haga falta aqui.
 *
 * Sin autenticacion: las imagenes son publicas y una URL de otra web pegada
 * en el Markdown no debe recibir el token. Solo `http(s)`.
 *
 * Los GIF se ven como imagen fija (su primer fotograma).
 */
class ImageLoader(cacheDir: File) {

    private val client = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, DISK_CACHE_DIR), DISK_CACHE_BYTES))
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val memory = object : LruCache<String, Bitmap>(memoryCacheKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    /** La imagen ya decodificada, si esta en memoria (sin parpadeo al volver). */
    fun cached(url: String, maxWidthPx: Int): Bitmap? = memory.get(key(url, maxWidthPx))

    /**
     * Descarga (o lee de la cache) y decodifica a no mas de [maxWidthPx] de
     * ancho. @throws ImageLoadException si no se puede.
     */
    suspend fun load(url: String, maxWidthPx: Int): Bitmap {
        val key = key(url, maxWidthPx)
        memory.get(key)?.let { return it }
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            throw ImageLoadException("Esquema de imagen no soportado")
        }
        val bytes = withContext(Dispatchers.IO) { download(url) }
        val bitmap = withContext(Dispatchers.Default) { decode(bytes, maxWidthPx) }
            ?: throw ImageLoadException("No se pudo decodificar la imagen")
        memory.put(key, bitmap)
        return bitmap
    }

    private fun download(url: String): ByteArray {
        val request = try {
            Request.Builder().url(url).get().build()
        } catch (error: IllegalArgumentException) {
            throw ImageLoadException("URL de imagen no valida", error)
        }
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ImageLoadException("HTTP ${response.code}")
                return response.body.bytes()
            }
        } catch (error: IOException) {
            throw ImageLoadException("Fallo de red al cargar la imagen", error)
        }
    }

    private fun decode(bytes: ByteArray, maxWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidthPx.coerceAtLeast(1)) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Anchos agrupados: la misma imagen en pantallas casi iguales comparte entrada. */
    private fun key(url: String, maxWidthPx: Int): String {
        val bucket = ((maxWidthPx.coerceAtLeast(1) + WIDTH_BUCKET - 1) / WIDTH_BUCKET) * WIDTH_BUCKET
        return "$bucket|$url"
    }

    private companion object {
        const val DISK_CACHE_DIR = "note-images"
        const val DISK_CACHE_BYTES = 50L * 1024 * 1024
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val CALL_TIMEOUT_SECONDS = 30L
        const val WIDTH_BUCKET = 256

        /** Un octavo de la memoria de la app. */
        fun memoryCacheKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}

class ImageLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
