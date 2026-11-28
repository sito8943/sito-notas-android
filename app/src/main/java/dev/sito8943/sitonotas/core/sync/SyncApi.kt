package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.network.ApiException
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
    /**
     * Categorias propias. [updatedSince] nulo = todas las activas (descarga
     * completa); con cursor, las cambiadas desde entonces, papelera incluida
     * (solo el servidor nuevo filtra categorias por `updatedAt`).
     */
    suspend fun getCategories(page: Int, pageSize: Int, updatedSince: String?): PageResponse<RemoteCategory>

    /**
     * Notas propias, activas y en papelera. [updatedSince] (inclusive) es el
     * `updatedAt` mas alto ya descargado; nulo = descarga completa.
     */
    suspend fun getNotes(page: Int, pageSize: Int, updatedSince: String?): PageResponse<RemoteNote>

    suspend fun createCategories(bodies: List<CreateCategoryBody>): List<Long>
    /**
     * @return ids actualizados (servidor nuevo: los que no existen se saltan),
     *   o nulo si el servidor solo devuelve un conteo (anterior: todos o 404).
     */
    suspend fun updateCategories(bodies: List<UpdateCategoryBody>): List<Long>?
    suspend fun updateCategory(body: UpdateCategoryBody)
    suspend fun deleteCategoriesPermanently(remoteIds: List<Long>)

    suspend fun createNotes(bodies: List<CreateNoteBody>): List<Long>
    /** Igual que [updateCategories]. */
    suspend fun updateNotes(bodies: List<UpdateNoteBody>): List<Long>?
    suspend fun updateNote(body: UpdateNoteBody)
    suspend fun trashNotes(remoteIds: List<Long>)
    suspend fun restoreNotes(remoteIds: List<Long>)
    suspend fun deleteNotesPermanently(remoteIds: List<Long>)

    /**
     * Ids borrados para siempre desde [since] (`GET /notes/deleted`). Nulo =
     * el servidor no tiene el endpoint (version anterior).
     */
    suspend fun getDeletedNotes(since: String): DeletedIds?

    /** Igual que [getDeletedNotes] para `GET /categories/deleted`. */
    suspend fun getDeletedCategories(since: String): DeletedIds?
}

class NotesApi(private val client: RestClient) : NotesEndpoints {

    override suspend fun getCategories(
        page: Int,
        pageSize: Int,
        updatedSince: String?,
    ): PageResponse<RemoteCategory> {
        val filter = updatedSince
            ?.let { "&softDeleteScope=ALL&filters=" + encode("updatedAt>=$it") }
            .orEmpty()
        return client.get("$CATEGORIES?page=$page&pageSize=$pageSize&sort=id&order=ASC$filter", auth = true)
    }

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

    override suspend fun updateCategories(bodies: List<UpdateCategoryBody>): List<Long>? =
        updatedIdsOf(client.patchForRawBody("$CATEGORIES/batch", bodies))

    override suspend fun updateCategory(body: UpdateCategoryBody) {
        client.patch<Long, UpdateCategoryBody>("$CATEGORIES/${body.id}", body)
    }

    override suspend fun deleteCategoriesPermanently(remoteIds: List<Long>) {
        client.delete<Int, List<Long>>("$CATEGORIES/permanent", remoteIds)
    }

    override suspend fun createNotes(bodies: List<CreateNoteBody>): List<Long> =
        idsOf("$NOTES/batch", bodies)

    override suspend fun updateNotes(bodies: List<UpdateNoteBody>): List<Long>? =
        updatedIdsOf(client.patchForRawBody("$NOTES/batch", bodies))

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

    override suspend fun getDeletedNotes(since: String): DeletedIds? = deletedSince(NOTES, since)

    override suspend fun getDeletedCategories(since: String): DeletedIds? = deletedSince(CATEGORIES, since)

    /**
     * El servidor anterior no tiene `/deleted`: lo toma por `/{id}` y responde
     * 400 (no es un numero); otros despliegues, 404/405/501.
     */
    private suspend fun deletedSince(base: String, since: String): DeletedIds? = try {
        client.get<DeletedIds>("$base/deleted?since=${encode(since)}", auth = true)
    } catch (error: ApiException) {
        if (error.status in OLD_SERVER_STATUSES) null else throw error
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
        val OLD_SERVER_STATUSES = setOf(400, 404, 405, 501)
    }
}

/**
 * Respuesta de un `PATCH /batch`: el servidor nuevo devuelve los ids que
 * actualizo (`[1,2]`), el anterior un conteo (`2`). Nulo = conteo.
 */
internal fun updatedIdsOf(raw: String): List<Long>? {
    val text = raw.trim()
    return if (text.startsWith("[")) Json.decodeFromString(ListSerializer(Long.serializer()), text) else null
}

class OutdatedServerException(path: String, body: String) : Exception(
    "el servidor respondio \"$body\" en $path en vez de la lista de ids: " +
        "esta desplegada una version anterior del backend",
)
