package dev.sito8943.sitonotas.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.BuildConfig
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.settings.AppLanguage
import dev.sito8943.sitonotas.core.sync.SyncStatus
import dev.sito8943.sitonotas.feature.settings.SettingsUiState
import dev.sito8943.sitonotas.feature.settings.SettingsViewModel
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatDateTime
import dev.sito8943.sitonotas.ui.foundations.BorderWidth
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesConfirmationDialog
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme
import dev.sito8943.sitonotas.ui.theme.ThemePreference

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onSignIn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Page(title = stringResource(R.string.settings_title)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing(4))
                .padding(bottom = spacing(6)),
            verticalArrangement = Arrangement.spacedBy(spacing(6)),
        ) {
            Section(stringResource(R.string.settings_account)) {
                if (state.accountEmail == null) {
                    NotesText(stringResource(R.string.settings_no_account), color = NotesTheme.colors.textMuted)
                    NotesButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = onSignIn,
                    )
                } else {
                    NotesText(stringResource(R.string.settings_signed_in_as, state.accountEmail!!))
                    NotesButton(
                        text = stringResource(R.string.settings_sign_out),
                        onClick = { confirmSignOut = true },
                        variant = ButtonVariant.OUTLINED,
                        color = ButtonColor.PRIMARY,
                        icon = R.drawable.fa_arrow_right_from_bracket,
                    )
                }
            }

            if (state.accountEmail != null) {
                Section(stringResource(R.string.settings_sync)) {
                    NotesText(syncStatusText(state))
                    state.lastSyncAt?.let {
                        NotesText(
                            stringResource(R.string.settings_sync_last, formatDateTime(it)),
                            style = NotesTheme.typography.caption,
                            color = NotesTheme.colors.textMuted,
                        )
                    }
                    NotesText(
                        if (state.pending == 0) {
                            stringResource(R.string.settings_sync_nothing_pending)
                        } else {
                            pluralStringResource(R.plurals.settings_sync_pending, state.pending, state.pending)
                        },
                        style = NotesTheme.typography.caption,
                        color = NotesTheme.colors.textMuted,
                    )
                    NotesButton(
                        text = stringResource(R.string.settings_sync_now),
                        onClick = viewModel::syncNow,
                        icon = R.drawable.fa_rotate,
                        loading = state.syncStatus is SyncStatus.Syncing,
                    )
                }
            }

            Section(stringResource(R.string.settings_theme)) {
                ThemePreference.entries.forEach { theme ->
                    Choice(
                        label = stringResource(
                            when (theme) {
                                ThemePreference.LIGHT -> R.string.settings_theme_light
                                ThemePreference.DARK -> R.string.settings_theme_dark
                                ThemePreference.SYSTEM -> R.string.settings_theme_system
                            },
                        ),
                        selected = state.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                    )
                }
            }

            Section(stringResource(R.string.settings_language)) {
                listOf(null, AppLanguage.SPANISH, AppLanguage.ENGLISH).forEach { language ->
                    Choice(
                        label = stringResource(
                            when (language) {
                                null -> R.string.settings_language_system
                                AppLanguage.SPANISH -> R.string.settings_language_es
                                AppLanguage.ENGLISH -> R.string.settings_language_en
                            },
                        ),
                        selected = state.language == language,
                        onClick = { viewModel.setLanguage(language) },
                    )
                }
            }

            NotesText(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = NotesTheme.typography.caption,
                color = NotesTheme.colors.textSubtle,
            )
        }
    }

    NotesConfirmationDialog(
        open = confirmSignOut,
        title = stringResource(R.string.settings_sign_out),
        message = stringResource(
            if (state.pending > 0) R.string.settings_sign_out_pending else R.string.settings_sign_out_message,
        ),
        confirmLabel = stringResource(R.string.settings_sign_out),
        onDismiss = { confirmSignOut = false },
        onConfirm = {
            confirmSignOut = false
            viewModel.signOut()
        },
    )
}

@Composable
private fun syncStatusText(state: SettingsUiState): String = when (val status = state.syncStatus) {
    SyncStatus.Idle -> stringResource(R.string.settings_sync_idle)
    SyncStatus.Syncing -> stringResource(R.string.settings_sync_running)
    is SyncStatus.UpToDate -> stringResource(R.string.settings_sync_ok)
    is SyncStatus.Failed -> if (status.offline) {
        stringResource(R.string.settings_sync_offline)
    } else {
        stringResource(R.string.settings_sync_failed, status.message)
    }
}

/**
 * Seccion en tarjeta, como las de la pagina de ajustes de la web
 * (`rounded-2xl border bg-base-light p-4`, titulo `text-xl` en Poppins).
 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = Radius.xlShape,
        color = NotesTheme.colors.surface,
        border = BorderStroke(BorderWidth.thin, NotesTheme.colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing(4)),
            verticalArrangement = Arrangement.spacedBy(spacing(3)),
        ) {
            NotesText(title, style = NotesTheme.typography.title)
            content()
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = NotesTheme.colors.accent),
        )
        NotesText(label)
    }
}
