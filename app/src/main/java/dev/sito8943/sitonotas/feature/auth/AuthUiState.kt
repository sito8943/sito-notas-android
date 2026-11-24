package dev.sito8943.sitonotas.feature.auth

/** Motivo por el que fallo un intento de autenticacion, ya interpretado. */
enum class AuthError {
    /** 401 en sign-in: credenciales incorrectas. */
    INVALID_CREDENTIALS,

    /** 404 en sign-in: no existe la cuenta. */
    ACCOUNT_NOT_FOUND,

    /** 400: el backend rechazo el payload. */
    INVALID_INPUT,

    /** Sin red, host inalcanzable o timeout. */
    NETWORK,

    UNKNOWN,
}

/** Errores de validacion local, antes de tocar la red. */
data class AuthFieldErrors(
    val email: Boolean = false,
    val password: Boolean = false,
) {
    val any: Boolean get() = email || password
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: AuthError? = null,
    val fieldErrors: AuthFieldErrors = AuthFieldErrors(),
    /** Se envio el correo de recuperacion de contraseña. */
    val resetSent: Boolean = false,
) {
    val canSubmitSignIn: Boolean
        get() = !loading && email.isNotBlank() && password.isNotBlank()
}
