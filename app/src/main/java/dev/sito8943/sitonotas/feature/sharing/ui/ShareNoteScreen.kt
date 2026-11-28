package dev.sito8943.sitonotas.feature.sharing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.BuildConfig
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.sharing.ShareAvailability
import dev.sito8943.sitonotas.feature.sharing.ShareLinks
import dev.sito8943.sitonotas.feature.sharing.ShareNoteUiState
import dev.sito8943.sitonotas.feature.sharing.ShareNoteViewModel
import dev.sito8943.sitonotas.feature.sharing.ShareNotice
import dev.sito8943.sitonotas.feature.sharing.SharingError
import dev.sito8943.sitonotas.feature.sharing.messageRes
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.NotesTextField
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Web `ShareNoteDialog` as a screen: email + permission + "Compartir",
 * people with access, general access (public link), copy link and send a
 * copy with the share sheet.
 */
@Composable
fun ShareNoteScreen(viewModel: ShareNoteViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = NotesTheme.colors
    BackHandler(onBack = onBack)

    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            context.toast(it.messageRes())
            viewModel.consumeActionError()
        }
    }
    LaunchedEffect(state.notice) {
        if (state.notice == ShareNotice.LINK_TURNED_OFF) {
            context.toast(R.string.share_link_turned_off)
            viewModel.consumeNotice()
        }
    }

    val title = state.title.ifBlank { stringResource(R.string.note_untitled) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        SharingHeader(title = stringResource(R.string.share_title, title), onBack = onBack)
        when (val availability = state.availability) {
            ShareAvailability.Loading -> Box(Modifier.weight(1f))
            ShareAvailability.NoAccount -> SharingErrorState(
                error = SharingError.UNKNOWN,
                onRetry = null,
                icon = R.drawable.fa_user,
                text = stringResource(R.string.share_no_account),
            )
            ShareAvailability.NotSynced -> SharingErrorState(
                error = SharingError.UNKNOWN,
                onRetry = null,
                icon = R.drawable.fa_cloud_arrow_up,
                text = stringResource(R.string.share_not_synced),
            )
            is ShareAvailability.Ready -> ShareBody(
                state = state,
                remoteId = availability.remoteId,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ShareBody(
    state: ShareNoteUiState,
    remoteId: Long,
    viewModel: ShareNoteViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = NotesTheme.colors
    val typography = NotesTheme.typography
    val title = state.title.ifBlank { stringResource(R.string.note_untitled) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(spacing(4)),
        verticalArrangement = Arrangement.spacedBy(spacing(5)),
    ) {
        // Add a person
        Column(verticalArrangement = Arrangement.spacedBy(spacing(2))) {
            NotesTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.share_email_label),
                error = state.addError?.let { stringResource(it.messageRes()) },
                enabled = !state.adding,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                placeholder = stringResource(R.string.share_email_placeholder),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PermissionSelect(
                    value = state.permission,
                    onChange = viewModel::onPermissionChange,
                    contentDescription = stringResource(R.string.share_permission_label),
                    modifier = Modifier.weight(1f),
                )
                NotesButton(
                    text = stringResource(R.string.share_submit),
                    onClick = viewModel::add,
                    color = ButtonColor.PRIMARY,
                    loading = state.adding,
                    enabled = state.canSubmit,
                    icon = R.drawable.fa_user_plus,
                )
            }
            if (state.addError == null) {
                NotesText(stringResource(R.string.share_hint), style = typography.small, color = colors.textMuted)
            }
        }

        // People with access
        Column(verticalArrangement = Arrangement.spacedBy(spacing(2))) {
            NotesText(stringResource(R.string.share_people), style = typography.bodyStrong, color = colors.textStrong)
            when {
                state.sharesLoading && state.shares.isEmpty() -> Box(Modifier.size(1.dp))
                state.sharesError != null -> SharingErrorState(state.sharesError, onRetry = viewModel::retry)
                state.shares.isEmpty() -> NotesText(stringResource(R.string.share_only_you), color = colors.textMuted)
                else -> state.shares.forEach { share ->
                    val name = share.displayName
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colors.accent.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            NotesText(name.take(1).uppercase(), style = typography.bodyStrong, color = colors.accent)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = spacing(3)),
                        ) {
                            NotesText(name, style = typography.small, color = colors.textStrong, maxLines = 1)
                            if (!share.username.isNullOrBlank()) {
                                NotesText(share.email, style = typography.caption, color = colors.textMuted, maxLines = 1)
                            }
                        }
                        PermissionSelect(
                            value = share.permission,
                            onChange = { viewModel.changePermission(share.userId, it) },
                            contentDescription = stringResource(R.string.share_permission_for, name),
                        )
                        NotesIconButton(
                            icon = R.drawable.fa_xmark,
                            contentDescription = stringResource(R.string.share_remove, name),
                            onClick = { viewModel.remove(share.userId) },
                            size = IconButtonSize.SM,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.border)

        // General access (public link)
        Column(verticalArrangement = Arrangement.spacedBy(spacing(2))) {
            NotesText(stringResource(R.string.share_link_title), style = typography.bodyStrong, color = colors.textStrong)
            val public = state.linkToken != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaIcon(
                    if (public) R.drawable.fa_earth_americas else R.drawable.fa_lock,
                    contentDescription = null,
                    tint = if (public) colors.accent else colors.textMuted,
                    size = 18.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing(2)),
                ) {
                    OptionSelect(
                        value = public,
                        options = listOf(
                            false to stringResource(R.string.share_link_restricted),
                            true to stringResource(R.string.share_link_public),
                        ),
                        onChange = viewModel::setPublic,
                        contentDescription = stringResource(R.string.share_link_label),
                        enabled = !state.linkBusy && !state.linkLoading,
                    )
                    NotesText(
                        stringResource(if (public) R.string.share_link_public_hint else R.string.share_link_restricted_hint),
                        style = typography.small,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = spacing(2)),
                    )
                }
            }
        }

        // Footer: copy link (public link, else the note's app URL, like the web),
        // send the link and send a copy through the share sheet.
        val link = state.linkToken?.let { ShareLinks.publicUrl(it, BuildConfig.SHARE_BASE_URL, BuildConfig.API_BASE_URL) }
            ?: ShareLinks.noteAppUrl(remoteId, BuildConfig.WEB_APP_URL)
        Column(verticalArrangement = Arrangement.spacedBy(spacing(2))) {
            NotesButton(
                text = stringResource(R.string.share_link_copy),
                onClick = { context.copyToClipboard(link, R.string.share_link_copied) },
                variant = ButtonVariant.OUTLINED,
                color = ButtonColor.PRIMARY,
                enabled = !state.linkLoading,
                icon = R.drawable.fa_link,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.linkToken != null) {
                NotesButton(
                    text = stringResource(R.string.share_link_send),
                    onClick = { context.shareText(link, title) },
                    variant = ButtonVariant.OUTLINED,
                    color = ButtonColor.PRIMARY,
                    icon = R.drawable.fa_share_nodes,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NotesButton(
                text = stringResource(R.string.share_send_copy),
                onClick = { context.shareText(state.content, title) },
                variant = ButtonVariant.OUTLINED,
                color = ButtonColor.PRIMARY,
                icon = R.drawable.fa_share_nodes,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
