package dev.sito8943.sitonotas.core.sync

import androidx.room.withTransaction
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.entity.CategoryEntity
import dev.sito8943.sitonotas.core.data.entity.NoteEntity
import dev.sito8943.sitonotas.core.data.entity.newClientId
import dev.sito8943.sitonotas.core.model.CategoryNames

/** Cuanto bajo una pasada. */
data class PullResult(
    val categoriesUpserted: Int = 0,
    val categoriesRemoved: Int = 0,
    val notesUpserted: Int = 0,
    val notesSkippedPending: Int = 0,
)

/**
 * Descarga lo del servidor y lo mezcla en Room, emparejando por `remoteId`.
 *
 * Regla de conflicto: si la fila local tiene algo pendiente de subir, gana lo
 * local (se subira); si no, gana el servidor.
 *
 * - Categorias ([syncCategories]): con un servidor que da tombstones
 *   (`GET /categories/deleted`), incrementales por `updatedAt >= cursor` y los
 *   borrados llegan como ids. Con el servidor anterior, siempre completas: al
 *   venir todas se detectan tambien las borradas en la web.
 * - Notas: incrementales por `updatedAt >= cursor`, con papelera incluida. La
 *   primera vez (sin cursor) es una descarga completa. Los borrados
 *   definitivos llegan por [pullNoteTombstones].
 */
class SyncPull(
    private val database: NotesDatabase,
    private val api: NotesEndpoints,
    private val state: SyncStateStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val pageSize: Int = PAGE_SIZE,
) {

    private val categories get() = database.categories()
    private val notes get() = database.notes()

    /**
     * Tombstones de categorias y luego la descarga: incremental si el servidor
     * los tiene y ya hay cursor, completa si no.
     */
    suspend fun syncCategories(userId: Long): PullResult {
        val since = state.cursor(userId, SyncCursor.CATEGORY_TOMBSTONES) ?: TOMBSTONES_FROM_START
        val tombstones = api.getDeletedCategories(since) ?: return pullCategories()
        val removed = applyCategoryTombstones(tombstones.ids)
        val result = pullCategories(updatedSince = state.cursor(userId, SyncCursor.CATEGORIES))
        // `until` es la hora del servidor antes de leer: lo que cambie despues
        // tendra un `updatedAt` posterior y entrara en la siguiente pasada.
        tombstones.until?.let { until ->
            state.setCursor(userId, SyncCursor.CATEGORY_TOMBSTONES, until)
            state.setCursor(userId, SyncCursor.CATEGORIES, until)
        }
        return result.copy(categoriesRemoved = result.categoriesRemoved + removed)
    }

    /**
     * Borra lo que el servidor borro para siempre. Lo pendiente se resuelve
     * como un 404 al subir: una nota activa con cambios se vuelve a crear, una
     * en la papelera o ya purgada aqui se va.
     */
    suspend fun pullNoteTombstones(userId: Long): Int {
        val since = state.cursor(userId, SyncCursor.NOTE_TOMBSTONES) ?: TOMBSTONES_FROM_START
        val tombstones = api.getDeletedNotes(since) ?: return 0
        var removed = 0
        database.withTransaction {
            for (remoteId in tombstones.ids.distinct()) {
                val local = notes.getByRemoteId(remoteId) ?: continue
                if (local.pending && !local.purged && local.deletedAt == null) {
                    notes.unlinkRemote(local.id, newClientId())
                } else {
                    notes.deleteByIds(listOf(local.id))
                    removed++
                }
            }
        }
        tombstones.until?.let { state.setCursor(userId, SyncCursor.NOTE_TOMBSTONES, it) }
        return removed
    }

    private suspend fun applyCategoryTombstones(remoteIds: List<Long>): Int {
        var removed = 0
        database.withTransaction {
            for (remoteId in remoteIds.distinct()) {
                val local = categories.getByRemoteId(remoteId) ?: continue
                if (local.dirty && !local.purged) {
                    categories.unlinkRemote(local.id, newClientId())
                } else {
                    categories.deleteByIds(listOf(local.id))
                    removed++
                }
            }
        }
        return removed
    }

    /**
     * [updatedSince] nulo = descarga completa (solo activas), que ademas borra
     * aqui las que ya no estan en el servidor. Con cursor llegan tambien las
     * que pasaron a la papelera, y esas se quitan.
     */
    suspend fun pullCategories(updatedSince: String? = null): PullResult {
        val full = updatedSince == null
        val fetched = fetchAll { page -> api.getCategories(page, pageSize, updatedSince) }
        val remote = fetched.filter { it.deletedAt == null }
        val trashed = fetched.filter { it.deletedAt != null }.map { it.id }.toSet()

        var upserted = 0
        var removed = 0
        database.withTransaction {
            val locals = categories.getAll()
            val byRemoteId = locals.filter { it.remoteId != null }.associateBy { it.remoteId!! }
            // Altas locales sin subir, por nombre: el backend no admite dos con
            // el mismo nombre, asi que una categoria creada sin conexion que ya
            // existe en la cuenta se enlaza en vez de chocar con un 409.
            val unlinkedByName = locals
                .filter { it.remoteId == null && !it.purged }
                .associateBy { CategoryNames.key(it.name) }
                .toMutableMap()

            for (item in remote) {
                val local = byRemoteId[item.id]
                when {
                    local == null -> {
                        val adopted = unlinkedByName.remove(CategoryNames.key(item.name.orEmpty()))
                        if (adopted != null) {
                            // Gana lo local: queda sucia y se subira como PATCH.
                            categories.update(adopted.copy(remoteId = item.id, dirty = true))
                        } else {
                            categories.insert(item.toEntity())
                        }
                        upserted++
                    }
                    local.dirty || local.purged -> Unit
                    else -> {
                        categories.update(
                            local.copy(
                                name = item.name.orEmpty(),
                                description = item.description,
                                color = item.color,
                                updatedAt = ServerTime.toEpochMillis(item.updatedAt) ?: local.updatedAt,
                            ),
                        )
                        upserted++
                    }
                }
            }

            // Borradas en el servidor (la web las borra para siempre) y sin
            // nada pendiente aqui: fuera. Sus enlaces caen en cascada. Solo una
            // descarga completa sabe que falta algo.
            val remoteIds = remote.map { it.id }.toSet()
            val gone = locals.filter {
                it.remoteId != null && !it.dirty && !it.purged &&
                    (if (full) it.remoteId !in remoteIds else it.remoteId in trashed)
            }
            if (gone.isNotEmpty()) {
                categories.deleteByIds(gone.map { it.id })
                removed = gone.size
            }
        }
        return PullResult(categoriesUpserted = upserted, categoriesRemoved = removed)
    }

    suspend fun pullNotes(userId: Long): PullResult {
        val cursor = state.pullCursor(userId)
        val remote = fetchAll { page -> api.getNotes(page, pageSize, cursor) }

        // Una nota que apunta a una categoria que aqui no existe: se descargan
        // las categorias antes de mezclar, para no perder el enlace.
        val known = categories.getAll().mapNotNull { it.remoteId }.toSet()
        val referenced = remote.flatMap { note -> note.categories.map { it.id } }.toSet()
        if (!known.containsAll(referenced)) pullCategories()

        var upserted = 0
        var skipped = 0
        database.withTransaction {
            val categoryIdByRemote = categories.getAll()
                .filter { it.remoteId != null && !it.purged }
                .associate { it.remoteId!! to it.id }

            for (item in remote) {
                val local = notes.getByRemoteId(item.id)
                if (local != null && local.pending) {
                    skipped++
                    continue
                }
                val merged = item.toEntity(base = local)
                val localId = if (local == null) notes.insert(merged) else merged.id.also { notes.update(merged) }
                notes.replaceCategories(localId, item.categories.mapNotNull { categoryIdByRemote[it.id] })
                upserted++
            }

            notes.deleteExpiredTrash(before = now() - TRASH_RETENTION_MILLIS)
        }

        val newest = remote.fold(cursor) { acc, note -> ServerTime.max(acc, note.updatedAt) }
        if (newest != cursor) state.setPullCursor(userId, newest)

        return PullResult(notesUpserted = upserted, notesSkippedPending = skipped)
    }

    private suspend fun <T> fetchAll(load: suspend (page: Int) -> PageResponse<T>): List<T> {
        val all = mutableListOf<T>()
        var page = 0
        while (page < MAX_PAGES) {
            val response = load(page)
            all += response.items
            if (response.items.isEmpty() || page + 1 >= response.totalPages) break
            page++
        }
        return all
    }

    private fun RemoteCategory.toEntity() = CategoryEntity(
        remoteId = id,
        name = name.orEmpty(),
        description = description,
        color = color,
        updatedAt = ServerTime.toEpochMillis(updatedAt) ?: now(),
        dirty = false,
    )

    private fun RemoteNote.toEntity(base: NoteEntity?): NoteEntity {
        val updated = ServerTime.toEpochMillis(updatedAt) ?: base?.updatedAt ?: now()
        val deleted = ServerTime.toEpochMillis(deletedAt)
        return NoteEntity(
            id = base?.id ?: 0,
            remoteId = id,
            title = title.orEmpty(),
            content = content.orEmpty(),
            favorite = favorite,
            position = position,
            createdAt = ServerTime.toEpochMillis(createdAt) ?: base?.createdAt ?: updated,
            updatedAt = updated,
            deletedAt = deleted,
            remoteDeleted = deleted != null,
            dirty = false,
            purged = false,
            localVersion = base?.localVersion ?: 0,
            clientId = base?.clientId ?: newClientId(),
        )
    }

    companion object {
        const val PAGE_SIZE = 100

        /** `since` de la primera peticion de tombstones: todos los que haya. */
        const val TOMBSTONES_FROM_START = "1970-01-01T00:00:00Z"

        /** Tope de seguridad del paginado (como `MAX_NOTE_PAGES` en la web). */
        const val MAX_PAGES = 1_000

        /** `app.notes.trash.retention-days` por defecto en el backend. */
        const val TRASH_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
