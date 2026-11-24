package dev.sito8943.sitonotas.feature.auth.data

import dev.sito8943.sitonotas.core.network.RestClient

/**
 * Endpoints de sito-auth (`servers/auth`, `AuthController`). Es el mismo
 * servicio de login que usa SitoWallet: la cuenta es la misma.
 *
 * Solo habla HTTP: no toca el almacen de sesion; eso es de `SessionRepository`.
 * El `client` apunta a `AUTH_BASE_URL`, no a la API de notas.
 */
class AuthApi(private val client: RestClient) {

    suspend fun signIn(email: String, password: String): AuthResponse =
        client.post(SIGN_IN, SignInRequest(email = email, password = password))

    /** `refreshToken` rota: el backend devuelve uno nuevo y anula el anterior. */
    suspend fun refresh(refreshToken: String): AuthResponse =
        client.post(REFRESH, RefreshRequest(refreshToken))

    suspend fun session(): AuthResponse = client.get(SESSION, auth = true)

    suspend fun signOut(refreshToken: String?) {
        client.postForUnit(SIGN_OUT, SignOutRequest(refreshToken), auth = true)
    }

    /** El enlace del correo lo completa la web de notas, no la app. */
    suspend fun forgotPassword(email: String): AcceptedResponse =
        client.post(PASSWORD_FORGOT, ForgotPasswordRequest(email, WEB_UPDATE_PASSWORD_URL))

    private companion object {
        const val SIGN_IN = "/auth/sign-in"
        const val SIGN_OUT = "/auth/sign-out"
        const val REFRESH = "/auth/refresh"
        const val SESSION = "/auth/session"
        const val PASSWORD_FORGOT = "/auth/password/forgot"

        /** Mismo destino que usa `apps/notes` (`AppRoute.UpdatePassword`). */
        const val WEB_UPDATE_PASSWORD_URL = "https://notas.sito8943.com/auth/update-password"
    }
}
