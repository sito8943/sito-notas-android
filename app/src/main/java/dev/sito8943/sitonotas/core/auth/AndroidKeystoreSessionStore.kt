package dev.sito8943.sitonotas.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sesion cifrada con AES-256-GCM bajo una clave que vive en el AndroidKeyStore
 * y nunca sale de el; en SharedPreferences solo quedan el IV y el ciphertext.
 *
 * Portado de `AndroidKeystoreSessionStore` de SitoChat. Unica diferencia: el
 * cuerpo se serializa con kotlinx-serialization en vez de `org.json`, porque el
 * proyecto ya la lleva y evita mapear campo a campo a mano.
 */
class AndroidKeystoreSessionStore(
    context: Context,
    private val json: Json,
) : SessionStore {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loginAnswered(): Boolean =
        preferences.getBoolean(KEY_LOGIN_ANSWERED, false)

    override fun markLoginAnswered() {
        preferences.edit().putBoolean(KEY_LOGIN_ANSWERED, true).apply()
    }

    override fun load(): SessionLoadResult {
        val iv = preferences.getString(KEY_INITIALIZATION_VECTOR, null)
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)

        if (iv == null && ciphertext == null) return SessionLoadResult.Missing
        if (iv == null || ciphertext == null) {
            // Escritura a medias: no es recuperable, se limpia y se sigue.
            clear()
            return SessionLoadResult.Unreadable
        }

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv.decodeBase64()),
            )
            val plaintext = cipher.doFinal(ciphertext.decodeBase64())
            SessionLoadResult.Loaded(
                json.decodeFromString<AuthSession>(String(plaintext, StandardCharsets.UTF_8)),
            )
        }.getOrElse {
            // La clave del Keystore puede quedar invalidada (el usuario cambia el
            // bloqueo de pantalla, se restaura el dispositivo). No es un error a
            // mostrar: se descarta la sesion y se vuelve a pedir login.
            clear()
            deleteSecretKey()
            SessionLoadResult.Unreadable
        }
    }

    override fun save(session: AuthSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val ciphertext = cipher.doFinal(
            json.encodeToString(session).toByteArray(StandardCharsets.UTF_8),
        )

        // commit() y no apply(): si el proceso muere justo despues, una sesion a
        // medio escribir deja al usuario sin poder entrar.
        val persisted = preferences.edit()
            .putString(KEY_INITIALIZATION_VECTOR, cipher.iv.encodeBase64())
            .putString(KEY_CIPHERTEXT, ciphertext.encodeBase64())
            .commit()
        check(persisted) { "No se pudo persistir la sesion cifrada." }
    }

    /**
     * Tambien olvida que se contesto al login. Cerrar sesion tiene que devolver
     * ahi, y una clave del Keystore invalidada tambien: en los dos casos hay
     * que volver a preguntar.
     */
    override fun clear() {
        preferences.edit()
            .remove(KEY_INITIALIZATION_VECTOR)
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_LOGIN_ANSWERED)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun deleteSecretKey() {
        runCatching {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
                deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "sitonotas.encrypted_session"
        const val KEY_INITIALIZATION_VECTOR = "initialization_vector"
        const val KEY_CIPHERTEXT = "ciphertext"

        /**
         * Sin cifrar a proposito: es un booleano de navegacion, no un secreto,
         * y tiene que sobrevivir a que la clave del Keystore se invalide.
         */
        const val KEY_LOGIN_ANSWERED = "login_answered"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "sitonotas.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
