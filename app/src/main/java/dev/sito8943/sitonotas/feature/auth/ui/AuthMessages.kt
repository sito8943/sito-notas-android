package dev.sito8943.sitonotas.feature.auth.ui

import androidx.annotation.StringRes
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.feature.auth.AuthError

/** Traduce el error de dominio a un recurso de texto. */
@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.ACCOUNT_NOT_FOUND -> R.string.auth_error_account_not_found
    AuthError.INVALID_INPUT -> R.string.auth_error_invalid_input
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
