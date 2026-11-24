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
        else -> AuthError.UNKNOWN
    }
    else -> AuthError.UNKNOWN
}

/** Validacion laxa a proposito: quien decide de verdad es el backend. */
private fun String.isValidEmail(): Boolean =
    isNotBlank() && contains('@') && substringAfter('@').contains('.')
