package dev.sito8943.sitonotas.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java,
    )

    @Test
    fun `1 to 2 keeps every row and gives each one its own client id`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, remote_id, name, description, color, updated_at, dirty, purged, local_version) " +
                    "VALUES (1, 10, 'Trabajo', NULL, '#3B82F6', 100, 0, 0, 1)",
            )
            for (id in 1..3) {
                db.execSQL(
                    "INSERT INTO notes (id, remote_id, title, content, favorite, position, created_at, updated_at, " +
                        "deleted_at, remote_deleted, dirty, purged, local_version) " +
                        "VALUES ($id, ${if (id == 1) "NULL" else id * 100}, 'Nota $id', 'texto', 0, NULL, 1, 2, NULL, 0, 1, 0, $id)",
                )
            }
            db.execSQL("INSERT INTO note_categories (note_id, category_id) VALUES (1, 1)")
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true, NotesDatabase.MIGRATION_1_2)

        val noteIds = mutableListOf<String>()
        db.query("SELECT title, client_id FROM notes ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) noteIds += cursor.getString(1)
            assertEquals(3, cursor.count)
        }
        noteIds.forEach { assertTrue(it, UUID_V4.matches(it)) }
        assertEquals(3, noteIds.toSet().size)

        db.query("SELECT client_id FROM categories").use { cursor ->
            cursor.moveToFirst()
            assertTrue(UUID_V4.matches(cursor.getString(0)))
            assertNotEquals(noteIds.first(), cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM note_categories").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test"
        val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
