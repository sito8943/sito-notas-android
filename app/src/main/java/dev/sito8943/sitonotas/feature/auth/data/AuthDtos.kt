package dev.sito8943.sitonotas.feature.auth.data

import kotlinx.serialization.Serializable

/**
 * Contrato de sito-auth (`LoginRequest`, `RefreshRequest`, `SignOutRequest`,
 * `ForgotPasswordRequest`, `AuthDto`). No inventar campos.
 */

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
    val rememberMe: Boolean = true,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class SignOutRequest(
    val refreshToken: String? = null,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
    val redirectTo: String,
)

/**
 * `AuthDto`. Sign-in, refresh y session devuelven esta forma. En la respuesta de
 * refresh los campos de cuenta pueden no venir, por eso son opcionales.
 */
@Serializable
data class AuthResponse(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val token: String,
    val refreshToken: String? = null,
    val accessTokenExpiresAt: String? = null,
    val admin: Boolean? = null,
    val emailConfirmed: Boolean? = null,
)

/** Respuesta 202 de forgot-password. */
@Serializable
data class AcceptedResponse(
    val accepted: Boolean = false,
    val message: String? = null,
)
