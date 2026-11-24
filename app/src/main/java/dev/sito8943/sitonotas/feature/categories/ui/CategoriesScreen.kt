package dev.sito8943.sitonotas.feature.categories.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.model.CategoryNames
import dev.sito8943.sitonotas.feature.categories.CategoriesViewModel
import dev.sito8943.sitonotas.feature.categories.CategoryError
import dev.sito8943.sitonotas.feature.categories.CategoryForm
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.ColorDot
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.NotesTextField
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.EntityAction
import dev.sito8943.sitonotas.ui.patterns.EntityCard
import dev.sito8943.sitonotas.ui.patterns.NotesConfirmationDialog
import dev.sito8943.sitonotas.ui.patterns.NotesDialog
import dev.sito8943.sitonotas.ui.patterns.NotesFab
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.templates.Page
import dev.sito8943.sitonotas.ui.theme.NotesTheme

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        Page(title = stringResource(R.string.categories_title)) {
            when {
                state.loading -> Unit
                state.categories.isEmpty() -> EmptyState(stringResource(R.string.categories_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = spacing(4), end = spacing(4), bottom = spacing(24)),
                    verticalArrangement = Arrangement.spacedBy(spacing(2)),
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        val uses = state.uses[category.id] ?: 0
                        EntityCard(
                            onClick = { viewModel.startEdit(category) },
                            actions = listOf(
                                EntityAction(
                                    icon = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.common_edit),
                                    onClick = { viewModel.startEdit(category) },
                                ),
                                EntityAction(
                                    icon = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    onClick = { viewModel.askDelete(category) },
                                ),
                            ),
                        ) {
                            ColorDot(category.color, size = 14)
                            Column(modifier = Modifier.weight(1f)) {
                                NotesText(category.name, style = NotesTheme.typography.bodyStrong, maxLines = 1)
                                category.description?.let {
                                    NotesText(it, color = colors.textMuted, maxLines = 2)
                                }
                                NotesText(
                                    pluralStringResource(R.plurals.categories_uses, uses, uses),
                                    style = NotesTheme.typography.caption,
                                    color = colors.textSubtle,
                                )
                            }
                        }
                    }
                }
            }
        }

        NotesFab(
            onClick = viewModel::startCreate,
            contentDescription = stringResource(R.string.categories_new),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing(5)),
        )
    }

    state.form?.let { form ->
        CategoryFormDialog(
            form = form,
            onNameChange = viewModel::onNameChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onColorChange = viewModel::onColorChange,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissForm,
        )
    }

    NotesConfirmationDialog(
        open = state.confirmDelete != null,
        title = stringResource(R.string.categories_delete_title),
        message = stringResource(R.string.categories_delete_message, state.confirmDelete?.name.orEmpty()),
        confirmLabel = stringResource(R.string.common_delete),
        onConfirm = viewModel::confirmDelete,
        onDismiss = viewModel::dismissDelete,
    )
}

@Composable
private fun CategoryFormDialog(
    form: CategoryForm,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NotesTheme.colors
    NotesDialog(
        onDismissRequest = { if (!form.saving) onDismiss() },
        title = {
            NotesText(
                stringResource(if (form.id == null) R.string.categories_new else R.string.categories_edit),
                style = NotesTheme.typography.title,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing(3))) {
                NotesTextField(
                    value = form.name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.categories_name),
                    error = when (form.error) {
                        CategoryError.NAME_REQUIRED -> stringResource(R.string.categories_error_name_required)
                        CategoryError.NAME_TAKEN -> stringResource(R.string.categories_error_name_taken)
                        CategoryError.NOT_FOUND -> stringResource(R.string.categories_error_not_found)
                        null -> null
                    },
                    capitalization = KeyboardCapitalization.Sentences,
                )
                NotesTextField(
                    value = form.description,
                    onValueChange = onDescriptionChange,
                    label = stringResource(R.string.categories_description),
                    singleLine = false,
                    minLines = 2,
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect = true,
                )
                NotesText(
                    stringResource(R.string.categories_color),
                    style = NotesTheme.typography.caption,
                    color = colors.textMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CategoryNames.COLORS.forEach { color ->
                        val selected = form.color.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(spacing(7))
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = colors.textStrong,
                                    shape = CircleShape,
                                )
                                .clickable { onColorChange(color) },
                            contentAlignment = Alignment.Center,
                        ) {
                            ColorDot(color, size = 20)
                        }
                    }
                }
            }
        },
        confirmButton = {
            NotesButton(text = stringResource(R.string.common_save), onClick = onSave, loading = form.saving)
        },
        dismissButton = {
            NotesButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                variant = ButtonVariant.OUTLINED,
                enabled = !form.saving,
            )
        },
    )
}
