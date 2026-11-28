package dev.sito8943.sitonotas.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.sync.SyncCoordinator
import dev.sito8943.sitonotas.core.sync.SyncStatus
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.patterns.NotesDialog
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Entro otra cuenta con cambios sin subir de la anterior: subirlos a esta o
 * descartarlos. No se cierra tocando fuera: descartar tiene que ser a proposito.
 */
@Composable
fun AccountSwitchDialog(coordinator: SyncCoordinator) {
    val status by coordinator.status.collectAsStateWithLifecycle()
    val pending = (status as? SyncStatus.AccountSwitch)?.pending ?: return

    NotesDialog(
        onDismissRequest = {},
        title = {
            NotesText(text = stringResource(R.string.account_switch_title), style = NotesTheme.typography.title)
        },
        text = {
            NotesText(
                text = pluralStringResource(R.plurals.account_switch_message, pending, pending),
                style = NotesTheme.typography.body,
                color = NotesTheme.colors.textMuted,
            )
        },
        dismissButton = {
            NotesButton(
                text = stringResource(R.string.account_switch_discard),
                onClick = { coordinator.resolveAccountSwitch(upload = false) },
                variant = ButtonVariant.OUTLINED,
                color = ButtonColor.DANGER,
            )
        },
        confirmButton = {
            NotesButton(
                text = stringResource(R.string.account_switch_upload),
                onClick = { coordinator.resolveAccountSwitch(upload = true) },
            )
        },
    )
}
