package hr.bebindnevnik.app.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CloudKeyManager(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enable(password: CharArray): PasswordWrappedDek {
        val (dek, passwordWrap) = CloudBackupCodec.createKeyMaterial(password)
        try {
            storeLocalDek(dek)
            storePasswordWrap(passwordWrap)
            return passwordWrap
        } finally {
            dek.fill(0)
        }
    }

    fun localDek(): ByteArray {
        val wrapped = preferences.getString(KEY_LOCAL_DEK, null)?.decode() ?: error("Lokalni cloud ključ nije dostupan.")
        require(wrapped.size > NONCE_BYTES) { "Lokalni cloud ključ je oštećen." }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(false), GCMParameterSpec(128, wrapped.copyOfRange(0, NONCE_BYTES)))
            cipher.doFinal(wrapped.copyOfRange(NONCE_BYTES, wrapped.size))
        } finally {
            wrapped.fill(0)
        }
    }

    fun passwordWrap(): PasswordWrappedDek =
        PasswordWrappedDek(
            preferences.getString(KEY_SALT, null)?.decode() ?: error("Cloud lozinka nije postavljena."),
            preferences.getString(KEY_PASSWORD_NONCE, null)?.decode() ?: error("Cloud lozinka nije postavljena."),
            preferences.getString(KEY_ENCRYPTED_DEK, null)?.decode() ?: error("Cloud lozinka nije postavljena."),
            preferences.getInt(KEY_ITERATIONS, CloudBackupCodec.KDF_ITERATIONS),
        )

    fun changePassword(newPassword: CharArray): PasswordWrappedDek {
        val dek = localDek()
        return try {
            CloudBackupCodec.rewrapDek(dek, newPassword).also(::storePasswordWrap)
        } finally {
            dek.fill(0)
        }
    }

    fun clear() = preferences.edit(commit = true) { clear() }

    private fun storeLocalDek(dek: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val wrapped = cipher.iv + cipher.doFinal(dek)
        preferences.edit(commit = true) { putString(KEY_LOCAL_DEK, wrapped.encode()) }
        wrapped.fill(0)
    }

    private fun storePasswordWrap(wrap: PasswordWrappedDek) {
        preferences.edit(commit = true) {
            putString(KEY_SALT, wrap.salt.encode())
            putString(KEY_PASSWORD_NONCE, wrap.nonce.encode())
            putString(KEY_ENCRYPTED_DEK, wrap.encryptedDek.encode())
            putInt(KEY_ITERATIONS, wrap.iterations)
        }
    }

    private fun keystoreKey(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Android Keystore ključ za cloud više nije dostupan." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.encode(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.decode(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    private companion object {
        const val PREFS = "cloud_key_material"
        const val KEY_LOCAL_DEK = "local_dek"
        const val KEY_SALT = "salt"
        const val KEY_PASSWORD_NONCE = "password_nonce"
        const val KEY_ENCRYPTED_DEK = "encrypted_dek"
        const val KEY_ITERATIONS = "iterations"
        const val ALIAS = "bebin_dnevnik_cloud_dek_key"
        const val NONCE_BYTES = 12
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
