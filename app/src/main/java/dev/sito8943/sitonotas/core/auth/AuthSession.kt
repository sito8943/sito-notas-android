package dev.sito8943.sitonotas.core.auth

import kotlinx.serialization.Serializable

/**
 * Sesion autenticada tal y como se persiste. Portado de `SessionDto` +
 * `tokenStore.ts`, que en la app RN estan separados: los tokens en
 * expo-secure-store y el retrato publico de la cuenta en AsyncStorage.
 *
 * Aqui va todo junto y todo cifrado: no hay ninguna ventaja en dejar el correo
 * del usuario en claro, y una sola escritura no puede quedar a medias.
 */
@Serializable
data class AuthSession(
    val userId: Long,
    val username: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String? = null,
    /** ISO-8601 local, tal cual lo manda el backend. Nulo si no lo envio. */
    val accessTokenExpiresAt: String? = null,
    val emailConfirmed: Boolean = false,
)

/** Resultado de leer la sesion del disco. */
sealed interface SessionLoadResult {
    /** No hay sesion guardada: el usuario va como invitado. */
    data object Missing : SessionLoadResult

    /**
     * Habia algo pero no se pudo descifrar (clave del Keystore invalidada, datos
     * corruptos). Se trata como sesion caida, no como error a mostrar.
     */
    data object Unreadable : SessionLoadResult

    data class Loaded(val session: AuthSession) : SessionLoadResult
}

interface SessionStore {
    fun load(): SessionLoadResult
    fun save(session: AuthSession)
    fun clear()

    /**
     * Si ya se contesto a la pantalla de login, sea entrando o eligiendo seguir
     * sin cuenta.
     *
     * No es una sesion, pero si es estado de autenticacion, y tiene que leerse
     * **sincrono** al arrancar: el destino inicial del grafo se decide antes de
     * la primera composicion. Sin esto, quien elige seguir sin cuenta vuelve a
     * ver el login en cada arranque, y cambiar el idioma recrea la Activity, asi
     * que tambien la mandaba de vuelta ahi.
     */
    fun loginAnswered(): Boolean
    fun markLoginAnswered()
}
