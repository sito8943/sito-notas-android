package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.network.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Lo que la sincronizacion necesita del servidor de notas. Interfaz para poder
 * probar `SyncPush`/`SyncPull` sin red.
 *
 * Se usa el CRUD por entidad, no `/sync/...` (igual que SitoWallet).
 */
interface NotesEndpoints {
    suspend fun getCategories(page: Int, pageSize: Int): PageResponse<RemoteCategory>

    /**
     * Notas propias, activas y en papelera. [updatedSince] (inclusive) es el
     * `updatedAt` mas alto ya descargado; nulo = descarga completa.
     */
    suspend fun getNotes(page: Int, pageSize: Int, updatedSince: String?): PageResponse<RemoteNote>

    suspend fun createCategories(bodies: List<CreateCategoryBody>): List<Long>
    suspend fun updateCategories(bodies: List<UpdateCategoryBody>)
    suspend fun updateCategory(body: UpdateCategoryBody)
    suspend fun deleteCategoriesPermanently(remoteIds: List<Long>)

    suspend fun createNotes(bodies: List<CreateNoteBody>): List<Long>
    suspend fun updateNotes(bodies: List<UpdateNoteBody>)
    suspend fun updateNote(body: UpdateNoteBody)
    suspend fun trashNotes(remoteIds: List<Long>)
    suspend fun restoreNotes(remoteIds: List<Long>)
    suspend fun deleteNotesPermanently(remoteIds: List<Long>)
}

class NotesApi(private val client: RestClient) : NotesEndpoints {

    override suspend fun getCategories(page: Int, pageSize: Int): PageResponse<RemoteCategory> =
        client.get("$CATEGORIES?page=$page&pageSize=$pageSize&sort=id&order=ASC", auth = true)

    /**
     * `softDeleteScope=ALL` trae tambien la papelera. El rango de fechas va por
     * el parametro `filters` (`IFilterDTO.parseAndAssignFilters`), que es como
     * lo filtra la web. Se ordena por id para que el paginado sea estable.
     */
    override suspend fun getNotes(
        page: Int,
        pageSize: Int,
        updatedSince: String?,
    ): PageResponse<RemoteNote> {
        val filter = updatedSince?.let { "&filters=" + encode("updatedAt>=$it") }.orEmpty()
        return client.get(
            "$NOTES?page=$page&pageSize=$pageSize&sort=id&order=ASC&softDeleteScope=ALL$filter",
            auth = true,
        )
    }

    override suspend fun createCategories(bodies: List<CreateCategoryBody>): List<Long> =
        idsOf("$CATEGORIES/batch", bodies)

    override suspend fun updateCategories(bodies: List<UpdateCategoryBody>) {
        client.patch<Int, List<UpdateCategoryBody>>("$CATEGORIES/batch", bodies)
    }

    override suspend fun updateCategory(body: UpdateCategoryBody) {
        client.patch<Long, UpdateCategoryBody>("$CATEGORIES/${body.id}", body)
    }

    override suspend fun deleteCategoriesPermanently(remoteIds: List<Long>) {
        client.delete<Int, List<Long>>("$CATEGORIES/permanent", remoteIds)
    }

    override suspend fun createNotes(bodies: List<CreateNoteBody>): List<Long> =
        idsOf("$NOTES/batch", bodies)

    override suspend fun updateNotes(bodies: List<UpdateNoteBody>) {
        client.patch<Int, List<UpdateNoteBody>>("$NOTES/batch", bodies)
    }

    override suspend fun updateNote(body: UpdateNoteBody) {
        client.patch<Long, UpdateNoteBody>("$NOTES/${body.id}", body)
    }

    override suspend fun trashNotes(remoteIds: List<Long>) {
        client.delete<Int, List<Long>>(NOTES, remoteIds)
    }

    override suspend fun restoreNotes(remoteIds: List<Long>) {
        client.patch<Int, List<Long>>("$NOTES/restore", remoteIds)
    }

    override suspend fun deleteNotesPermanently(remoteIds: List<Long>) {
        client.delete<Int, List<Long>>("$NOTES/permanent", remoteIds)
    }

    /**
     * Los `POST /batch` devuelven los ids creados en orden de entrada. Un
     * servidor anterior a ese cambio devuelve un conteo: sin ids no hay
     * `remoteId` y la fila no se podria volver a tocar, asi que se corta.
     */
    private suspend inline fun <reified B> idsOf(path: String, bodies: List<B>): List<Long> {
        val raw = client.postForRawBody(path, bodies, auth = true).trim()
        if (raw.startsWith("[")) {
            return Json.decodeFromString(ListSerializer(Long.serializer()), raw)
        }
        throw OutdatedServerException(path, raw)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val NOTES = "/notes"
        const val CATEGORIES = "/categories"
    }
}

class OutdatedServerException(path: String, body: String) : Exception(
    "el servidor respondio \"$body\" en $path en vez de la lista de ids: " +
        "esta desplegada una version anterior del backend",
)
