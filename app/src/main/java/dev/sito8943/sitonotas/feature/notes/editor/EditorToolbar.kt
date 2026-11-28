package dev.sito8943.sitonotas.feature.notes.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import dev.sito8943.sitonotas.ui.elements.FaIcon
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.NotesTextField
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesDialog
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Barra de formato sobre el teclado, con los botones del `EditorToolbar` de la
 * web (negrita, cursiva, tachado, encabezado, listas, tarea, codigo, enlace) y
 * el cambio a Markdown. En modo Markdown solo queda ese cambio.
 */
@Composable
fun EditorToolbar(
    state: RichEditorState?,
    markdownMode: Boolean,
    onToggleMarkdown: () -> Unit,
    modifier: Modifier = Modifier,
    /** Imagen desde la galeria; nulo = sin boton de imagen. */
    onPickImage: (() -> Unit)? = null,
    /** Imagen desde el portapapeles. */
    onPasteImage: (() -> Unit)? = null,
    /** Hay una subida en curso: el boton espera. */
    imageBusy: Boolean = false,
) {
    val colors = NotesTheme.colors
    var linking by rememberSaveable { mutableStateOf(false) }
    var imageMenu by remember { mutableStateOf(false) }
    val block = state?.focusedBlock
    val inline = block?.supportsInline == true
    val marks = state?.activeMarks().orEmpty()

    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing(1)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!markdownMode && state != null) {
                    ToolButton(R.drawable.fa_bold, R.string.editor_bold, MarkType.BOLD in marks, inline) {
                        state.toggleMark(MarkType.BOLD)
                    }
                    ToolButton(R.drawable.fa_italic, R.string.editor_italic, MarkType.ITALIC in marks, inline) {
                        state.toggleMark(MarkType.ITALIC)
                    }
                    ToolButton(R.drawable.fa_strikethrough, R.string.editor_strike, MarkType.STRIKE in marks, inline) {
                        state.toggleMark(MarkType.STRIKE)
                    }
                    ToolButton(
                        R.drawable.fa_heading,
                        R.string.editor_heading,
                        block?.type == BlockType.HEADING,
                        inline,
                    ) { state.cycleHeading() }
                    ToolButton(R.drawable.fa_list_ul, R.string.editor_bullet_list, block?.type == BlockType.BULLET, inline) {
                        state.setBlockType(BlockType.BULLET)
                    }
                    ToolButton(R.drawable.fa_list_ol, R.string.editor_ordered_list, block?.type == BlockType.ORDERED, inline) {
                        state.setBlockType(BlockType.ORDERED)
                    }
                    ToolButton(R.drawable.fa_square_check, R.string.editor_task_list, block?.type == BlockType.TASK, inline) {
                        state.setBlockType(BlockType.TASK)
                    }
                    ToolButton(R.drawable.fa_code, R.string.editor_code, MarkType.CODE in marks, inline) {
                        state.toggleMark(MarkType.CODE)
                    }
                    ToolButton(R.drawable.fa_link, R.string.editor_link, MarkType.LINK in marks, inline) {
                        linking = true
                    }
                } else {
                    NotesText(
                        stringResource(R.string.editor_markdown_hint),
                        style = NotesTheme.typography.caption,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = spacing(3)),
                        maxLines = 1,
                    )
                }
            }
            if (onPickImage != null) {
                // Como pegar o soltar una imagen en la web: galeria o portapapeles.
                Box {
                    ToolButton(R.drawable.fa_image, R.string.editor_image, active = imageMenu, enabled = !imageBusy) {
                        imageMenu = true
                    }
                    DropdownMenu(
                        expanded = imageMenu,
                        onDismissRequest = { imageMenu = false },
                        containerColor = colors.surface,
                    ) {
                        DropdownMenuItem(
                            text = { NotesText(stringResource(R.string.editor_image_gallery)) },
                            onClick = {
                                imageMenu = false
                                onPickImage()
                            },
                        )
                        if (onPasteImage != null) {
                            DropdownMenuItem(
                                text = { NotesText(stringResource(R.string.editor_image_paste)) },
                                onClick = {
                                    imageMenu = false
                                    onPasteImage()
                                },
                            )
                        }
                    }
                }
            }
            Box(Modifier.width(1.dp).height(24.dp).background(colors.border))
            ToolButton(
                R.drawable.fa_markdown,
                if (markdownMode) R.string.editor_rich_mode else R.string.editor_markdown_mode,
                active = markdownMode,
                enabled = true,
                onClick = onToggleMarkdown,
            )
        }
    }

    if (linking && state != null) {
        LinkDialog(
            onDismiss = { linking = false },
            onConfirm = { url ->
                linking = false
                state.insertLink(url)
            },
        )
    }
}

/**
 * Boton de la barra. No puede tomar el foco: si lo tomara, el campo que se
 * esta editando lo perderia y el teclado se cerraria al dar formato.
 */
@Composable
private fun ToolButton(
    @DrawableRes icon: Int,
    label: Int,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = NotesTheme.colors
    val description = stringResource(label)
    Box(
        modifier = Modifier
            .size(48.dp)
            .focusProperties { canFocus = false }
            .clip(CircleShape)
            .background(if (active) colors.accent.copy(alpha = ACTIVE_ALPHA) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics {
                selected = active
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> colors.textMuted.copy(alpha = DISABLED_ALPHA)
                active -> colors.accent
                else -> colors.textMuted
            },
            size = 18.dp,
        )
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("https://") }
    val valid = url.trim().let { it.length > "https://".length && it.none(Char::isWhitespace) }
    NotesDialog(
        onDismissRequest = onDismiss,
        title = { NotesText(stringResource(R.string.editor_link), style = NotesTheme.typography.title) },
        text = {
            NotesTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.editor_link_url),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            )
        },
        confirmButton = {
            NotesButton(
                text = stringResource(R.string.editor_link_insert),
                onClick = { onConfirm(url.trim()) },
                enabled = valid,
            )
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

private const val ACTIVE_ALPHA = 0.12f
private const val DISABLED_ALPHA = 0.4f
