package dev.sito8943.sitonotas.core.network

/**
 * Lo unico que `RestClient` necesita saber de la sesion. Existe para que la capa
 * de red no dependa de la de autenticacion: `SessionRepository` lo implementa y
 * `AppContainer` los une.
 */
interface AuthTokenSupplier {

    suspend fun accessToken(): String?

    /**
     * Canjea el refresh token por uno de acceso nuevo.
     *
     * La respuesta no es un si/no: distinguir "el servidor rechazo el token" de
     * "no se pudo hablar con el servidor" es lo que decide si la sesion cae. Ver
     * [RefreshOutcome].
     *
     * La implementacion debe ser single-flight (ver `SessionRepository`).
     */
    suspend fun refresh(): RefreshOutcome

    /** El refresco no pudo recuperar la sesion: hay que volver a invitado. */
    suspend fun onSessionExpired()
}

/**
 * Por que esto no es un `Boolean`: un refresco puede fallar por dos motivos que
 * piden lo contrario el uno del otro.
 *
 * Si el backend **rechaza** el refresh token, la sesion esta muerta y no hay mas
 * que hacer que volver al login. Pero si el telefono se quedo sin cobertura, el
 * token sigue siendo perfectamente valido: cerrar la sesion ahi echa a la
 * usuaria de su cuenta por haber entrado en un ascensor, y encima le hace
 * teclear la contraseña de nuevo justo cuando no hay red para comprobarla.
 *
 * Ante la duda se conserva la sesion: como mucho la siguiente accion falla con
 * un error de red, que es recuperable; un cierre de sesion no lo es.
 */
enum class RefreshOutcome {
    /** Token nuevo guardado; se puede reintentar la peticion. */
    REFRESHED,

    /** El servidor dijo que no. La sesion cae. */
    REJECTED,

    /** No hubo respuesta (sin red, timeout, 5xx). La sesion se conserva. */
    UNREACHABLE,
}
