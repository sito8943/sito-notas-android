package dev.sito8943.sitonotas.feature.sharing.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.notes.editor.EditorToolbar
import dev.sito8943.sitonotas.feature.notes.editor.MarkdownDocument
import dev.sito8943.sitonotas.feature.notes.editor.RichEditorState
import dev.sito8943.sitonotas.feature.notes.editor.RichNoteEditor
import dev.sito8943.sitonotas.feature.sharing.SharedNoteUiState
import dev.sito8943.sitonotas.feature.sharing.SharedNoteViewModel
import dev.sito8943.sitonotas.feature.sharing.SharedSaveStatus
import dev.sito8943.sitonotas.feature.sharing.messageRes
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.formatNoteDate
import dev.sito8943.sitonotas.ui.foundations.BorderWidth
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * A note shared with the user (web `Note` page for a shared note): owner and
 * permission under the title, no tags, favorite or trash. Read only unless
 * the share is EDIT; then title and content save online as you type.
 */
@Composable
fun SharedNoteScreen(
    viewModel: SharedNoteViewModel,
    onBack: () -> Unit,
    onOpenLocal: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors

    LaunchedEffect(state.openLocalId) {
        state.openLocalId?.let(onOpenLocal)
    }

    val leave = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = leave)

    val loaded = !state.loading && state.loadError == null
    var markdownMode by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var richEpoch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(loaded, state.canEdit) {
        if (loaded && state.canEdit && markdownMode == null) {
            markdownMode = MarkdownDocument.hasRawContent(MarkdownDocument.parse(state.content))
        }
    }
    val rich = if (loaded && state.canEdit && markdownMode == false) {
        remember(richEpoch) { RichEditorState(state.content, viewModel::onContentChange) }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        SharingHeader(
            title = state.title.ifBlank { stringResource(R.string.note_untitled) }.takeIf { loaded }.orEmpty(),
            onBack = leave,
        )
        when {
            state.loading -> Box(Modifier.weight(1f))
            state.loadError != null -> SharingErrorState(state.loadError!!, onRetry = viewModel::load)
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing(4))
                        .padding(top = spacing(6), bottom = spacing(10)),
                    verticalArrangement = Arrangement.spacedBy(spacing(6)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing(2))) {
                        SharedTitle(state, viewModel::onTitleChange)
                        SharedMeta(state, onRetry = viewModel::retrySave)
                    }
                    when {
                        !state.canEdit -> ReadOnlyMarkdown(state.content)
                        rich != null -> RichNoteEditor(
                            state = rich,
                            placeholder = stringResource(R.string.editor_content_placeholder),
                        )
                        else -> SourceField(state.content, viewModel::onContentChange)
                    }
                }
                if (state.canEdit) {
                    EditorToolbar(
                        state = rich,
                        markdownMode = markdownMode == true,
                        onToggleMarkdown = {
                            if (markdownMode == true) richEpoch++
                            markdownMode = markdownMode != true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTitle(state: SharedNoteUiState, onTitleChange: (String) -> Unit) {
    val colors = NotesTheme.colors
    val style = NotesTheme.typography.display.copy(fontSize = 30.sp, lineHeight = 38.sp, color = colors.textStrong)
    if (!state.canEdit) {
        NotesText(state.title.ifBlank { stringResource(R.string.note_untitled) }, style = style)
        return
    }
    BasicTextField(
        value = state.title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box {
                if (state.title.isEmpty()) {
                    NotesText(stringResource(R.string.editor_title_placeholder), style = style.copy(color = colors.textSubtle))
                }
                inner()
            }
        },
    )
}

/** "De Ana · Puedes editar · Guardado" (web `NoteMeta`). A failed save is tappable to retry. */
@Composable
private fun SharedMeta(state: SharedNoteUiState, onRetry: () -> Unit) {
    val colors = NotesTheme.colors
    val parts = buildList {
        state.owner?.let { add(stringResource(R.string.shared_from, it.displayName)) }
        if (state.owner != null) {
            add(stringResource(if (state.canEdit) R.string.shared_can_edit else R.string.shared_read_only))
        }
        state.updatedAt?.let { add(stringResource(R.string.public_note_updated, formatNoteDate(it))) }
        when (state.saveStatus) {
            SharedSaveStatus.SAVING -> add(stringResource(R.string.shared_saving))
            SharedSaveStatus.SAVED -> add(stringResource(R.string.shared_saved))
            else -> Unit
        }
    }
    NotesText(parts.joinToString(" · "), style = NotesTheme.typography.small, color = colors.textMuted)
    if (state.saveStatus == SharedSaveStatus.FAILED) {
        val reason = state.saveError?.let { stringResource(it.messageRes()) }.orEmpty()
        NotesText(
            "${stringResource(R.string.shared_not_saved)}: $reason ${stringResource(R.string.sharing_retry)} ↻",
            style = NotesTheme.typography.small,
            color = colors.negative,
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}

@Composable
private fun SourceField(content: String, onContentChange: (String) -> Unit) {
    val colors = NotesTheme.colors
    BasicTextField(
        value = content,
        onValueChange = onContentChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp)
            .clip(Radius.lgShape)
            .background(colors.surface)
            .border(BorderWidth.thin, colors.border, Radius.lgShape)
            .padding(spacing(4)),
        textStyle = NotesTheme.typography.body.copy(
            color = colors.textStrong,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
    )
}
