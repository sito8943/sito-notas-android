package dev.sito8943.sitonotas.ui.patterns

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import dev.sito8943.sitonotas.core.settings.AppLocaleProvider
import dev.sito8943.sitonotas.core.settings.LocalAppLanguage
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Dialogo de la app. Todos los dialogos pasan por aqui, y no por `AlertDialog`
 * directamente, por un motivo concreto:
 *
 * un dialogo de Compose se compone en su propia ventana, y ahi `LocalContext` y
 * `LocalConfiguration` se vuelven a proveer desde el cero de la plataforma. Eso
 * tira por tierra el idioma que puso `AppLocaleProvider`, y el resultado es un
 * dialogo en el idioma del telefono dentro de una app puesta en otro. Con la app
 * en español el selector de idioma salia entero en ingles.
 *
 * Los composition locals propios si atraviesan la ventana, asi que se lee
 * [LocalAppLanguage] y se vuelve a aplicar el idioma dentro de cada hueco.
 */
@Composable
fun NotesDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val language = LocalAppLanguage.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = Radius.lgShape,
        containerColor = NotesTheme.colors.surface,
        title = { AppLocaleProvider(language, title) },
        text = text?.let { { AppLocaleProvider(language, it) } },
        confirmButton = { AppLocaleProvider(language, confirmButton) },
        dismissButton = dismissButton?.let { { AppLocaleProvider(language, it) } },
    )
}
