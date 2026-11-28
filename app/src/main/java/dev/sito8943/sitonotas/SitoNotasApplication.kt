package dev.sito8943.sitonotas

import android.app.Application
import dev.sito8943.sitonotas.core.di.AppContainer

class SitoNotasApplication : Application() {

    /** Raiz de composicion del proceso; la Activity la lee, no la crea. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Sesion cifrada leida antes del primer frame: sin parpadeo de
        // "sin cuenta" a "con cuenta".
        container.sessionRepository.restore()

        // La sincronizacion vive con el proceso, no con una pantalla: girar el
        // telefono o cambiar el idioma no debe cortarla a medias.
        container.syncCoordinator.start()
        container.realtimeClient.start()
    }
}
