package dev.sito8943.sitonotas.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.sito8943.sitonotas.core.data.dao.CategoryDao
import dev.sito8943.sitonotas.core.data.dao.NoteDao
import dev.sito8943.sitonotas.core.data.dao.SyncDao
import dev.sito8943.sitonotas.core.data.entity.CategoryEntity
import dev.sito8943.sitonotas.core.data.entity.NoteCategoryCrossRef
import dev.sito8943.sitonotas.core.data.entity.NoteEntity

@Database(
    entities = [
        CategoryEntity::class,
        NoteEntity::class,
        NoteCategoryCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun notes(): NoteDao
    abstract fun sync(): SyncDao

    companion object {
        private const val NAME = "sito-notas.db"

        fun build(context: Context): NotesDatabase =
            Room.databaseBuilder(context.applicationContext, NotesDatabase::class.java, NAME)
                // Sin fallbackToDestructiveMigration a proposito: las notas sin
                // sincronizar solo viven aqui. Cada cambio de esquema lleva su
                // Migration escrita a mano y su test.
                .addMigrations(MIGRATION_1_2)
                .build()

        /** UUID v4 en SQL, distinto por fila. */
        private const val SQL_UUID =
            "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "lower(hex(randomblob(6)))"

        /** 1 -> 2: `client_id` en notas y categorias, rellenado para las filas existentes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("notes", "categories")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `client_id` TEXT")
                    db.execSQL("UPDATE `$table` SET `client_id` = $SQL_UUID WHERE `client_id` IS NULL")
                }
            }
        }
    }
}
