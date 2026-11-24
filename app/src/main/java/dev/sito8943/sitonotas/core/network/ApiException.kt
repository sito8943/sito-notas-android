package dev.sito8943.sitonotas.core.network

/**
 * Portado de `AuthApiError` en `restClient.ts`. Lleva el `status` para que quien
 * llama pueda ramificar: 401 password invalida, 409 email ya registrado, etc.
 */
class ApiException(
    val status: Int,
    /** Cuerpo de la respuesta. El backend a veces responde texto plano, no JSON. */
    val body: String? = null,
    message: String = "La peticion fallo con estado $status",
) : Exception(message)

/** Fallo de transporte: sin red, host inalcanzable o timeout agotado. */
class NetworkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
