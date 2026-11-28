package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.entity.CategoryEntity
import dev.sito8943.sitonotas.core.data.entity.NoteEntity
import dev.sito8943.sitonotas.core.data.entity.newClientId
import dev.sito8943.sitonotas.core.network.ApiException

/** Cuanto subio una pasada. */
data class PushResult(
    val categoriesCreated: Int = 0,
    val categoriesUpdated: Int = 0,
    val categoriesDeleted: Int = 0,
    val notesCreated: Int = 0,
    val notesUpdated: Int = 0,
    val notesTrashed: Int = 0,
    val notesRestored: Int = 0,
    val notesDeleted: Int = 0,
)

/**
 * Sube lo pendiente. Categorias **antes** que notas: una nota referencia sus
 * categorias por id remoto, y una categoria sin `remoteId` no se puede
 * mandar.
 *
 * Cada paso guarda lo confirmado antes de seguir. Si algo falla a medias, lo
 * que ya subio tiene su `remoteId` y no se repite al reintentar.
 */
class SyncPush(
    private val database: NotesDatabase,
    private val api: NotesEndpoints,
) {

    private val categories get() = database.categories()
    private val notes get() = database.notes()

    suspend fun push(): PushResult {
        val categoryResult = pushCategories()
        return pushNotes(categoryResult)
    }

    suspend fun pushCategories(base: PushResult = PushResult()): PushResult {
        // Altas.
        val creates = categories.getPendingCreates()
        if (creates.isNotEmpty()) {
            val ids = api.createCategories(creates.map { it.toCreateBody() })
            checkIdCount("categories", creates.size, ids.size)
            creates.zip(ids).forEach { (row, remoteId) ->
                categories.markCreated(row.id, remoteId, row.localVersion)
            }
        }

        // Ediciones.
        val updates = categories.getPendingUpdates()
        updateWithFallback(
            rows = updates,
            batch = { rows -> api.updateCategories(rows.map { it.toUpdateBody() }) },
            single = { row -> api.updateCategory(row.toUpdateBody()) },
            onUpdated = { row -> categories.markUpdated(row.id, row.localVersion) },
            onGone = { row -> categories.unlinkRemote(row.id, newClientId()) },
        )

        // Borrados definitivos (la web no tiene papelera de categorias).
        val purges = categories.getPendingPurges()
        if (purges.isNotEmpty()) {
            api.deleteCategoriesPermanently(purges.mapNotNull { it.remoteId })
            categories.deleteByIds(purges.map { it.id })
        }

        return base.copy(
            categoriesCreated = creates.size,
            categoriesUpdated = updates.size,
            categoriesDeleted = purges.size,
        )
    }

    suspend fun pushNotes(base: PushResult = PushResult()): PushResult {
        val remoteCategoryIds = categories.getAll()
            .filter { it.remoteId != null && !it.purged }
            .associate { it.id to it.remoteId!! }

        // Altas. Tambien las que ya estan en la papelera: en el servidor
        // tienen que existir para aparecer en su papelera.
        val creates = notes.getPendingCreates()
        if (creates.isNotEmpty()) {
            val prepared = creates.map { note -> note to categoryRemoteIds(note.id, remoteCategoryIds) }
            val ids = api.createNotes(
                prepared.map { (note, refs) ->
                    CreateNoteBody(note.title, note.content, refs.remoteIds, note.clientId)
                },
            )
            checkIdCount("notes", creates.size, ids.size)
            prepared.zip(ids).forEach { (pair, remoteId) ->
                val (note, refs) = pair
                // `favorite` no viaja en el alta y una categoria aun sin id
                // remoto tampoco: los dos quedan para el PATCH.
                notes.markCreated(
                    id = note.id,
                    remoteId = remoteId,
                    version = note.localVersion,
                    stillDirty = note.favorite || refs.missing,
                )
            }
        }

        // Ediciones (se releen: las altas con favorita acaban de quedar aqui).
        val updates = notes.getPendingUpdates()
        updateWithFallback(
            rows = updates,
            batch = { rows -> api.updateNotes(rows.map { it.toUpdateBody(remoteCategoryIds) }) },
            single = { row -> api.updateNote(row.toUpdateBody(remoteCategoryIds)) },
            onUpdated = { row -> notes.markUpdated(row.id, row.localVersion) },
            onGone = { row ->
                // En la papelera y el servidor ya no la tiene: la purgo el
                // servidor. Si no, se vuelve a crear con lo que hay en local.
                if (row.deletedAt != null) notes.deleteByIds(listOf(row.id)) else notes.unlinkRemote(row.id, newClientId())
            },
        )

        // Papelera y restauracion: el backend ignora ids ajenos o inexistentes.
        val trash = notes.getPendingTrash()
        if (trash.isNotEmpty()) {
            api.trashNotes(trash.mapNotNull { it.remoteId })
            notes.markRemoteDeleted(trash.map { it.id }, deleted = true)
        }
        val restores = notes.getPendingRestores()
        if (restores.isNotEmpty()) {
            api.restoreNotes(restores.mapNotNull { it.remoteId })
            notes.markRemoteDeleted(restores.map { it.id }, deleted = false)
        }

        // Borrado definitivo: solo despues de que el servidor la tenga en la
        // papelera (paso anterior).
        val purges = notes.getPendingPurges()
        if (purges.isNotEmpty()) {
            api.deleteNotesPermanently(purges.mapNotNull { it.remoteId })
            notes.deleteByIds(purges.map { it.id })
        }

        return base.copy(
            notesCreated = creates.size,
            notesUpdated = updates.size,
            notesTrashed = trash.size,
            notesRestored = restores.size,
            notesDeleted = purges.size,
        )
    }

    /**
     * Lote primero. Un 404 en el lote dice que **alguna** fila ya no existe en
     * el servidor (purgada de la papelera, borrada desde la web) y tumba el
     * lote entero; se reintenta una a una para aislarla y que el resto suba.
     */
    private suspend fun <T> updateWithFallback(
        rows: List<T>,
        batch: suspend (List<T>) -> Unit,
        single: suspend (T) -> Unit,
        onUpdated: suspend (T) -> Unit,
        onGone: suspend (T) -> Unit,
    ) {
        if (rows.isEmpty()) return
        try {
            batch(rows)
            rows.forEach { onUpdated(it) }
            return
        } catch (error: ApiException) {
            if (error.status != HTTP_NOT_FOUND) throw error
        }
        for (row in rows) {
            try {
                single(row)
                onUpdated(row)
            } catch (error: ApiException) {
                if (error.status != HTTP_NOT_FOUND) throw error
                onGone(row)
            }
        }
    }

    private class CategoryRefs(val remoteIds: List<Long>, val missing: Boolean)

    private suspend fun categoryRemoteIds(noteId: Long, remoteIds: Map<Long, Long>): CategoryRefs {
        val localIds = notes.categoryIdsOf(noteId)
        val mapped = localIds.mapNotNull { remoteIds[it] }
        return CategoryRefs(mapped, missing = mapped.size != localIds.size)
    }

    private suspend fun NoteEntity.toUpdateBody(remoteIds: Map<Long, Long>) = UpdateNoteBody(
        id = requireNotNull(remoteId),
        title = title,
        content = content,
        favorite = favorite,
        categoryIds = categoryRemoteIds(id, remoteIds).remoteIds,
    )

    private fun CategoryEntity.toCreateBody() = CreateCategoryBody(name, description, color, clientId)

    private fun CategoryEntity.toUpdateBody() =
        UpdateCategoryBody(requireNotNull(remoteId), name, description, color)

    private fun checkIdCount(entity: String, sent: Int, received: Int) {
        check(sent == received) {
            "El servidor devolvio $received ids de $entity para $sent altas"
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
