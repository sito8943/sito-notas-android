package dev.sito8943.sitonotas.ui.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Buscador de la lista, como `SearchWrapper` en la web: pastilla sobre la
 * superficie, sin borde, lupa a la izquierda. La X para borrar es de Android
 * (en la web lo hace el `type="search"` del navegador).
 */
@Composable
fun NotesSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = NotesTheme.colors
    val style = NotesTheme.typography.body.copy(color = colors.textStrong)
    val focus = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(Radius.fullShape)
            .background(colors.surface)
            .padding(start = spacing(4), end = spacing(1)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing(3)),
    ) {
        FaIcon(
            R.drawable.fa_magnifying_glass,
            contentDescription = null,
            tint = colors.textStrong.copy(alpha = 0.7f),
            size = 16.dp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = spacing(3))
                .semantics { contentDescription = placeholder },
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        NotesText(placeholder, style = style.copy(color = colors.textMuted), maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            NotesIconButton(
                icon = R.drawable.fa_xmark,
                contentDescription = clearLabel,
                onClick = { onValueChange("") },
            )
        } else {
            Box(Modifier.padding(end = spacing(3)))
        }
    }
}
