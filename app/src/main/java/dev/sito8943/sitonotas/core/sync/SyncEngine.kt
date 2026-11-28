package dev.sito8943.sitonotas.core.sync

/** Resultado de una sincronizacion completa. */
data class SyncResult(
    val push: PushResult,
    val pulledCategories: PullResult,
    val pulledNotes: PullResult,
)

/**
 * Una sincronizacion completa, en este orden:
 *
 * 1. Bajar categorias: enlaza por nombre las creadas sin conexion antes de
 *    intentar crearlas (el backend rechaza nombres repetidos con 409).
 * 2. Subir categorias.
 * 3. Subir notas (ya pueden referenciar ids remotos de categorias).
 * 4. Bajar notas cambiadas desde el ultimo cursor. Va despues de subir para
 *    recoger lo que el servidor decide al crear (posicion, fechas).
 *
 * No hay concurrencia aqui: la serializa `SyncCoordinator`.
 */
class SyncEngine(
    private val push: SyncPush,
    private val pull: SyncPull,
    private val state: SyncStateStore,
    /** Pasos extra (p. ej. el orden manual) entre subir y bajar notas. */
    private val extraSteps: List<SyncStep> = emptyList(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(userId: Long): SyncResult {
        val pulledCategories = pull.pullCategories()
        val categoriesPushed = push.pushCategories()
        val pushed = push.pushNotes(categoriesPushed)
        extraSteps.forEach { it.run(userId) }
        val pulledNotes = pull.pullNotes(userId)
        state.setLastSyncAt(now())
        return SyncResult(pushed, pulledCategories, pulledNotes)
    }
}
