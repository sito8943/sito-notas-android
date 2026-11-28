package dev.sito8943.sitonotas.feature.notes.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.sito8943.sitonotas.core.network.ImageLoader
import dev.sito8943.sitonotas.feature.notes.images.ImageUploadError
import dev.sito8943.sitonotas.feature.notes.images.ImageUploadProgress
import dev.sito8943.sitonotas.ui.elements.formatRelativeNoteDate
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import dev.sito8943.sitonotas.feature.notes.editor.EditorToolbar
import dev.sito8943.sitonotas.feature.notes.editor.MarkdownDocument
import dev.sito8943.sitonotas.feature.notes.editor.RichEditorState
import dev.sito8943.sitonotas.feature.notes.editor.RichNoteEditor
import dev.sito8943.sitonotas.ui.foundations.BorderWidth
import dev.sito8943.sitonotas.ui.foundations.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.categories.Category
import dev.sito8943.sitonotas.feature.notes.NoteEditorViewModel
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.CategoryChip
import dev.sito8943.sitonotas.ui.elements.ColorDot
import dev.sito8943.sitonotas.ui.elements.DashedChip
import dev.sito8943.sitonotas.ui.elements.FaIcon
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesIconButton
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.patterns.NotesConfirmationDialog
import dev.sito8943.sitonotas.ui.patterns.NotesDialog
import dev.sito8943.sitonotas.ui.templates.EmptyState
import dev.sito8943.sitonotas.ui.theme.NotesTheme

@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
    imageLoader: ImageLoader? = null,
    /** Opens sharing for the note (local id; null while a new note is unsaved). */
    onShare: ((Long?) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = NotesTheme.colors
    var pickingCategories by rememberSaveable { mutableStateOf(false) }
    var confirmTrash by rememberSaveable { mutableStateOf(false) }
    val loaded = !state.loading && !state.missing

    // Se edita con formato, como en la web. Una nota con algo que el editor
    // solo sabe mostrar como Markdown (tablas, HTML) se abre ya en
    // Markdown. Nulo = aun no se decidio (la nota no ha cargado).
    var markdownMode by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var richEpoch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(loaded) {
        if (loaded && markdownMode == null) {
            markdownMode = MarkdownDocument.hasRawContent(MarkdownDocument.parse(state.content))
        }
    }
    val rich = if (loaded && markdownMode == false) {
        // Se crea desde el contenido actual al cargar y cada vez que se vuelve
        // de Markdown; despues el editor es la fuente y solo avisa de cambios.
        remember(richEpoch) { RichEditorState(state.content, viewModel::onContentChange) }
    } else {
        null
    }

    val leave = {
        viewModel.flush()
        onBack()
    }

    // Imagenes: galeria (selector del sistema, sin permisos) o portapapeles.
    val context = LocalContext.current
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_IMAGES),
    ) { uris -> viewModel.uploadImages(uris) }
    val pasteImage = {
        val uris = clipboardImageUris(context)
        if (uris.isEmpty()) viewModel.reportImageError(ImageUploadError.NO_CLIPBOARD_IMAGE) else viewModel.uploadImages(uris)
    }
    // Lo subido entra donde estaba el cursor (o al final del Markdown).
    LaunchedEffect(state.uploadedImages, rich, markdownMode) {
        val uploaded = state.uploadedImages
        if (uploaded.isEmpty() || !loaded || markdownMode == null) return@LaunchedEffect
        val urls = uploaded.map { it.url }
        if (rich != null) {
            rich.insertImages(urls)
        } else {
            viewModel.onContentChange(appendImageLines(state.content, urls))
        }
        viewModel.onImagesInserted(uploaded.mapTo(mutableSetOf()) { it.key })
    }
    BackHandler(onBack = leave)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        EditorHeader(
            title = state.title,
            showActions = loaded,
            favorite = state.favorite,
            onBack = leave,
            onToggleFavorite = viewModel::toggleFavorite,
            onTrash = { confirmTrash = true },
            onShare = onShare?.let { share ->
                {
                    viewModel.flush()
                    share(viewModel.state.value.noteId)
                }
            },
        )

        when {
            state.loading -> Box(Modifier.weight(1f))
            state.missing -> EmptyState(stringResource(R.string.editor_missing), icon = R.drawable.fa_note_sticky_regular)
            else -> {
                EditorBody(
                    title = state.title,
                    content = state.content,
                    rich = rich,
                    updatedAt = state.updatedAt,
                    imageLoader = imageLoader,
                    categories = state.selectedCategories,
                    onTitleChange = viewModel::onTitleChange,
                    onContentChange = viewModel::onContentChange,
                    onAddCategory = { pickingCategories = true },
                    onRemoveCategory = { id -> viewModel.setCategories(state.categoryIds - id) },
                    modifier = Modifier.weight(1f),
                )
                ImageUploadStatus(
                    progress = state.imageUpload,
                    error = state.imageError,
                    onDismissError = viewModel::dismissImageError,
                )
                EditorToolbar(
                    state = rich,
                    onPickImage = if (imageLoader != null) {
                        { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    } else {
                        null
                    },
                    onPasteImage = pasteImage,
                    imageBusy = state.imageUpload != null,
                    markdownMode = markdownMode == true,
                    onToggleMarkdown = {
                        if (markdownMode == true) richEpoch++
                        markdownMode = markdownMode != true
                    },
                )
            }
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

/**
 * Barra superior de la nota, como `NoteHeader` de la web: volver, miga
 * "Notas › titulo" y las acciones, con una linea debajo.
 */
@Composable
private fun EditorHeader(
    title: String,
    showActions: Boolean,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing(2)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotesText(
                    stringResource(R.string.notes_title),
                    style = NotesTheme.typography.small,
                    color = colors.textMuted,
                    maxLines = 1,
                )
                FaIcon(R.drawable.fa_chevron_right, contentDescription = null, size = 10.dp)
                NotesText(
                    title.ifBlank { stringResource(R.string.note_untitled) },
                    style = NotesTheme.typography.small,
                    color = colors.textStrong,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (showActions) {
                NotesIconButton(
                    icon = if (favorite) R.drawable.fa_heart else R.drawable.fa_heart_regular,
                    contentDescription = stringResource(
                        if (favorite) R.string.note_unfavorite else R.string.note_favorite,
                    ),
                    onClick = onToggleFavorite,
                    tint = if (favorite) colors.accent else colors.textMuted,
                )
                if (onShare != null) {
                    NotesIconButton(
                        icon = R.drawable.fa_share_nodes,
                        contentDescription = stringResource(R.string.share_action),
                        onClick = onShare,
                    )
                }
                NotesIconButton(
                    icon = R.drawable.fa_trash,
                    contentDescription = stringResource(R.string.note_trash),
                    onClick = onTrash,
                )
            }
        }
        HorizontalDivider(color = colors.border)
    }
}

@Composable
private fun EditorBody(
    title: String,
    content: String,
    rich: RichEditorState?,
    updatedAt: Long?,
    imageLoader: ImageLoader?,
    categories: List<Category>,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onRemoveCategory: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NotesTheme.colors
    val typography = NotesTheme.typography
    val titleStyle = typography.display.copy(fontSize = 30.sp, lineHeight = 38.sp, color = colors.textStrong)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing(4))
            .padding(top = spacing(6)),
        verticalArrangement = Arrangement.spacedBy(spacing(6)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing(3))) {
            PlainField(
                value = title,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.editor_title_placeholder),
                style = titleStyle,
                singleLine = true,
                onNext = rich?.let { editor -> { editor.blocks.firstOrNull()?.let { editor.focus(it.id, 0) } } },
            )

            if (updatedAt != null) UpdatedLabel(updatedAt)

            // Como `NoteTags`: chips con X para quitar y el chip discontinuo
            // para añadir.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                categories.forEach { category ->
                    CategoryChip(
                        name = category.name,
                        color = category.color,
                        onRemove = { onRemoveCategory(category.id) },
                        removeLabel = stringResource(R.string.editor_remove_category, category.name),
                    )
                }
                DashedChip(
                    icon = R.drawable.fa_plus,
                    label = stringResource(R.string.editor_add_category),
                    contentDescription = stringResource(R.string.editor_categories),
                    onClick = onAddCategory,
                )
            }
        }

        if (rich != null) {
            RichNoteEditor(
                state = rich,
                placeholder = stringResource(R.string.editor_content_placeholder),
                imageLoader = imageLoader,
            )
        } else {
            MarkdownSource(content = content, onContentChange = onContentChange)
        }
    }
}

/**
 * El Markdown tal cual, como `.note-source` en la web: monoespaciada, en una
 * tarjeta con borde. Avisa si la nota tiene algo que el editor con formato
 * no sabe mostrar.
 */
@Composable
private fun MarkdownSource(content: String, onContentChange: (String) -> Unit) {
    val colors = NotesTheme.colors
    val hasRaw = remember(content) { MarkdownDocument.hasRawContent(MarkdownDocument.parse(content)) }
    Column(
        modifier = Modifier.padding(bottom = spacing(10)),
        verticalArrangement = Arrangement.spacedBy(spacing(3)),
    ) {
        if (hasRaw) {
            NotesText(
                stringResource(R.string.editor_raw_notice),
                style = NotesTheme.typography.small,
                color = colors.textMuted,
            )
        }
        val style = NotesTheme.typography.body.copy(
            color = colors.textStrong,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
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
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            // Sin autocorreccion: el teclado "arreglaba" la sintaxis (espacios
            // tras el punto de una URL, mayusculas en `.png`).
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
        )
    }
}

/**
 * "Editado hace 5 minutos", como `NoteMeta` de la web. Se recalcula cada
 * minuto y al cambiar la fecha.
 */
@Composable
private fun UpdatedLabel(updatedAt: Long) {
    val now by produceState(System.currentTimeMillis(), updatedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(UPDATED_TICK_MILLIS)
        }
    }
    NotesText(
        stringResource(R.string.editor_updated, formatRelativeNoteDate(updatedAt, now)),
        style = NotesTheme.typography.small,
        color = NotesTheme.colors.textMuted,
        maxLines = 1,
    )
}

/** Barra de subida (progreso) o del ultimo error, sobre la de formato. */
@Composable
private fun ImageUploadStatus(progress: ImageUploadProgress?, error: ImageUploadError?, onDismissError: () -> Unit) {
    val colors = NotesTheme.colors
    if (progress == null && error == null) return
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.border)
        if (progress != null) {
            val percent = progress.progress?.let { (it * 100).toInt() }
            NotesText(
                if (percent == null) {
                    stringResource(R.string.editor_image_uploading, progress.index, progress.count)
                } else {
                    stringResource(R.string.editor_image_uploading_percent, progress.index, progress.count, percent)
                },
                style = NotesTheme.typography.small,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = spacing(4), vertical = spacing(2)),
            )
            val fraction = progress.progress
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.border)
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
        } else if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = spacing(4)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotesText(
                    stringResource(error.message()),
                    style = NotesTheme.typography.small,
                    color = colors.negative,
                    modifier = Modifier.weight(1f).padding(vertical = spacing(2)),
                )
                NotesIconButton(
                    icon = R.drawable.fa_xmark,
                    contentDescription = stringResource(R.string.common_back),
                    onClick = onDismissError,
                )
            }
        }
    }
}

private fun ImageUploadError.message(): Int = when (this) {
    ImageUploadError.NEEDS_ACCOUNT -> R.string.editor_image_error_account
    ImageUploadError.NOT_SYNCED -> R.string.editor_image_error_not_synced
    ImageUploadError.OFFLINE -> R.string.editor_image_error_offline
    ImageUploadError.INVALID_TYPE -> R.string.editor_image_error_invalid_type
    ImageUploadError.TOO_LARGE -> R.string.editor_image_error_too_large
    ImageUploadError.UNREADABLE -> R.string.editor_image_error_unreadable
    ImageUploadError.NO_CLIPBOARD_IMAGE -> R.string.editor_image_error_no_clipboard
    ImageUploadError.NOT_FOUND -> R.string.editor_image_error_not_found
    ImageUploadError.SERVER -> R.string.editor_image_error_server
}

/** Imagenes del portapapeles (las copiadas desde otra app o una captura). */
private fun clipboardImageUris(context: Context): List<Uri> {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return emptyList()
    val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return emptyList()
    val describedAsImage = clip.description.hasMimeType("image/*")
    return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.filter { uri ->
        describedAsImage || runCatching { context.contentResolver.getType(uri) }.getOrNull()?.startsWith("image/") == true
    }
}

/** En modo Markdown la imagen va al final, una por linea (`toMarkdownImage` de la web). */
internal fun appendImageLines(content: String, urls: List<String>): String {
    val lines = urls.joinToString("\n") { MarkdownDocument.imageLine(it) }
    return when {
        content.isEmpty() -> lines
        content.endsWith("\n") -> content + lines
        else -> content + "\n" + lines
    }
}

private const val MAX_PICKED_IMAGES = 10
private const val UPDATED_TICK_MILLIS = 60_000L

/** Campo sin borde: el editor es la pagina, no un formulario. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
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
            imeAction = if (onNext != null) ImeAction.Next else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
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
