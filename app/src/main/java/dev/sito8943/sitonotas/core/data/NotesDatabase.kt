package dev.sito8943.sitonotas.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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
                .build()
    }
}
