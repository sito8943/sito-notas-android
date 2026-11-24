package dev.sito8943.sitonotas.feature.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.feature.notes.NoteEditorViewModel
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.CategoryChip
import dev.sito8943.sitonotas.ui.elements.ColorDot
import dev.sito8943.sitonotas.ui.elements.IconButtonSize
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesConfirmationDialog
import dev.sito8943.sitonotas.ui.patterns.NotesDialog
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.theme.NotesTheme

@Composable
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors
    var pickingCategories by rememberSaveable { mutableStateOf(false) }
    var confirmTrash by rememberSaveable { mutableStateOf(false) }

    val leave = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = leave)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing(1), vertical = spacing(1)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotesIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                onClick = leave,
                tint = colors.textStrong,
                size = IconButtonSize.LG,
            )
            Box(Modifier.weight(1f))
            if (!state.missing) {
                NotesIconButton(
                    icon = if (state.preview) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (state.preview) R.string.editor_edit else R.string.editor_preview,
                    ),
                    onClick = viewModel::togglePreview,
                    size = IconButtonSize.LG,
                )
                NotesIconButton(
                    icon = if (state.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (state.favorite) R.string.note_unfavorite else R.string.note_favorite,
                    ),
                    onClick = viewModel::toggleFavorite,
                    tint = if (state.favorite) colors.warning else colors.textMuted,
                    size = IconButtonSize.LG,
                )
                NotesIconButton(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    contentDescription = stringResource(R.string.editor_categories),
                    onClick = { pickingCategories = true },
                    size = IconButtonSize.LG,
                )
                NotesIconButton(
                    icon = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.note_trash),
                    onClick = { confirmTrash = true },
                    size = IconButtonSize.LG,
                )
            }
        }

        when {
            state.loading -> Unit
            state.missing -> EmptyState(stringResource(R.string.editor_missing))
            else -> EditorBody(
                title = state.title,
                content = state.content,
                preview = state.preview,
                categories = state.selectedCategories,
                onTitleChange = viewModel::onTitleChange,
                onContentChange = viewModel::onContentChange,
                onCategoriesClick = { pickingCategories = true },
            )
        }
    }

    if (pickingCategories) {
        CategoryPickerDialog(
            all = state.allCategories,
            selected = state.categoryIds,
            onDismiss = { pickingCategories = false },
            onConfirm = {
                viewModel.setCategories(it)
                pickingCategories = false
            },
        )
    }

    NotesConfirmationDialog(
        open = confirmTrash,
        title = stringResource(R.string.note_trash_confirm_title),
        message = stringResource(R.string.note_trash_confirm_message),
        confirmLabel = stringResource(R.string.note_trash),
        onDismiss = { confirmTrash = false },
        onConfirm = {
            confirmTrash = false
            viewModel.trash(onDone = {})
            onBack()
        },
    )
}

@Composable
private fun EditorBody(
    title: String,
    content: String,
    preview: Boolean,
    categories: List<Category>,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCategoriesClick: () -> Unit,
) {
    val colors = NotesTheme.colors
    val typography = NotesTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing(5)),
        verticalArrangement = Arrangement.spacedBy(spacing(3)),
    ) {
        if (preview) {
            NotesText(
                text = title.ifBlank { stringResource(R.string.note_untitled) },
                style = typography.title.copy(fontSize = 24.sp),
                color = if (title.isBlank()) colors.textMuted else colors.textStrong,
            )
        } else {
            PlainField(
                value = title,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.editor_title_placeholder),
                style = typography.title.copy(fontSize = 24.sp, color = colors.textStrong),
                singleLine = true,
            )
        }

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .clickable(onClick = onCategoriesClick),
                horizontalArrangement = Arrangement.spacedBy(spacing(2)),
            ) {
                categories.forEach { CategoryChip(it.name, it.color) }
            }
        }

        if (preview) {
            if (content.isBlank()) {
                NotesText(stringResource(R.string.editor_preview_empty), color = colors.textMuted)
            } else {
                MarkdownPreview(content, Modifier.padding(bottom = spacing(10)))
            }
        } else {
            PlainField(
                value = content,
                onValueChange = onContentChange,
                placeholder = stringResource(R.string.editor_content_placeholder),
                style = typography.body.copy(color = colors.textStrong, lineHeight = 24.sp),
                singleLine = false,
                modifier = Modifier.padding(bottom = spacing(10)),
            )
        }
    }
}

/** Campo sin borde: el editor es la pagina, no un formulario. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NotesTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrectEnabled = true,
        ),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) NotesText(placeholder, style = style.copy(color = colors.textSubtle))
                inner()
            }
        },
    )
}

@Composable
private fun CategoryPickerDialog(
    all: List<Category>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var current by remember(selected) { mutableStateOf(selected) }
    val colors = NotesTheme.colors

    NotesDialog(
        onDismissRequest = onDismiss,
        title = { NotesText(stringResource(R.string.editor_categories), style = NotesTheme.typography.title) },
        text = {
            if (all.isEmpty()) {
                NotesText(stringResource(R.string.editor_no_categories), color = colors.textMuted)
            } else {
                LazyColumn {
                    items(all, key = { it.id }) { category ->
                        val checked = category.id in current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    current = if (checked) current - category.id else current + category.id
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing(2)),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                            )
                            ColorDot(category.color)
                            NotesText(category.name, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            NotesButton(text = stringResource(R.string.common_save), onClick = { onConfirm(current) })
        },
        dismissButton = {
            NotesButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.OUTLINED,
            )
        },
    )
}
