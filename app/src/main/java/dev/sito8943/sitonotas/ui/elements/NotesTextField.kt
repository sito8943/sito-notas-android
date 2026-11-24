package dev.sito8943.sitonotas.ui.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.sito8943.sitonotas.R
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dev.sito8943.sitonotas.ui.foundations.Radius
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme

/**
 * Portado del elemento `TextField`. El mensaje de error va debajo y ocupa sitio
 * solo cuando existe, para que el formulario no salte al aparecer.
 */
@Composable
fun NotesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    contentType: ContentType? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** Ejemplo dentro del campo vacio. */
    placeholder: String? = null,
    /** Texto libre (titulos, descripciones): mayuscula inicial y autocorreccion. */
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    autoCorrect: Boolean = false,
    minLines: Int = if (singleLine) 1 else MULTILINE_MIN_LINES,
) {
    val colors = NotesTheme.colors

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // Deja que el gestor de contraseñas de Android ofrezca guardar
                // y autocompletar. Nunca se implementa un almacen propio.
                .then(
                    if (contentType != null) {
                        Modifier.semantics { this.contentType = contentType }
                    } else {
                        Modifier
                    },
                ),
            enabled = enabled,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            shape = Radius.mdShape,
            label = { NotesText(label, style = NotesTheme.typography.caption) },
            placeholder = placeholder?.let { { NotesText(it, color = colors.textSubtle) } },
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = capitalization,
                autoCorrectEnabled = autoCorrect,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textStrong,
                unfocusedTextColor = colors.textStrong,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                errorBorderColor = colors.negative,
                focusedLabelColor = colors.textMuted,
                unfocusedLabelColor = colors.textSubtle,
                errorLabelColor = colors.negative,
                cursorColor = colors.accent,
            ),
        )

        if (error != null) {
            NotesText(
                text = error,
                modifier = Modifier.padding(top = spacing(1), start = spacing(1)),
                style = NotesTheme.typography.caption,
                color = colors.negative,
            )
        }
    }
}

/** Campo de contraseña con alternancia de visibilidad. */
@Composable
fun NotesPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    contentType: ContentType = ContentType.Password,
) {
    var visible by remember { mutableStateOf(false) }
    val colors = NotesTheme.colors

    NotesTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        error = error,
        enabled = enabled,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        contentType = contentType,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.password_hide else R.string.password_show,
                    ),
                    tint = colors.textSubtle,
                )
            }
        },
    )
}

private const val MULTILINE_MIN_LINES = 3
