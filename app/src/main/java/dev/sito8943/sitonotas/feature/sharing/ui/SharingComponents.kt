package dev.sito8943.sitonotas.feature.sharing.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.editor.NotePreviewModel
import dev.sito8943.sitonotas.feature.notes.editor.PreviewKind
import dev.sito8943.sitonotas.feature.notes.ui.inline
import dev.sito8943.sitonotas.feature.sharing.SharingError
import dev.sito8943.sitonotas.feature.sharing.data.SharePermission
import dev.sito8943.sitonotas.feature.sharing.messageRes
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/** Top bar of the sharing screens: back, title, divider (like the editor header). */
@Composable
internal fun SharingHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val colors = NotesTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = spacing(1)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotesIconButton(
                icon = R.drawable.fa_arrow_left,
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
                tint = colors.textMuted,
            )
            NotesText(
                title,
                style = NotesTheme.typography.small,
                color = colors.textStrong,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        HorizontalDivider(color = colors.border)
    }
}

/** Explicit failure of an online screen, with a retry. */
@Composable
internal fun SharingErrorState(
    error: SharingError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.fa_link_slash,
    text: String = stringResource(error.messageRes()),
) {
    val colors = NotesTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing(6), vertical = spacing(12)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing(4)),
    ) {
        FaIcon(icon, contentDescription = null, tint = colors.textSubtle, size = 32.dp)
        NotesText(text, color = colors.textMuted, style = NotesTheme.typography.body)
        if (onRetry != null) {
            NotesButton(
                text = stringResource(R.string.sharing_retry),
                onClick = onRetry,
                variant = ButtonVariant.OUTLINED,
                icon = R.drawable.fa_rotate,
            )
        }
    }
}

@Composable
internal fun permissionLabel(permission: SharePermission): String = stringResource(
    when (permission) {
        SharePermission.READ -> R.string.share_permission_read
        SharePermission.EDIT -> R.string.share_permission_edit
    },
)

/** "Puede ver / Puede editar" select (web `SharePermissionSelect`). */
@Composable
internal fun PermissionSelect(
    value: SharePermission,
    onChange: (SharePermission) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OptionSelect(
        value = value,
        options = SharePermission.entries.map { it to permissionLabel(it) },
        onChange = onChange,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
internal fun <T> OptionSelect(
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NotesTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClickLabel = contentDescription) { open = true }
                .padding(horizontal = spacing(2), vertical = spacing(2)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing(1)),
        ) {
            NotesText(
                options.firstOrNull { it.first == value }?.second.orEmpty(),
                style = NotesTheme.typography.small,
                color = if (enabled) colors.textStrong else colors.textSubtle,
                maxLines = 1,
            )
            FaIcon(R.drawable.fa_chevron_right, contentDescription = null, size = 10.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = NotesTheme.typography.small) },
                    onClick = {
                        open = false
                        if (option != value) onChange(option)
                    },
                )
            }
        }
    }
}

/**
 * Full note, read only, with its formatting (web `NoteEditor readOnly`).
 * Built on the card preview model: images and raw HTML are left out.
 */
@Composable
internal fun ReadOnlyMarkdown(content: String, modifier: Modifier = Modifier) {
    val items = remember(content) { NotePreviewModel.build(content) }
    val colors = NotesTheme.colors
    val base = NotesTheme.typography.body.copy(color = colors.textStrong, fontWeight = FontWeight.Normal, lineHeight = 26.sp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing(2))) {
        items.forEach { item ->
            val text = remember(item.text, colors) { inline(item.text, colors) }
            when (item.kind) {
                PreviewKind.PARAGRAPH -> Text(text, style = base)
                PreviewKind.HEADING -> Text(
                    text,
                    style = base.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
                )
                PreviewKind.QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(colors.border))
                    Text(text, style = base.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 12.dp))
                }
                PreviewKind.CODE -> Text(
                    item.text,
                    style = base.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceRecessed, Radius.mdShape)
                        .padding(spacing(3)),
                )
                PreviewKind.RULE -> HorizontalDivider(color = colors.border)
                PreviewKind.BULLET, PreviewKind.ORDERED, PreviewKind.TASK -> Row(
                    modifier = Modifier.padding(start = 20.dp * item.depth.coerceAtMost(4)),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.width(26.dp).height(26.dp), contentAlignment = Alignment.CenterStart) {
                        when (item.kind) {
                            PreviewKind.TASK -> FaIcon(
                                if (item.checked) R.drawable.fa_square_check else R.drawable.fa_square_regular,
                                contentDescription = null,
                                tint = if (item.checked) colors.accent else colors.textMuted,
                                size = 15.dp,
                            )
                            PreviewKind.ORDERED -> Text(item.number, style = base, maxLines = 1)
                            else -> Text("•", style = base)
                        }
                    }
                    Text(text, style = base)
                }
            }
        }
    }
}

/** Android share sheet with plain text (web `navigator.share`). */
internal fun Context.shareText(text: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun Context.copyToClipboard(text: String, @androidx.annotation.StringRes confirmation: Int) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    // Android 13+ shows its own confirmation.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
    }
}

internal fun Context.toast(@androidx.annotation.StringRes message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
