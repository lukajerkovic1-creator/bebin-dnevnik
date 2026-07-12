package hr.bebindnevnik.app.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import hr.bebindnevnik.app.data.AppSnapshot
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LocalSafetyBackup {
    fun create(
        context: Context,
        snapshot: AppSnapshot,
    ) {
        val passwordBytes = ByteArray(24).also(SecureRandom()::nextBytes)
        val password =
            android.util.Base64
                .encodeToString(passwordBytes, android.util.Base64.NO_WRAP)
                .toCharArray()
        passwordBytes.fill(0)
        val encryptedBackup = BackupManager.encrypt(snapshot, password.copyOf())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val wrappedPassword = cipher.iv + cipher.doFinal(String(password).toByteArray(Charsets.UTF_8))
        password.fill('\u0000')
        val directory = context.filesDir.resolve("safety-backup").apply { mkdirs() }
        directory.resolve("latest.bdk").writeBytes(encryptedBackup)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_PASSWORD, android.util.Base64.encodeToString(wrappedPassword, android.util.Base64.NO_WRAP))
        }
        wrappedPassword.fill(0)
    }

    fun exists(context: Context): Boolean =
        context.filesDir.resolve("safety-backup/latest.bdk").isFile &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_PASSWORD)

    fun restore(context: Context): BackupPreview {
        val wrapped =
            android.util.Base64.decode(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PASSWORD, null)
                    ?: error("Lokalna sigurnosna kopija nije dostupna."),
                android.util.Base64.NO_WRAP,
            )
        require(wrapped.size > NONCE_BYTES) { "Lokalna sigurnosna kopija je oštećena." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(false), GCMParameterSpec(128, wrapped.copyOfRange(0, NONCE_BYTES)))
        val password = String(cipher.doFinal(wrapped.copyOfRange(NONCE_BYTES, wrapped.size)), Charsets.UTF_8).toCharArray()
        wrapped.fill(0)
        return BackupManager.decrypt(context.filesDir.resolve("safety-backup/latest.bdk").readBytes(), password)
    }

    private fun getOrCreateKey(create: Boolean = true): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Lokalni zaštitni ključ nije dostupan." }
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

    private const val PREFS = "local_safety_backup"
    private const val KEY_PASSWORD = "wrapped_password"
    private const val ALIAS = "bebin_dnevnik_safety_backup_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_BYTES = 12
}
