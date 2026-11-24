package dev.sito8943.sitonotas.ui.patterns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Portado de `Dialog/ConfirmationDialog`. Mientras la accion esta en vuelo se
 * ignoran los cierres por fuera y por atras, igual que alli: cerrarlo a medias
 * dejaria al usuario sin saber si el borrado ocurrio.
 */
@Composable
fun NotesConfirmationDialog(
    open: Boolean,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    loading: Boolean = false,
) {
    if (!open) return

    val colors = NotesTheme.colors

    NotesDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { NotesText(text = title, style = NotesTheme.typography.title) },
        text = message?.let {
            {
                NotesText(
                    text = it,
                    style = NotesTheme.typography.body,
                    color = colors.textMuted,
                )
            }
        },
        dismissButton = {
            NotesButton(
                text = cancelLabel ?: stringResource(R.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.OUTLINED,
                enabled = !loading,
            )
        },
        confirmButton = {
            NotesButton(
                text = confirmLabel ?: stringResource(R.string.common_confirm),
                onClick = onConfirm,
                color = ButtonColor.DANGER,
                loading = loading,
            )
        },
    )
}
