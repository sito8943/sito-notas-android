package dev.sito8943.sitonotas.feature.sharing

import androidx.annotation.StringRes
import dev.sito8943.sitonotas.R
import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.core.network.NetworkException

/** Why an online sharing request failed, as the UI tells it. */
enum class SharingError {
    /** No connection or no answer: sharing is online only. */
    OFFLINE,

    /** 404: the note is gone, trashed, or no longer shared with the user. */
    NOT_FOUND,

    /** "Share" 400: the user's own email (web `share.errors.self`). */
    SELF,

    /** "Share" 404: no Sito Notas user has that email. */
    NO_USER,

    /** "Share" 409. */
    ALREADY_SHARED,

    /** Anything else (5xx, unreadable answer). */
    UNKNOWN,
    ;

    companion object {
        fun of(error: Throwable): SharingError = when {
            error is NetworkException -> OFFLINE
            error is ApiException && error.status == 404 -> NOT_FOUND
            else -> UNKNOWN
        }

        /** Errors of `POST /notes/{id}/shares`, by status, like the web's `SHARE_ERROR_KEYS`. */
        fun ofShare(error: Throwable): SharingError = when {
            error is ApiException && error.status == 400 -> SELF
            error is ApiException && error.status == 404 -> NO_USER
            error is ApiException && error.status == 409 -> ALREADY_SHARED
            else -> of(error)
        }
    }
}

@StringRes
fun SharingError.messageRes(): Int = when (this) {
    SharingError.OFFLINE -> R.string.sharing_error_offline
    SharingError.NOT_FOUND -> R.string.sharing_error_not_found
    SharingError.SELF -> R.string.share_error_self
    SharingError.NO_USER -> R.string.share_error_not_found
    SharingError.ALREADY_SHARED -> R.string.share_error_already_shared
    SharingError.UNKNOWN -> R.string.sharing_error_unknown
}
