package dev.sito8943.sitonotas.feature.auth

import dev.sito8943.sitonotas.core.network.ApiException
import dev.sito8943.sitonotas.feature.auth.data.SignUpRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SignUpErrorTest {

    @Test
    fun `sign-up errors map like the web`() {
        assertEquals(AuthError.EMAIL_TAKEN, ApiException(409, "Email already registered").toSignUpError())
        assertEquals(
            AuthError.SIGN_UP_DISABLED,
            ApiException(400, "auth.register.featureDisabled").toSignUpError(),
        )
        assertEquals(AuthError.INVALID_INPUT, ApiException(400, "email is required").toSignUpError())
        assertEquals(AuthError.TOO_MANY_ATTEMPTS, ApiException(429).toSignUpError())
    }

    @Test
    fun `register request carries only email and password`() {
        assertEquals(
            """{"email":"a@b.c","password":"secreto"}""",
            Json.encodeToString(SignUpRequest("a@b.c", "secreto")),
        )
    }
}
