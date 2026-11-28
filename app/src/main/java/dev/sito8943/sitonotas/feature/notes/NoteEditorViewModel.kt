package dev.sito8943.sitonotas.feature.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.NetworkException
import dev.sito8943.sitonotas.feature.categories.CategoryRepository
import dev.sito8943.sitonotas.feature.notes.images.ImagePrepareException
import dev.sito8943.sitonotas.feature.notes.images.ImageUploadError
import dev.sito8943.sitonotas.feature.notes.images.ImageUploadProgress
import dev.sito8943.sitonotas.feature.notes.images.NoteImageSupport
import dev.sito8943.sitonotas.feature.notes.images.UploadedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class NoteEditorUiState(
    val loading: Boolean = true,
    val noteId: Long? = null,
    val title: String = "",
    val content: String = "",
    val favorite: Boolean = false,
    val categoryIds: Set<Long> = emptySet(),
    val allCategories: List<Category> = emptyList(),
    val preview: Boolean = false,
    /** La nota se borro mientras estaba abierta (otro dispositivo, papelera). */
    val missing: Boolean = false,
    /** Ultima modificacion guardada ("Editado hace..."); nula si aun no existe. */
    val updatedAt: Long? = null,
    /** Subida de imagenes en curso. */
    val imageUpload: ImageUploadProgress? = null,
    /** Imagenes subidas que el editor aun no ha insertado. */
    val uploadedImages: List<UploadedImage> = emptyList(),
    /** Ultimo fallo de subida, hasta que se descarta. */
    val imageError: ImageUploadError? = null,
    /**
     * Sube cuando el contenido cambia desde fuera (una descarga de otro
     * dispositivo): el editor enriquecido se vuelve a crear con el nuevo texto.
     */
    val externalRevision: Int = 0,
) {
    val selectedCategories: List<Category>
        get() = allCategories.filter { it.id in categoryIds }
}

/**
 * Editor con autoguardado local: cada cambio se escribe en Room tras una
 * pausa corta, y al salir se guarda lo que quede. Una nota nueva no se crea
 * hasta que tiene algo escrito (o se marca favorita / se le pone categoria).
 *
 * Las escrituras corren en [saveScope] (el del proceso) y no en el
 * `viewModelScope`: al cerrar el editor el ViewModel se destruye, y el ultimo
 * guardado no puede morir con el.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModel(
    noteId: Long?,
    private val notes: NoteRepository,
    categories: CategoryRepository,
    private val saveScope: CoroutineScope,
    private val autosaveMillis: Long = AUTOSAVE_MILLIS,
    private val images: NoteImageSupport? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(NoteEditorUiState(noteId = noteId, loading = noteId != null))
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    /** Serializa las escrituras: dos guardados a la vez crearian dos notas. */
    private val writes = Mutex()
    private var pendingSave: Job? = null
    private var uploadJob: Job? = null
    private var nextImageKey = 1L

    /** Titulo y contenido que hay en Room segun este editor (lo ultimo cargado o guardado). */
    @Volatile
    private var saved: Pair<String, String>? = null

    init {
        viewModelScope.launch {
            categories.observeCategories().collect { all ->
                _state.update { state ->
                    state.copy(
                        allCategories = all,
                        categoryIds = state.categoryIds.filterTo(mutableSetOf()) { id -> all.any { it.id == id } },
                    )
                }
            }
        }
        if (noteId != null) {
            viewModelScope.launch {
                var first = true
                notes.observeNote(noteId).collect { note ->
                    if (first) {
                        first = false
                        onLoaded(note)
                    } else if (note != null) {
                        onExternalChange(note)
                    }
                }
            }
        }
    }

    init {
        // Aparte de la carga: sigue la fecha de la nota (tambien la de una
        // nota nueva en cuanto se crea).
        viewModelScope.launch {
            _state.map { it.noteId }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else notes.observeNote(id) }
                .collect { note -> _state.update { it.copy(updatedAt = note?.updatedAt) } }
        }
    }

    private fun onLoaded(note: Note?) {
        saved = note?.let { it.title to it.content }
        _state.update {
            if (note == null) {
                it.copy(loading = false, missing = true)
            } else {
                it.copy(
                    loading = false,
                    title = note.title,
                    content = note.content,
                    favorite = note.favorite,
                    categoryIds = note.categories.map { c -> c.id }.toSet(),
                    // Abrir una nota con contenido muestra la vista previa.
                    preview = note.content.isNotBlank(),
                )
            }
        }
    }

    /**
     * La nota cambio en Room con el editor abierto. Solo se aplica si viene del
     * servidor (sin nada pendiente: no es el eco de un guardado propio) y aqui
     * no hay ediciones sin guardar; si las hay, gana lo local, que se subira.
     */
    private fun onExternalChange(note: Note) {
        if (note.pendingSync) return
        if (pendingSave?.isActive == true) return
        val current = _state.value
        if (current.loading || current.missing) return
        if (saved != current.title to current.content) return
        val categoryIds = note.categories.map { it.id }.toSet()
        val textChanged = note.title != current.title || note.content != current.content
        if (!textChanged && note.favorite == current.favorite && categoryIds == current.categoryIds) return
        saved = note.title to note.content
        _state.update {
            it.copy(
                title = note.title,
                content = note.content,
                favorite = note.favorite,
                categoryIds = categoryIds,
                externalRevision = if (textChanged) it.externalRevision + 1 else it.externalRevision,
            )
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value) }
        scheduleSave()
    }

    fun onContentChange(value: String) {
        _state.update { it.copy(content = value) }
        scheduleSave()
    }

    fun togglePreview() = _state.update { it.copy(preview = !it.preview) }

    fun toggleFavorite() {
        _state.update { it.copy(favorite = !it.favorite) }
        save(force = true) { id, s -> notes.setFavorite(id, s.favorite) }
    }

    fun setCategories(ids: Set<Long>) {
        _state.update { it.copy(categoryIds = ids) }
        save(force = true) { id, s -> notes.setCategories(id, s.categoryIds) }
    }

    /** Guarda ya lo que haya (al salir de la pantalla). */
    fun flush() {
        pendingSave?.cancel()
        save(force = false) { _, _ -> }
    }

    fun trash(onDone: () -> Unit) {
        pendingSave?.cancel()
        saveScope.launch {
            writes.withLock {
                val id = persistText(force = false)
                if (id != null) notes.trash(id)
            }
            onDone()
        }
    }

    /**
     * Sube imagenes como `useNoteImageUpload` de la web: una tras otra, y
     * cada una que llega se ofrece al editor ([NoteEditorUiState.uploadedImages]).
     * Necesita el id remoto de la nota: una nota nueva se guarda y se pide
     * una sincronizacion; si no llega a subirse, se avisa ([ImageUploadError]).
     */
    fun uploadImages(uris: List<Uri>) {
        val support = images ?: return
        if (uris.isEmpty() || uploadJob?.isActive == true) return
        uploadJob = viewModelScope.launch {
            _state.update { it.copy(imageError = null, imageUpload = ImageUploadProgress(1, uris.size, null)) }
            try {
                val error = uploadAll(support, uris)
                if (error != null) _state.update { it.copy(imageError = error) }
            } finally {
                _state.update { it.copy(imageUpload = null) }
            }
        }
    }

    fun reportImageError(error: ImageUploadError) = _state.update { it.copy(imageError = error) }

    fun dismissImageError() = _state.update { it.copy(imageError = null) }

    /** El editor ya metio estas imagenes en el texto. */
    fun onImagesInserted(keys: Set<Long>) =
        _state.update { state -> state.copy(uploadedImages = state.uploadedImages.filterNot { it.key in keys }) }

    /** @return el ultimo error, o nulo si todas subieron. */
    private suspend fun uploadAll(support: NoteImageSupport, uris: List<Uri>): ImageUploadError? {
        if (!support.isSignedIn()) return ImageUploadError.NEEDS_ACCOUNT
        val remoteId = remoteIdForUpload(support) ?: return ImageUploadError.NOT_SYNCED
        var lastError: ImageUploadError? = null
        uris.forEachIndexed { index, uri ->
            _state.update { it.copy(imageUpload = ImageUploadProgress(index + 1, uris.size, null)) }
            val error = try {
                val prepared = support.prepare(uri)
                val url = support.uploader.upload(remoteId, prepared) { progress ->
                    _state.update { it.copy(imageUpload = ImageUploadProgress(index + 1, uris.size, progress)) }
                }
                _state.update { it.copy(uploadedImages = it.uploadedImages + UploadedImage(nextImageKey++, url)) }
                null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                error.toUploadError()
            }
            if (error != null) lastError = error
            // Sin red o sin la nota en el servidor, las siguientes fallarian igual.
            if (error == ImageUploadError.OFFLINE || error == ImageUploadError.NOT_FOUND) return error
        }
        return lastError
    }

    private suspend fun remoteIdForUpload(support: NoteImageSupport): Long? {
        val id = writes.withLock { persistText(force = true) } ?: return null
        notes.observeRemoteId(id).first()?.let { return it }
        support.requestSync()
        return withTimeoutOrNull(REMOTE_ID_WAIT_MILLIS) { notes.observeRemoteId(id).filterNotNull().first() }
    }

    private fun Exception.toUploadError(): ImageUploadError = when (this) {
        is ImagePrepareException -> when (reason) {
            ImagePrepareException.Reason.INVALID_TYPE -> ImageUploadError.INVALID_TYPE
            ImagePrepareException.Reason.TOO_LARGE -> ImageUploadError.TOO_LARGE
            ImagePrepareException.Reason.UNREADABLE -> ImageUploadError.UNREADABLE
        }
        is NetworkException -> ImageUploadError.OFFLINE
        is ApiException -> when (status) {
            HTTP_NOT_FOUND, HTTP_FORBIDDEN -> ImageUploadError.NOT_FOUND
            HTTP_PAYLOAD_TOO_LARGE -> ImageUploadError.TOO_LARGE
            else -> ImageUploadError.SERVER
        }
        else -> ImageUploadError.SERVER
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(autosaveMillis)
            save(force = false) { _, _ -> }
        }
    }

    /**
     * Escribe titulo y contenido y luego [after]. Con [force] crea la nota
     * aunque este vacia: marcar favorita o poner categoria ya es contenido.
     */
    private fun save(force: Boolean, after: suspend (Long, NoteEditorUiState) -> Unit) {
        saveScope.launch {
            writes.withLock {
                val id = persistText(force) ?: return@withLock
                after(id, _state.value)
            }
        }
    }

    /** @return id de la nota, o nulo si es nueva y no hay nada que guardar. */
    private suspend fun persistText(force: Boolean): Long? {
        val current = _state.value
        if (current.loading || current.missing) return null
        val existing = current.noteId
        if (existing != null) {
            notes.updateText(existing, current.title, current.content)
            saved = current.title to current.content
            return existing
        }
        if (!force && current.title.isBlank() && current.content.isBlank()) return null
        val created = notes.create(
            title = current.title,
            content = current.content,
            favorite = current.favorite,
            categoryIds = current.categoryIds,
        )
        saved = current.title to current.content
        _state.update { it.copy(noteId = created) }
        return created
    }

    private companion object {
        const val AUTOSAVE_MILLIS = 600L

        /** Lo que se espera a que una nota nueva llegue al servidor. */
        const val REMOTE_ID_WAIT_MILLIS = 20_000L
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PAYLOAD_TOO_LARGE = 413
    }
}
