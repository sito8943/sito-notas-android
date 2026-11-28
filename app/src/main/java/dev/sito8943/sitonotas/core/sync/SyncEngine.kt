package dev.sito8943.sitonotas.core.sync

/** Resultado de una sincronizacion completa. */
data class SyncResult(
    val push: PushResult,
    val pulledCategories: PullResult,
    val pulledNotes: PullResult,
    /** Notas quitadas por borrados definitivos en el servidor (tombstones). */
    val notesRemoved: Int = 0,
)

/**
 * Una sincronizacion completa, en este orden:
 *
 * 0. Si los tombstones guardados caducaron en el servidor, todo completo.
 * 1. Bajar categorias (tombstones y cambios): enlaza por nombre las creadas
 *    sin conexion antes de intentar crearlas (el backend rechaza nombres
 *    repetidos con 409).
 * 2. Subir categorias.
 * 3. Subir notas (ya pueden referenciar ids remotos de categorias).
 * 4. Bajar notas cambiadas desde el ultimo cursor. Va despues de subir para
 *    recoger lo que el servidor decide al crear (posicion, fechas).
 * 5. Tombstones de notas: lo borrado para siempre en otro sitio.
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
        pull.resetStaleCursors(userId)
        val pulledCategories = pull.syncCategories(userId)
        val categoriesPushed = push.pushCategories()
        val pushed = push.pushNotes(categoriesPushed)
        extraSteps.forEach { it.run(userId) }
        val pulledNotes = pull.pullNotes(userId)
        val notesRemoved = pull.pullNoteTombstones(userId)
        state.setLastSyncAt(now())
        return SyncResult(pushed, pulledCategories, pulledNotes, notesRemoved)
    }
}
