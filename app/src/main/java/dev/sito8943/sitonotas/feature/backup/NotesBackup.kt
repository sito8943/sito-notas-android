package dev.sito8943.sitonotas.feature.backup

import androidx.room.withTransaction
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.data.entity.CategoryEntity
import dev.sito8943.sitonotas.core.data.entity.NoteEntity
import dev.sito8943.sitonotas.core.model.CategoryNames
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

/**
 * Formato de `GET /notes/export` del servidor (`ExportNoteDTO`): la web no
 * tiene importar/exportar, asi que el formato es el del backend, y un archivo
 * de aqui se puede subir tal cual a `POST /notes/import` (con `notes` como
 * `items`) y `POST /categories/import`.
 */
@Serializable
data class ExportFile(
    val categories: List<ExportCategory>? = null,
    val notes: List<ExportNote>? = null,
)

/** `CategoryDTO` (sin `user`, que el servidor ignora al importar). */
@Serializable
data class ExportCategory(
    val id: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    val updatedAt: String? = null,
)

/** `CommonCategoryDTO` dentro de una nota. */
@Serializable
data class ExportCategoryRef(
    val id: Long? = null,
    val name: String? = null,
    val color: String? = null,
)

/** `NoteDTO`. `id` es el del servidor (falta si la nota aun no se subio). */
@Serializable
data class ExportNote(
    val id: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val favorite: Boolean = false,
    val position: Int? = null,
    val categories: List<ExportCategoryRef> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class ExportSummary(val notes: Int, val categories: Int)

data class ImportSummary(val notesCreated: Int, val notesSkipped: Int, val categoriesCreated: Int)

/** El archivo no es JSON o no tiene la forma de una exportacion. */
class InvalidBackupException(cause: Throwable? = null) : Exception("Invalid notes import file", cause)

/**
 * Importar y exportar en local (Room), sin pasar por el servidor: funciona
 * sin cuenta y sin red, y lo importado se sube con la sincronizacion normal.
 *
 * Importar sigue las reglas de `NoteService.importNotes` con `override=false`:
 * - una nota que ya existe (mismo titulo y `createdAt`) se salta;
 * - `favorite`, `position` e `id` del archivo se ignoran;
 * - categorias por nombre (sin mayusculas ni tildes, como el servidor); las
 *   que faltan se crean (el servidor exige importarlas antes).
 */
class NotesBackup(
    private val database: NotesDatabase,
    private val clock: VersionClock,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Notas activas y todas las categorias, como JSON. */
    suspend fun export(): Pair<String, ExportSummary> {
        val notes = database.notes().observeActive().first()
        val categories = database.categories().getActive()
        val file = ExportFile(
            categories = categories.map {
                ExportCategory(
                    id = it.remoteId,
                    name = it.name,
                    description = it.description,
                    color = it.color,
                    updatedAt = format(it.updatedAt),
                )
            },
            notes = notes.map { row ->
                ExportNote(
                    id = row.note.remoteId,
                    title = row.note.title,
                    content = row.note.content,
                    favorite = row.note.favorite,
                    position = row.note.position,
                    categories = row.categories.filter { !it.purged }.map {
                        ExportCategoryRef(id = it.remoteId, name = it.name, color = it.color)
                    },
                    createdAt = format(row.note.createdAt),
                    updatedAt = format(row.note.updatedAt),
                )
            },
        )
        return json.encodeToString(ExportFile.serializer(), file) to
            ExportSummary(notes = notes.size, categories = categories.size)
    }

    /** Acepta la exportacion (`{categories, notes}`) o un array de `NoteDTO`. */
    suspend fun import(text: String): ImportSummary {
        val file = parse(text)
        return database.withTransaction {
            val byKey = database.categories().getActive().associateByTo(HashMap()) { CategoryNames.key(it.name) }
            var categoriesCreated = 0

            suspend fun resolve(name: String?, description: String?, color: String?): Long? {
                val normalized = name?.let(CategoryNames::normalize)?.takeIf { it.isNotEmpty() } ?: return null
                byKey[CategoryNames.key(normalized)]?.let { return it.id }
                val entity = CategoryEntity(
                    name = normalized,
                    description = description?.trim()?.ifBlank { null },
                    color = normalizeColor(color)
                        ?: CategoryNames.pickColor(byKey.values.map { it.color }),
                    updatedAt = now(),
                    dirty = true,
                    localVersion = clock.next(),
                )
                val id = database.categories().insert(entity)
                byKey[CategoryNames.key(normalized)] = entity.copy(id = id)
                categoriesCreated++
                return id
            }

            file.categories.orEmpty().forEach { resolve(it.name, it.description, it.color) }

            val existing = database.notes().getAll()
                .filter { it.deletedAt == null && !it.purged }
                .mapTo(HashSet()) { it.title to it.createdAt.truncatedToSeconds() }
            var created = 0
            var skipped = 0
            file.notes.orEmpty().forEach { note ->
                val title = note.title.orEmpty()
                val createdAt = parse(note.createdAt)
                if (createdAt != null && (title to createdAt.truncatedToSeconds()) in existing) {
                    skipped++
                    return@forEach
                }
                val categoryIds = note.categories.mapNotNull { resolve(it.name, null, it.color) }.distinct()
                val timestamp = now()
                val id = database.notes().insert(
                    NoteEntity(
                        title = title,
                        content = note.content.orEmpty(),
                        createdAt = createdAt ?: timestamp,
                        updatedAt = timestamp,
                        dirty = true,
                        localVersion = clock.next(),
                    ),
                )
                if (categoryIds.isNotEmpty()) database.notes().replaceCategories(id, categoryIds)
                createdAt?.let { existing += title to it.truncatedToSeconds() }
                created++
            }
            ImportSummary(created, skipped, categoriesCreated)
        }
    }

    private fun parse(text: String): ExportFile = try {
        when (val root: JsonElement = json.parseToJsonElement(text)) {
            is JsonObject -> json.decodeFromJsonElement<ExportFile>(root).also {
                if (it.notes == null && it.categories == null) throw InvalidBackupException()
            }
            is JsonArray -> ExportFile(notes = json.decodeFromJsonElement<List<ExportNote>>(root))
            else -> throw InvalidBackupException()
        }
    } catch (error: SerializationException) {
        throw InvalidBackupException(error)
    } catch (error: IllegalArgumentException) {
        throw InvalidBackupException(error)
    }

    private fun Long.truncatedToSeconds(): Long = this / 1000

    companion object {
        /** `LocalDateTime` sin zona, en UTC, como lo escribe el servidor. */
        fun format(epochMillis: Long): String =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC).format(SERVER_FORMAT)

        private val SERVER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun parse(value: String?): Long? = value?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDateTime.parse(it.removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }

        /** Como el servidor: `#RGB`, `#RGBA`, `#RRGGBB` o `#RRGGBBAA`, en mayusculas; si no, nulo. */
        fun normalizeColor(color: String?): String? {
            val hex = color?.trim()?.removePrefix("#") ?: return null
            if (hex.length !in setOf(3, 4, 6, 8) || hex.any { it.digitToIntOrNull(16) == null }) return null
            return "#" + hex.uppercase()
        }
    }
}
