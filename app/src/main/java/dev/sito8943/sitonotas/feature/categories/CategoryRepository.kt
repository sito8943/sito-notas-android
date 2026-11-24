package dev.sito8943.sitonotas.feature.categories

import androidx.room.withTransaction
import dev.sito8943.sitonotas.core.data.NotesDatabase
import dev.sito8943.sitonotas.core.data.VersionClock
import dev.sito8943.sitonotas.core.data.entity.CategoryEntity
import dev.sito8943.sitonotas.core.model.CategoryNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Por que no se pudo guardar una categoria. */
enum class CategoryError { NAME_REQUIRED, NAME_TAKEN, NOT_FOUND }

class CategoryException(val error: CategoryError) : Exception(error.name)

/**
 * Categorias. Cada escritura deja la fila pendiente de subir; la sube
 * `core/sync` sin que nadie tenga que avisar.
 */
class CategoryRepository(
    private val database: NotesDatabase,
    private val clock: VersionClock,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.categories()

    fun observeCategories(): Flow<List<Category>> =
        dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    /** Notas activas por id de categoria. */
    fun observeUses(): Flow<Map<Long, Int>> =
        dao.observeUses().map { rows -> rows.associate { it.categoryId to it.uses } }

    /** Color sugerido para una categoria nueva (`pickTagColor` de la web). */
    suspend fun suggestColor(): String = CategoryNames.pickColor(dao.getActive().map { it.color })

    suspend fun create(name: String, description: String?, color: String?): Long =
        database.withTransaction {
            val normalized = validName(name, exceptId = null)
            dao.insert(
                CategoryEntity(
                    name = normalized,
                    description = description?.trim()?.ifBlank { null },
                    color = color,
                    updatedAt = now(),
                    dirty = true,
                    localVersion = clock.next(),
                ),
            )
        }

    suspend fun update(id: Long, name: String, description: String?, color: String?) {
        database.withTransaction {
            val current = dao.getById(id)?.takeIf { !it.purged }
                ?: throw CategoryException(CategoryError.NOT_FOUND)
            val normalized = validName(name, exceptId = id)
            dao.update(
                current.copy(
                    name = normalized,
                    description = description?.trim()?.ifBlank { null },
                    color = color,
                    updatedAt = now(),
                    dirty = true,
                    localVersion = clock.next(),
                ),
            )
        }
    }

    /**
     * Borrado definitivo, como en la web. Las notas pierden la categoria al
     * momento; en el servidor la quita el propio `DELETE /categories/permanent`,
     * asi que las notas no quedan pendientes por esto.
     */
    suspend fun delete(id: Long) {
        database.withTransaction {
            val current = dao.getById(id) ?: return@withTransaction
            if (current.remoteId == null) {
                dao.deleteByIds(listOf(id))
            } else {
                database.notes().unlinkCategory(id)
                dao.update(current.copy(purged = true, localVersion = clock.next()))
            }
        }
    }

    private suspend fun validName(name: String, exceptId: Long?): String {
        val normalized = CategoryNames.normalize(name)
        if (normalized.isEmpty()) throw CategoryException(CategoryError.NAME_REQUIRED)
        val key = CategoryNames.key(normalized)
        val taken = dao.getActive().any { it.id != exceptId && CategoryNames.key(it.name) == key }
        if (taken) throw CategoryException(CategoryError.NAME_TAKEN)
        return normalized
    }
}
