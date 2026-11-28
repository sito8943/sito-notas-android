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

    /**
     * Servidor nuevo: tombstones, categorias incrementales, PATCH por lotes que
     * salta ids que no existen y fechas con offset. Falso = el de produccion.
     */
    var newServer = false
    val noteTombstones = mutableListOf<Pair<Long, String>>()
    val categoryTombstones = mutableListOf<Pair<Long, String>>()
    val categoryCursors = mutableListOf<String?>()
    val createdCategoryBodies = mutableListOf<CreateCategoryBody>()

    /** Hace fallar una llamada concreta (por nombre de [calls]). */
    var failOn: Map<String, Exception> = emptyMap()

    private fun call(name: String) {
        calls += name
        failOn[name]?.let { throw it }
    }

    private fun stamp(): String =
        ("2026-11-24T10:%02d:%02d".format(clock / 60, clock % 60) + if (newServer) "Z" else "").also { clock++ }

    override suspend fun getCategories(page: Int, pageSize: Int, updatedSince: String?): PageResponse<RemoteCategory> {
        call("getCategories")
        if (page == 0) categoryCursors += updatedSince
        val since = ServerTime.parse(updatedSince)
        val visible = if (since == null) {
            categories.values.filter { it.deletedAt == null }
        } else {
            check(newServer) { "el servidor anterior no filtra categorias" }
            categories.values.filter { !ServerTime.parse(it.updatedAt)!!.isBefore(since) }
        }
        return page(visible, page, this.pageSize ?: pageSize)
    }

    override suspend fun getDeletedNotes(since: String): DeletedIds? = deleted("getDeletedNotes", noteTombstones, since)

    override suspend fun getDeletedCategories(since: String): DeletedIds? =
        deleted("getDeletedCategories", categoryTombstones, since)

    private fun deleted(name: String, from: List<Pair<Long, String>>, since: String): DeletedIds? {
        call(name)
        if (!newServer) return null
        val start = ServerTime.parse(since)!!
        val until = stamp()
        return DeletedIds(from.filter { !ServerTime.parse(it.second)!!.isBefore(start) }.map { it.first }, until)
    }

    /** Borrado definitivo desde la web: deja tombstone en el servidor nuevo. */
    fun hardDeleteNote(id: Long) {
        notes.remove(id)
        noteTombstones += id to stamp()
    }

    fun hardDeleteCategory(id: Long) {
        categories.remove(id)
        categoryTombstones += id to stamp()
        notes.replaceAll { _, note -> note.copy(categories = note.categories.filter { it.id != id }) }
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
        createdCategoryBodies += bodies
        return bodies.map { body ->
            val id = nextId++
            categories[id] = RemoteCategory(id, body.name, body.description, body.color, stamp())
            id
        }
    }

    override suspend fun updateCategories(bodies: List<UpdateCategoryBody>): List<Long>? {
        call("updateCategories")
        if (newServer) {
            return bodies.filter { it.id in categories }.onEach { updateCategoryRow(it) }.map { it.id }
        }
        if (bodies.any { it.id !in categories }) throw ApiException(404)
        bodies.forEach { updateCategoryRow(it) }
        return null
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
        remoteIds.forEach { if (categories.remove(it) != null) categoryTombstones += it to stamp() }
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

    override suspend fun updateNotes(bodies: List<UpdateNoteBody>): List<Long>? {
        call("updateNotes")
        if (newServer) {
            return bodies.filter { it.id in notes }.onEach { updateNoteRow(it) }.map { it.id }
        }
        if (bodies.any { it.id !in notes }) throw ApiException(404)
        bodies.forEach { updateNoteRow(it) }
        return null
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
        remoteIds.filter { notes[it]?.deletedAt != null }.forEach {
            notes.remove(it)
            noteTombstones += it to stamp()
        }
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
    /** Cursor de notas por usuario (el de la version 1). */
    val cursors = mutableMapOf<Long, String?>()
    val otherCursors = mutableMapOf<Pair<Long, SyncCursor>, String?>()
    var owner: Long? = null
    private val last = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    override suspend fun cursor(userId: Long, kind: SyncCursor): String? =
        if (kind == SyncCursor.NOTES) cursors[userId] else otherCursors[userId to kind]

    override suspend fun setCursor(userId: Long, kind: SyncCursor, value: String?) {
        if (kind == SyncCursor.NOTES) cursors[userId] = value else otherCursors[userId to kind] = value
    }

    override suspend fun dataOwner(): Long? = owner
    override suspend fun setDataOwner(userId: Long) {
        owner = userId
    }

    override val lastSyncAt: kotlinx.coroutines.flow.Flow<Long?> = last
    override suspend fun setLastSyncAt(epochMillis: Long) {
        last.value = epochMillis
    }
}
