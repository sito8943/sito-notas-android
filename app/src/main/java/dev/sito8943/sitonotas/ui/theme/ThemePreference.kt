package dev.sito8943.sitonotas.ui.theme

/** Portado de `theme/constants.ts` -> THEME_PREFERENCE. */
enum class ThemePreference {
    LIGHT,
    DARK,
    SYSTEM;

    /** Portado de PREFERENCE_CYCLE: light -> dark -> system -> light. */
    fun next(): ThemePreference = when (this) {
        LIGHT -> DARK
        DARK -> SYSTEM
        SYSTEM -> LIGHT
    }

    companion object {
        /**
         * Lee lo guardado en DataStore. Nulo es "nunca se eligio" y cae en
         * [SYSTEM], igual que un nombre que no reconozcamos: si algun dia se
         * renombra un valor, la app sigue al telefono en vez de romperse.
         */
        fun fromName(name: String?): ThemePreference =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Portado de `theme/constants.ts` -> RESOLVED_THEME. */
enum class ResolvedTheme { LIGHT, DARK }
