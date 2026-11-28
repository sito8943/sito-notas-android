package dev.sito8943.sitonotas.feature.auth.data

import dev.sito8943.sitonotas.core.network.HttpMethod
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

    /**
     * `POST /auth/sign-up` (`RegisterRequest`: solo email y contraseña). Devuelve
     * un `AuthDto` sin refresh token, asi que quien llama inicia sesion despues
     * con `rememberMe`. 409 = correo ya registrado; 400 con cuerpo
     * `auth.register.featureDisabled` = registro apagado en el servidor.
     */
    suspend fun signUp(email: String, password: String): AuthResponse =
        client.post(SIGN_UP, SignUpRequest(email = email, password = password))

    /**
     * `POST /auth/password/change` con Bearer; 204 sin cuerpo. Revoca todas las
     * sesiones, tambien la de este telefono.
     *
     * Sin refresco ante 401: aqui un 401 es "contraseña actual incorrecta", no
     * un token caducado (quien llama renueva la sesion antes con [session]).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        client.execute(
            path = PASSWORD_CHANGE,
            method = HttpMethod.POST,
            body = client.encode(ChangePasswordRequest(currentPassword, newPassword)),
            auth = true,
            allowRefresh = false,
        )
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
        const val SIGN_UP = "/auth/sign-up"
        const val PASSWORD_CHANGE = "/auth/password/change"

        /** Mismo destino que usa `apps/notes` (`AppRoute.UpdatePassword`). */
        const val WEB_UPDATE_PASSWORD_URL = "https://notas.sito8943.com/auth/update-password"
    }
}
