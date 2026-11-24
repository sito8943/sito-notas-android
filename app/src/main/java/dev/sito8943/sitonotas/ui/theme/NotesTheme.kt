package dev.sito8943.sitonotas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Equivalente de `ThemeProvider.tsx`. Expone la paleta y la tipografia por
 * CompositionLocal: los composables leen `NotesTheme.colors.textStrong` igual
 * que en RN leian `useThemedStyles`.
 *
 * No se usa MaterialTheme como fuente de color a proposito: el sistema de
 * diseno es propio y semantico, y mapearlo a los roles de Material
 * (primary/onPrimary/surfaceVariant...) perderia significado. Aun asi se
 * provee un ColorScheme derivado como red de seguridad: los componentes de
 * Material que pintan su propia superficie (el popup de ExposedDropdownMenu,
 * dialogos, snackbars) no leen nuestro CompositionLocal, y sin esto caen al
 * esquema claro por defecto -> superficie blanca con nuestro texto claro.
 */
object NotesTheme {
    val colors: NotesColors
        @Composable @ReadOnlyComposable get() = LocalNotesColors.current

    val typography: NotesTypography
        @Composable @ReadOnlyComposable get() = LocalNotesTypography.current

    /**
     * Claro u oscuro ya resuelto. Lo necesitan los componentes con paleta propia
     * de dominio (la tarjeta de cuenta, que tiene colores de marca por banco y
     * una variante para cada tema).
     */
    val resolved: ResolvedTheme
        @Composable @ReadOnlyComposable get() = LocalResolvedTheme.current
}

val LocalResolvedTheme = staticCompositionLocalOf { ResolvedTheme.LIGHT }

val LocalNotesColors = staticCompositionLocalOf<NotesColors> {
    error("NotesColors no disponible: falta envolver el arbol en NotesTheme { }")
}

val LocalNotesTypography = staticCompositionLocalOf { NotesTypography() }

@Composable
fun NotesTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    typography: NotesTypography = NotesTypography(),
    content: @Composable () -> Unit,
) {
    val resolved = preference.resolve()
    val colors = when (resolved) {
        ResolvedTheme.LIGHT -> lightColors
        ResolvedTheme.DARK -> darkColors
    }

    CompositionLocalProvider(
        LocalNotesColors provides colors,
        LocalNotesTypography provides typography,
        LocalResolvedTheme provides resolved,
    ) {
        MaterialTheme(colorScheme = colors.toColorScheme(resolved), content = content)
    }
}

/**
 * Traduce la paleta semantica a los roles de Material que usan los componentes
 * de la libreria. Solo cubre los roles que pintan superficie o texto sobre
 * ella; el resto queda con el default del esquema del tema resuelto.
 */
private fun NotesColors.toColorScheme(resolved: ResolvedTheme): ColorScheme {
    val base = if (resolved == ResolvedTheme.DARK) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = primary,
        onPrimary = textInverted,
        secondary = accent,
        onSecondary = textInverted,
        background = background,
        onBackground = textStrong,
        surface = surface,
        onSurface = textStrong,
        surfaceVariant = background,
        onSurfaceVariant = textMuted,
        // Los menus (DropdownMenu / ExposedDropdownMenu) usan surfaceContainer.
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = border,
        outlineVariant = border,
        error = negative,
        onError = textInverted,
        scrim = overlay,
    )
}

/** SYSTEM se resuelve contra la configuracion del dispositivo. */
@Composable
private fun ThemePreference.resolve(): ResolvedTheme = when (this) {
    ThemePreference.LIGHT -> ResolvedTheme.LIGHT
    ThemePreference.DARK -> ResolvedTheme.DARK
    ThemePreference.SYSTEM ->
        if (isSystemInDarkTheme()) ResolvedTheme.DARK else ResolvedTheme.LIGHT
}
