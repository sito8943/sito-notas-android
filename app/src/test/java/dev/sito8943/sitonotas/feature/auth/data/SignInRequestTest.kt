package dev.sito8943.sitonotas.feature.auth.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInRequestTest {

    // Same settings as AppContainer.json.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun signInAlwaysSendsRememberMeSoTheServerIssuesARefreshToken() {
        val body = json.encodeToString(SignInRequest(email = "a@b.c", password = "x"))
        val rememberMe = json.parseToJsonElement(body).jsonObject["rememberMe"]
        assertTrue("rememberMe missing from $body", rememberMe?.jsonPrimitive?.boolean == true)
    }
}
