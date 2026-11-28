package dev.sito8943.sitonotas.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.BuildConfig
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.settings.AppLanguage
import dev.sito8943.sitonotas.core.sync.SyncStatus
import dev.sito8943.sitonotas.feature.settings.BackupStatus
import dev.sito8943.sitonotas.feature.settings.PasswordError
import dev.sito8943.sitonotas.feature.settings.PasswordFormState
import dev.sito8943.sitonotas.feature.settings.SettingsUiState
import dev.sito8943.sitonotas.feature.settings.SettingsViewModel
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesPasswordField
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
    val password by viewModel.password.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val analyticsAllowed by viewModel.analyticsAllowed.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    // Selector del sistema (SAF): sin permisos de almacenamiento.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

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
                Section(stringResource(R.string.settings_security)) {
                    PasswordForm(
                        form = password,
                        onCurrentChange = viewModel::onCurrentPasswordChange,
                        onNewChange = viewModel::onNewPasswordChange,
                        onRepeatChange = viewModel::onRepeatPasswordChange,
                        onSubmit = viewModel::changePassword,
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

            Section(stringResource(R.string.settings_backup)) {
                NotesText(
                    stringResource(R.string.settings_backup_helper),
                    style = NotesTheme.typography.caption,
                    color = NotesTheme.colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing(2))) {
                    NotesButton(
                        text = stringResource(R.string.settings_export),
                        onClick = { exportLauncher.launch(backupFileName()) },
                        loading = backupStatus is BackupStatus.Working,
                    )
                    NotesButton(
                        text = stringResource(R.string.settings_import),
                        onClick = { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "text/plain", "application/octet-stream")) },
                        variant = ButtonVariant.OUTLINED,
                        color = ButtonColor.PRIMARY,
                        enabled = backupStatus !is BackupStatus.Working,
                    )
                }
                backupStatusText(backupStatus)?.let { (text, failed) ->
                    NotesText(
                        text,
                        style = NotesTheme.typography.caption,
                        color = if (failed) NotesTheme.colors.negative else NotesTheme.colors.positive,
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

            // `PrivacySettings` de la web: indecisa se muestra como "No permitidas".
            Section(stringResource(R.string.settings_privacy)) {
                NotesText(stringResource(R.string.settings_privacy_label))
                Choice(
                    label = stringResource(R.string.settings_privacy_allowed),
                    selected = analyticsAllowed,
                    onClick = { viewModel.setAnalytics(true) },
                )
                Choice(
                    label = stringResource(R.string.settings_privacy_denied),
                    selected = !analyticsAllowed,
                    onClick = { viewModel.setAnalytics(false) },
                )
                NotesText(
                    stringResource(R.string.settings_privacy_helper),
                    style = NotesTheme.typography.caption,
                    color = NotesTheme.colors.textMuted,
                )
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
private fun PasswordForm(
    form: PasswordFormState,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onRepeatChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val enabled = !form.loading && !form.done
    NotesPasswordField(
        value = form.current,
        onValueChange = onCurrentChange,
        label = stringResource(R.string.settings_current_password),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        imeAction = ImeAction.Next,
        error = when (form.error) {
            PasswordError.CURRENT_REQUIRED -> stringResource(R.string.auth_error_password_required)
            PasswordError.WRONG_CURRENT -> stringResource(R.string.settings_password_wrong_current)
            else -> null
        },
    )
    NotesPasswordField(
        value = form.new,
        onValueChange = onNewChange,
        label = stringResource(R.string.settings_new_password),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        imeAction = ImeAction.Next,
        contentType = ContentType.NewPassword,
        error = stringResource(R.string.auth_error_password_too_short)
            .takeIf { form.error == PasswordError.TOO_SHORT },
    )
    NotesPasswordField(
        value = form.repeat,
        onValueChange = onRepeatChange,
        label = stringResource(R.string.settings_repeat_password),
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        contentType = ContentType.NewPassword,
        error = stringResource(R.string.auth_error_passwords_differ)
            .takeIf { form.error == PasswordError.MISMATCH },
    )
    when {
        form.done -> NotesText(
            stringResource(R.string.settings_password_updated),
            style = NotesTheme.typography.caption,
            color = NotesTheme.colors.positive,
        )
        form.error == PasswordError.NETWORK -> NotesText(
            stringResource(R.string.auth_error_network),
            style = NotesTheme.typography.caption,
            color = NotesTheme.colors.negative,
        )
        form.error == PasswordError.OTHER -> NotesText(
            stringResource(R.string.settings_password_failed, form.errorDetail.orEmpty()),
            style = NotesTheme.typography.caption,
            color = NotesTheme.colors.negative,
        )
    }
    NotesButton(
        text = stringResource(R.string.settings_change_password),
        onClick = onSubmit,
        loading = form.loading,
        enabled = enabled && form.current.isNotEmpty() && form.new.isNotEmpty() && form.repeat.isNotEmpty(),
    )
}

/** Texto del resultado y si es un fallo. */
@Composable
private fun backupStatusText(status: BackupStatus?): Pair<String, Boolean>? = when (status) {
    null, BackupStatus.Working -> null
    is BackupStatus.Exported ->
        stringResource(R.string.settings_export_done, status.summary.notes, status.summary.categories) to false
    is BackupStatus.Imported -> stringResource(
        R.string.settings_import_done,
        status.summary.notesCreated,
        status.summary.categoriesCreated,
        status.summary.notesSkipped,
    ) to false
    BackupStatus.Invalid -> stringResource(R.string.settings_import_invalid) to true
    is BackupStatus.Failed -> stringResource(R.string.settings_backup_failed, status.message) to true
}

private const val BACKUP_MIME_TYPE = "application/json"

/** `sito-notas-AAAA-MM-DD.json`. */
private fun backupFileName(): String = "sito-notas-${java.time.LocalDate.now()}.json"

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
    is SyncStatus.AccountSwitch -> stringResource(R.string.settings_sync_account_switch)
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
