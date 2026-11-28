package dev.sito8943.sitonotas.core.sync

/**
 * Paso adicional de una sincronizacion. `SyncEngine` los ejecuta en orden
 * despues de subir las notas (ya tienen `remoteId`) y antes de bajarlas (la
 * descarga recoge lo que el paso cambio en el servidor). Un fallo corta la
 * pasada como cualquier otro paso y se reintenta en el siguiente disparador.
 */
fun interface SyncStep {
    suspend fun run(userId: Long)
}
