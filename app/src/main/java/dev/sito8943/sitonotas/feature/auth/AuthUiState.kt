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

    /** 409 en sign-up: ya hay una cuenta con ese correo. */
    EMAIL_TAKEN,

    /** 400 `auth.register.featureDisabled`: el servidor no admite registros. */
    SIGN_UP_DISABLED,

    /** 429: limite de intentos de sito-auth. */
    TOO_MANY_ATTEMPTS,

    UNKNOWN,
}

/** Errores de validacion local, antes de tocar la red. */
data class AuthFieldErrors(
    val email: Boolean = false,
    val password: Boolean = false,
    /** Registro: menos de [MIN_PASSWORD_LENGTH] caracteres. */
    val passwordTooShort: Boolean = false,
    /** Registro: la repeticion no coincide. */
    val confirmPassword: Boolean = false,
) {
    val any: Boolean get() = email || password || passwordTooShort || confirmPassword
}

/** `MIN_PASSWORD_LENGTH` de la web y de sito-auth. */
const val MIN_PASSWORD_LENGTH = 6

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    val loading: Boolean = false,
    val error: AuthError? = null,
    val fieldErrors: AuthFieldErrors = AuthFieldErrors(),
    /** Se envio el correo de recuperacion de contraseña. */
    val resetSent: Boolean = false,
) {
    val canSubmitSignIn: Boolean
        get() = !loading && email.isNotBlank() && password.isNotBlank() &&
            (mode == AuthMode.SIGN_IN || confirmPassword.isNotBlank())
}
