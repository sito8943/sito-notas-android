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
