package dev.sito8943.sitonotas.core.sync

import dev.sito8943.sitonotas.core.network.ApiException

/**
 * Servidor de mentira con estado: guarda lo que se le sube y lo devuelve al
 * pedir paginas, para probar subida y bajada contra la misma "verdad".
 */
class FakeNotesApi : NotesEndpoints {

    val calls = mutableListOf<String>()
    val categories = linkedMapOf<Long, RemoteCategory>()
    val notes = linkedMapOf<Long, RemoteNote>()
    val createdNoteBodies = mutableListOf<CreateNoteBody>()
    val updatedNoteBodies = mutableListOf<UpdateNoteBody>()
    val noteCursors = mutableListOf<String?>()
    var pageSize: Int? = null
    var nextId = 1000L
    var clock = 0

    /** Hace fallar una llamada concreta (por nombre de [calls]). */
    var failOn: Map<String, Exception> = emptyMap()

    private fun call(name: String) {
        calls += name
        failOn[name]?.let { throw it }
    }

    private fun stamp(): String = "2026-11-24T10:%02d:%02d".format(clock / 60, clock % 60).also { clock++ }

    override suspend fun getCategories(page: Int, pageSize: Int): PageResponse<RemoteCategory> {
        call("getCategories")
        return page(categories.values.filter { it.deletedAt == null }, page, this.pageSize ?: pageSize)
    }

    override suspend fun getNotes(page: Int, pageSize: Int, updatedSince: String?): PageResponse<RemoteNote> {
        call("getNotes")
        if (page == 0) noteCursors += updatedSince
        val since = ServerTime.parse(updatedSince)
        val visible = notes.values.filter { note ->
            since == null || !ServerTime.parse(note.updatedAt)!!.isBefore(since)
        }
        return page(visible, page, this.pageSize ?: pageSize)
    }

    override suspend fun createCategories(bodies: List<CreateCategoryBody>): List<Long> {
        call("createCategories")
        return bodies.map { body ->
            val id = nextId++
            categories[id] = RemoteCategory(id, body.name, body.description, body.color, stamp())
            id
        }
    }

    override suspend fun updateCategories(bodies: List<UpdateCategoryBody>) {
        call("updateCategories")
        if (bodies.any { it.id !in categories }) throw ApiException(404)
        bodies.forEach { updateCategoryRow(it) }
    }

    override suspend fun updateCategory(body: UpdateCategoryBody) {
        call("updateCategory")
        if (body.id !in categories) throw ApiException(404)
        updateCategoryRow(body)
    }

    private fun updateCategoryRow(body: UpdateCategoryBody) {
        categories[body.id] = RemoteCategory(body.id, body.name, body.description, body.color, stamp())
    }

    override suspend fun deleteCategoriesPermanently(remoteIds: List<Long>) {
        call("deleteCategoriesPermanently")
        remoteIds.forEach { categories.remove(it) }
        notes.replaceAll { _, note -> note.copy(categories = note.categories.filter { it.id !in remoteIds }) }
    }

    override suspend fun createNotes(bodies: List<CreateNoteBody>): List<Long> {
        call("createNotes")
        createdNoteBodies += bodies
        return bodies.map { body ->
            val id = nextId++
            val time = stamp()
            notes[id] = RemoteNote(
                id = id,
                title = body.title,
                content = body.content,
                categories = body.categoryIds.map { RemoteCategoryRef(it) },
                createdAt = time,
                updatedAt = time,
            )
            id
        }
    }

    override suspend fun updateNotes(bodies: List<UpdateNoteBody>) {
        call("updateNotes")
        if (bodies.any { it.id !in notes }) throw ApiException(404)
        bodies.forEach { updateNoteRow(it) }
    }

    override suspend fun updateNote(body: UpdateNoteBody) {
        call("updateNote")
        if (body.id !in notes) throw ApiException(404)
        updateNoteRow(body)
    }

    private fun updateNoteRow(body: UpdateNoteBody) {
        updatedNoteBodies += body
        notes[body.id] = notes.getValue(body.id).copy(
            title = body.title,
            content = body.content,
            favorite = body.favorite,
            categories = body.categoryIds.map { RemoteCategoryRef(it) },
            updatedAt = stamp(),
        )
    }

    override suspend fun trashNotes(remoteIds: List<Long>) {
        call("trashNotes")
        remoteIds.filter { it in notes }.forEach { id ->
            val time = stamp()
            notes[id] = notes.getValue(id).copy(deletedAt = time, updatedAt = time)
        }
    }

    override suspend fun restoreNotes(remoteIds: List<Long>) {
        call("restoreNotes")
        remoteIds.filter { it in notes }.forEach { id ->
            notes[id] = notes.getValue(id).copy(deletedAt = null, updatedAt = stamp())
        }
    }

    override suspend fun deleteNotesPermanently(remoteIds: List<Long>) {
        call("deleteNotesPermanently")
        // Como el backend: solo lo que ya esta en la papelera.
        remoteIds.filter { notes[it]?.deletedAt != null }.forEach { notes.remove(it) }
    }

    /** Alta directa en el "servidor", como si viniera de la web. */
    fun seedNote(note: RemoteNote) {
        notes[note.id] = note
    }

    fun seedCategory(category: RemoteCategory) {
        categories[category.id] = category
    }

    private fun <T> page(all: List<T>, page: Int, size: Int): PageResponse<T> {
        val chunks = all.chunked(size)
        return PageResponse(
            items = chunks.getOrElse(page) { emptyList() },
            currentPage = page,
            pageSize = size,
            totalElements = all.size.toLong(),
            totalPages = chunks.size,
        )
    }
}

class InMemorySyncStateStore : SyncStateStore {
    val cursors = mutableMapOf<Long, String?>()
    private val last = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    override suspend fun pullCursor(userId: Long): String? = cursors[userId]
    override suspend fun setPullCursor(userId: Long, cursor: String?) {
        cursors[userId] = cursor
    }

    override val lastSyncAt: kotlinx.coroutines.flow.Flow<Long?> = last
    override suspend fun setLastSyncAt(epochMillis: Long) {
        last.value = epochMillis
    }
}
