package dev.sito8943.sitonotas.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cliente JSON minimo (copiado de SitoWallet). Hay dos instancias: una contra
 * sito-auth y otra contra la API de notas, que comparten la misma sesion.
 *
 * Ante un 401 en una llamada autenticada refresca el token en silencio y
 * reintenta una vez. Si el refresco no llega a hablar con el servidor la sesion
 * NO se cierra: sale un `NetworkException` y el token sigue guardado.
 *
 * No se usa Retrofit: son cuatro verbos sobre un puñado de endpoints, y
 * Retrofit añadiria reflexion y peso sin resolver nada que no resuelva esto.
 */
class RestClient(
    private val baseUrl: String,
    // @PublishedApi porque las funciones inline de abajo lo usan para
    // serializar, y una inline no puede acceder a un miembro privado.
    @PublishedApi internal val json: Json,
    /**
     * Diferido a proposito: `SessionRepository` necesita este mismo cliente para
     * poder llamar a `/auth/refresh`, asi que no puede existir antes que el.
     */
    private val tokens: () -> AuthTokenSupplier,
) {

    private val client = OkHttpClient.Builder()
        // Aborta una peticion colgada para que la UI no se quede en "cargando"
        // para siempre (host equivocado, firewall, servidor inalcanzable).
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend inline fun <reified T> get(path: String, auth: Boolean = false): T =
        request(path, HttpMethod.GET, null, auth)

    suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        auth: Boolean = false,
    ): T = request(path, HttpMethod.POST, encode(body), auth)

    suspend inline fun <reified T, reified B> patch(
        path: String,
        body: B,
        auth: Boolean = true,
    ): T = request(path, HttpMethod.PATCH, encode(body), auth)

    /**
     * `PATCH` sin cuerpo y sin leer la respuesta: las acciones de estado de una
     * nota (`/notes/restore`...) no siempre llevan nada y lo que devuelven no se
     * usa.
     */
    suspend fun patchForUnit(path: String, auth: Boolean = true) {
        request<Unit>(path, HttpMethod.PATCH, null, auth)
    }

    /** `DELETE` con cuerpo: el backend borra por lotes con `[id, id...]`. */
    suspend inline fun <reified T, reified B> delete(
        path: String,
        body: B,
        auth: Boolean = true,
    ): T = request(path, HttpMethod.DELETE, encode(body), auth)

    /**
     * El cuerpo tal cual, sin deserializar.
     *
     * Hace falta para leer una respuesta cuya **forma** hay que inspeccionar
     * antes de interpretarla: los `POST /<recurso>/batch` devuelven `[41,42]` en
     * el backend nuevo y `2` en el anterior, y distinguirlos es la diferencia
     * entre guardar los ids remotos y subir filas que ya nadie podra actualizar.
     *
     * Pedirlo como `String` por `post` no sirve: eso lo deserializa como una
     * cadena JSON y un array revienta con "Unexpected JSON token at offset 0".
     */
    suspend inline fun <reified B> postForRawBody(
        path: String,
        body: B,
        auth: Boolean = false,
    ): String = execute(path, HttpMethod.POST, encode(body), auth, allowRefresh = true)

    /** Como [postForRawBody], para los `PATCH /<recurso>/batch`. */
    suspend inline fun <reified B> patchForRawBody(
        path: String,
        body: B,
        auth: Boolean = true,
    ): String = execute(path, HttpMethod.PATCH, encode(body), auth, allowRefresh = true)

    /** Para endpoints `204 No Content` como `/auth/sign-out`. */
    suspend inline fun <reified B> postForUnit(
        path: String,
        body: B?,
        auth: Boolean = false,
    ) {
        request<Unit>(path, HttpMethod.POST, body?.let { encode(it) }, auth)
    }

    @PublishedApi
    internal inline fun <reified B> encode(body: B): String =
        json.encodeToString(body)

    @PublishedApi
    internal suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod,
        body: String?,
        auth: Boolean,
    ): T {
        val raw = execute(path, method, body, auth, allowRefresh = true)
        return when {
            // 204 y endpoints sin cuerpo.
            T::class == Unit::class -> Unit as T
            raw.isBlank() -> throw ApiException(
                status = 200,
                message = "El servidor respondio $path sin cuerpo",
            )
            else -> json.decodeFromString(raw)
        }
    }

    /**
     * @return el cuerpo como texto, vacio cuando la respuesta es 204.
     */
    @PublishedApi
    internal suspend fun execute(
        path: String,
        method: HttpMethod,
        body: String?,
        auth: Boolean,
        allowRefresh: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/json")

        if (auth) {
            tokens().accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        }

        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
        when (method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> builder.post(requestBody ?: EMPTY_BODY)
            HttpMethod.PATCH -> builder.patch(requestBody ?: EMPTY_BODY)
            HttpMethod.DELETE ->
                if (requestBody == null) builder.delete() else builder.delete(requestBody)
        }

        val response = try {
            client.newCall(builder.build()).await()
        } catch (error: IOException) {
            // Fallo de red o timeout: se hace visible en vez de quedarse colgado.
            // No se incluye la URL en el mensaje: puede llevar token en la query.
            throw NetworkException("La peticion a $path fallo: ${error.javaClass.simpleName}", error)
        }

        response.use {
            val text = it.body?.string().orEmpty()

            if (!it.isSuccessful) {
                // Token de acceso caducado o revocado -> un refresco silencioso y
                // un reintento. Solo en llamadas autenticadas, y nunca cuando ya
                // se esta reintentando (si no, bucle infinito).
                if (it.code == HTTP_UNAUTHORIZED && auth && allowRefresh) {
                    when (tokens().refresh()) {
                        RefreshOutcome.REFRESHED ->
                            return@withContext execute(path, method, body, auth, allowRefresh = false)

                        // El servidor rechazo el refresh token: no hay vuelta atras.
                        RefreshOutcome.REJECTED -> tokens().onSessionExpired()

                        // No se pudo preguntar. La sesion se queda como esta y
                        // sale un fallo de red, que es lo que de verdad paso:
                        // tratarlo como sesion caducada la echaria de su cuenta
                        // por un tunel o un wifi que se cae.
                        RefreshOutcome.UNREACHABLE -> throw NetworkException(
                            "No se pudo renovar la sesion para $path: sin respuesta del servidor",
                        )
                    }
                }
                throw ApiException(status = it.code, body = text.ifBlank { null })
            }

            if (it.code == HTTP_NO_CONTENT) "" else text
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 15L
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NO_CONTENT = 204
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

@PublishedApi
internal enum class HttpMethod { GET, POST, PATCH, DELETE }

/** Puente entre el callback de OkHttp y las corrutinas, con cancelacion. */
private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        })
    }
