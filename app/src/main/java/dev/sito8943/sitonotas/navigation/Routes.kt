package dev.sito8943.sitonotas.navigation

import kotlinx.serialization.Serializable

/** Rutas type-safe de Navigation Compose. */
@Serializable
data object SignInRoute

@Serializable
data object NotesRoute

@Serializable
data object CategoriesRoute

@Serializable
data object TrashRoute

@Serializable
data object SettingsRoute

/** `noteId` nulo = nota nueva. */
@Serializable
data class NoteEditorRoute(val noteId: Long? = null)

/** Compartidas conmigo (online). */
@Serializable
data object SharedRoute

/** Compartir una nota local (`noteId` nulo = nota nueva aun sin guardar). */
@Serializable
data class ShareNoteRoute(val noteId: Long? = null)

/** Nota compartida con el usuario, por su id del servidor. */
@Serializable
data class SharedNoteRoute(val remoteId: Long)

/** Nota abierta desde su enlace publico. */
@Serializable
data class PublicNoteRoute(val token: String)
