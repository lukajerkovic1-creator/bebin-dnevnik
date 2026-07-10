package hr.bebindnevnik.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class DatabaseKeyManager(
    private val context: Context,
    preferencesName: String = PREFS,
    private val keyAlias: String = ALIAS,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val wrapped = preferences.getString(KEY_WRAPPED, null)
        return if (wrapped == null) createPassphrase() else unwrap(wrapped)
    }

    private fun createPassphrase(): ByteArray {
        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
            val encrypted = cipher.doFinal(passphrase)
            val payload = cipher.iv + encrypted
            preferences.edit(commit = true) { putString(KEY_WRAPPED, payload.encodeBase64()) }
            return passphrase
        } catch (error: Exception) {
            passphrase.fill(0)
            throw DatabaseUnavailableException("Ključ baze nije moguće sigurno stvoriti.", error)
        }
    }

    private fun unwrap(encoded: String): ByteArray {
        val payload =
            try {
                encoded.decodeBase64()
            } catch (error: Exception) {
                throw DatabaseUnavailableException("Zaštićeni ključ baze je nevažeći.", error)
            }
        if (payload.size <= NONCE_BYTES) throw DatabaseUnavailableException("Zaštićeni ključ baze je oštećen.")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKeystoreKey(create = false),
                GCMParameterSpec(128, payload.copyOfRange(0, NONCE_BYTES)),
            )
            cipher.doFinal(payload.copyOfRange(NONCE_BYTES, payload.size))
        } catch (error: Exception) {
            throw DatabaseUnavailableException(
                "Šifriranu bazu nije moguće otvoriti. Nemojte brisati podatke; pokušajte uvesti sigurnosnu kopiju.",
                error,
            )
        } finally {
            payload.fill(0)
        }
    }

    private fun getOrCreateKeystoreKey(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        if (!create) throw DatabaseUnavailableException("Zaštitni ključ uređaja više nije dostupan.")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.encodeBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    private companion object {
        const val PREFS = "database_key"
        const val KEY_WRAPPED = "wrapped"
        const val ALIAS = "bebin_dnevnik_database_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
    }
}
