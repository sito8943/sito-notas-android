package dev.sito8943.sitonotas.feature.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.auth.AuthMode
import dev.sito8943.sitonotas.feature.auth.AuthUiState
import dev.sito8943.sitonotas.ui.elements.ButtonColor
import dev.sito8943.sitonotas.ui.elements.ButtonVariant
import dev.sito8943.sitonotas.ui.elements.NotesButton
import dev.sito8943.sitonotas.ui.elements.NotesPasswordField
import dev.sito8943.sitonotas.ui.elements.NotesText
import dev.sito8943.sitonotas.ui.elements.NotesTextField
import dev.sito8943.sitonotas.ui.foundations.spacing
import dev.sito8943.sitonotas.ui.theme.NotesTheme
import dev.sito8943.sitonotas.ui.theme.ThemePreference

/**
 * Pantalla de login y registro de sito-auth. Sin estado propio: recibe el
 * `AuthUiState` y emite callbacks. El registro pide correo, contraseña y su
 * repeticion, como `AuthSignUpView` de la web.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmPasswordChange: (String) -> Unit = {},
    onToggleMode: () -> Unit = {},
) {
    val colors = NotesTheme.colors
    val signUp = state.mode == AuthMode.SIGN_UP

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing(5), vertical = spacing(6)),
        verticalArrangement = Arrangement.spacedBy(spacing(4)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing(2)),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_glyph),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(spacing(16)),
            )
            NotesText(
                text = stringResource(R.string.brand_name),
                style = NotesTheme.typography.display,
                color = colors.accent,
            )
        }

        NotesText(
            text = stringResource(if (signUp) R.string.auth_sign_up_title else R.string.auth_sign_in_title),
            style = NotesTheme.typography.title,
            modifier = Modifier.padding(top = spacing(4)),
        )
        NotesText(
            text = stringResource(if (signUp) R.string.auth_sign_up_subtitle else R.string.auth_sign_in_subtitle),
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = spacing(2)),
        )

        NotesTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            error = stringResource(R.string.auth_error_email_invalid)
                .takeIf { state.fieldErrors.email },
            keyboardType = KeyboardType.Email,
            contentType = ContentType.EmailAddress,
        )

        NotesPasswordField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.auth_password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            error = when {
                state.fieldErrors.password -> stringResource(R.string.auth_error_password_required)
                state.fieldErrors.passwordTooShort -> stringResource(R.string.auth_error_password_too_short)
                else -> null
            },
            imeAction = if (signUp) ImeAction.Next else ImeAction.Done,
        )

        if (signUp) {
            NotesPasswordField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = stringResource(R.string.auth_confirm_password),
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
                error = stringResource(R.string.auth_error_passwords_differ)
                    .takeIf { state.fieldErrors.confirmPassword },
                imeAction = ImeAction.Done,
            )
        }

        state.error?.let { error ->
            NotesText(
                text = stringResource(error.messageRes()),
                style = NotesTheme.typography.caption,
                color = colors.negative,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.resetSent) {
            NotesText(
                text = stringResource(R.string.auth_reset_sent),
                style = NotesTheme.typography.caption,
                color = colors.positive,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        NotesButton(
            text = stringResource(if (signUp) R.string.auth_sign_up else R.string.auth_sign_in),
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing(2)),
            loading = state.loading,
            enabled = state.canSubmitSignIn,
        )

        if (!signUp) {
            NotesButton(
                text = stringResource(R.string.auth_forgot_password),
                onClick = onForgotPassword,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.TEXT,
                color = ButtonColor.PRIMARY,
                enabled = !state.loading,
            )
        }

        NotesButton(
            text = stringResource(if (signUp) R.string.auth_to_sign_in else R.string.auth_to_sign_up),
            onClick = onToggleMode,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.TEXT,
            color = ButtonColor.PRIMARY,
            enabled = !state.loading,
        )

        // Local-first: la app funciona sin cuenta; la cuenta solo sincroniza.
        NotesButton(
            text = stringResource(R.string.auth_continue_without_account),
            onClick = onContinueWithoutAccount,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.OUTLINED,
            enabled = !state.loading,
        )
    }
}

@Preview(name = "Entrar", showBackground = true)
@Composable
private fun SignInPreview() {
    NotesTheme(preference = ThemePreference.LIGHT) {
        AuthScreen(
            state = AuthUiState(email = "ana@example.com", password = "secreto"),
            onEmailChange = {}, onPasswordChange = {}, onSubmit = {},
            onForgotPassword = {}, onContinueWithoutAccount = {},
        )
    }
}
