package dev.sito8943.sitonotas.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sito8943.sitonotas.core.data.NotesDatabase

fun inMemoryDatabase(): NotesDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext(),
    NotesDatabase::class.java,
).allowMainThreadQueries().build()
