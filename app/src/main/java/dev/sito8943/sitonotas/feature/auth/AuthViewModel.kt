package dev.sito8943.sitonotas.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sito8943.sitonotas.core.auth.SessionRepository
import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.NetworkException
import dev.sito8943.sitonotas.feature.auth.data.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val api: AuthApi,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        it.copy(
            email = value.trim(),
            error = null,
            resetSent = false,
            fieldErrors = it.fieldErrors.copy(email = false),
        )
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, error = null, fieldErrors = it.fieldErrors.copy(password = false))
    }

    fun onConfirmPasswordChange(value: String) = _state.update {
        it.copy(confirmPassword = value, error = null, fieldErrors = it.fieldErrors.copy(confirmPassword = false))
    }

    /** Alterna entre entrar y crear cuenta conservando el correo. */
    fun toggleMode() = _state.update {
        AuthUiState(
            email = it.email,
            mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
        )
    }

    fun submit() = when (_state.value.mode) {
        AuthMode.SIGN_IN -> signIn()
        AuthMode.SIGN_UP -> signUp()
    }

    /**
     * Registro en sito-auth, como `AuthSignUpView` (wallet): correo, contraseña
     * y repeticion. El `AuthDto` del registro no trae refresh token, asi que se
     * inicia sesion a continuacion con `rememberMe` para que la sesion dure.
     */
    fun signUp() {
        val current = _state.value
        val errors = AuthFieldErrors(
            email = !current.email.isValidEmail(),
            password = current.password.isEmpty(),
            passwordTooShort = current.password.isNotEmpty() && current.password.length < MIN_PASSWORD_LENGTH,
            confirmPassword = current.confirmPassword != current.password,
        )
        if (errors.any) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null, resetSent = false) }
        viewModelScope.launch {
            try {
                api.signUp(current.email, current.password)
                session.onAuthenticated(api.signIn(current.email, current.password))
            } catch (error: Throwable) {
                _state.update { it.copy(loading = false, error = error.toSignUpError()) }
            }
        }
    }

    fun signIn() {
        val current = _state.value
        val errors = AuthFieldErrors(
            email = !current.email.isValidEmail(),
            password = current.password.isEmpty(),
        )
        if (errors.any) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null, resetSent = false) }
        viewModelScope.launch {
            try {
                session.onAuthenticated(api.signIn(current.email, current.password))
                // `loading` sigue encendido: la navegacion se lleva la pantalla.
            } catch (error: Throwable) {
                _state.update { it.copy(loading = false, error = error.toAuthError()) }
            }
        }
    }

    /** Pide a sito-auth el correo de recuperacion para el email escrito. */
    fun forgotPassword() {
        val current = _state.value
        if (!current.email.isValidEmail()) {
            _state.update { it.copy(fieldErrors = it.fieldErrors.copy(email = true)) }
            return
        }
        _state.update { it.copy(loading = true, error = null, resetSent = false) }
        viewModelScope.launch {
            try {
                api.forgotPassword(current.email)
                _state.update { it.copy(loading = false, resetSent = true) }
            } catch (error: Throwable) {
                _state.update { it.copy(loading = false, error = error.toAuthError()) }
            }
        }
    }
}

internal fun Throwable.toAuthError(): AuthError = when (this) {
    is NetworkException -> AuthError.NETWORK
    is ApiException -> when (status) {
        400 -> AuthError.INVALID_INPUT
        401 -> AuthError.INVALID_CREDENTIALS
        404 -> AuthError.ACCOUNT_NOT_FOUND
        429 -> AuthError.TOO_MANY_ATTEMPTS
        else -> AuthError.UNKNOWN
    }
    else -> AuthError.UNKNOWN
}

/** Errores de `POST /auth/sign-up` (cuerpos en texto plano). */
internal fun Throwable.toSignUpError(): AuthError = when {
    this is ApiException && status == 409 -> AuthError.EMAIL_TAKEN
    this is ApiException && status == 400 && body?.contains(SIGN_UP_DISABLED) == true ->
        AuthError.SIGN_UP_DISABLED
    else -> toAuthError()
}

private const val SIGN_UP_DISABLED = "auth.register.featureDisabled"

/** Validacion laxa a proposito: quien decide de verdad es el backend. */
private fun String.isValidEmail(): Boolean =
    isNotBlank() && contains('@') && substringAfter('@').contains('.')
