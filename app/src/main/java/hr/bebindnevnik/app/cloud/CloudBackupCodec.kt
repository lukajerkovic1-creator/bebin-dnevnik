package hr.bebindnevnik.app.cloud

import hr.bebindnevnik.app.BuildConfig
import hr.bebindnevnik.app.backup.BackupManager
import hr.bebindnevnik.app.backup.InvalidBackupException
import hr.bebindnevnik.app.data.AppSnapshot
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class PasswordWrappedDek(
    val salt: ByteArray,
    val nonce: ByteArray,
    val encryptedDek: ByteArray,
    val iterations: Int = CloudBackupCodec.KDF_ITERATIONS,
)

data class CloudBackupMetadata(
    val createdAt: String,
    val appVersion: String,
    val schemaVersion: Int,
    val mealCount: Int,
    val dailyCount: Int,
    val tummyCount: Int,
)

data class DecodedCloudBackup(
    val metadata: CloudBackupMetadata,
    val snapshot: AppSnapshot,
)

@Suppress("TooManyFunctions") // Binary codec keeps all format primitives together so the authenticated layout stays auditable.
object CloudBackupCodec {
    internal const val KDF_ITERATIONS = 600_000
    private const val FORMAT_VERSION = 1
    private const val NONCE_BYTES = 12
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val MAX_CONTENT_BYTES = 100 * 1024 * 1024
    private val magic = byteArrayOf(0x42, 0x44, 0x43, 0x31)

    fun createKeyMaterial(password: CharArray): Pair<ByteArray, PasswordWrappedDek> {
        require(password.size >= 8) { "Lozinka mora imati najmanje 8 znakova." }
        val dek = ByteArray(32).also(SecureRandom()::nextBytes)
        return dek to wrapDek(dek, password)
    }

    fun rewrapDek(
        dek: ByteArray,
        password: CharArray,
    ): PasswordWrappedDek = wrapDek(dek, password)

    fun encode(
        snapshot: AppSnapshot,
        dek: ByteArray,
        passwordWrap: PasswordWrappedDek,
        schemaVersion: Int,
    ): ByteArray {
        require(dek.size == 32) { "Cloud ključ mora imati 256 bita." }
        val dataNonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val metadata =
            CloudBackupMetadata(
                createdAt = Instant.now().toString(),
                appVersion = BuildConfig.VERSION_NAME,
                schemaVersion = schemaVersion,
                mealCount = snapshot.meals.size,
                dailyCount = snapshot.dailyEntries.size,
                tummyCount = snapshot.tummySessions.size,
            )
        val header = metadata.toHeader(passwordWrap, dataNonce).toString().toByteArray(Charsets.UTF_8)
        val plain = BackupManager.snapshotJson(snapshot).toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(128, dataNonce))
            cipher.updateAAD(magic + FORMAT_VERSION.toByte() + header)
            val encrypted = cipher.doFinal(plain)
            ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { data ->
                    data.write(magic)
                    data.writeInt(FORMAT_VERSION)
                    data.writeInt(header.size)
                    data.writeInt(encrypted.size)
                    data.write(header)
                    data.write(encrypted)
                }
                output.toByteArray()
            }
        } finally {
            plain.fill(0)
            dataNonce.fill(0)
        }
    }

    fun metadata(bytes: ByteArray): CloudBackupMetadata = parse(bytes).metadata

    fun decode(
        bytes: ByteArray,
        password: CharArray,
    ): DecodedCloudBackup {
        require(password.size >= 8) { "Lozinka mora imati najmanje 8 znakova." }
        val parsed = parse(bytes)
        val header = parsed.header
        val wrap =
            PasswordWrappedDek(
                header.bytes("salt"),
                header.bytes("passwordNonce"),
                header.bytes("encryptedDek"),
                header.getInt("kdfIterations"),
            )
        val dek = unwrapDek(wrap, password)
        password.fill('\u0000')
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(128, header.bytes("dataNonce")))
            cipher.updateAAD(magic + FORMAT_VERSION.toByte() + parsed.headerBytes)
            val plain =
                try {
                    cipher.doFinal(parsed.encrypted)
                } catch (error: AEADBadTagException) {
                    throw InvalidBackupException("Cloud kopija je oštećena ili je lozinka pogrešna.", error)
                }
            try {
                DecodedCloudBackup(parsed.metadata, BackupManager.snapshotFromJson(String(plain, Charsets.UTF_8)))
            } finally {
                plain.fill(0)
            }
        } finally {
            dek.fill(0)
        }
    }

    private fun wrapDek(
        dek: ByteArray,
        password: CharArray,
    ): PasswordWrappedDek {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val kek = derive(password, salt, KDF_ITERATIONS)
        password.fill('\u0000')
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(magic + FORMAT_VERSION.toByte())
            PasswordWrappedDek(salt, nonce, cipher.doFinal(dek))
        } finally {
            kek.fill(0)
        }
    }

    private fun unwrapDek(
        wrap: PasswordWrappedDek,
        password: CharArray,
    ): ByteArray {
        require(wrap.iterations in 200_000..2_000_000) { "KDF parametri nisu sigurni." }
        val kek = derive(password, wrap.salt, wrap.iterations)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, wrap.nonce))
            cipher.updateAAD(magic + FORMAT_VERSION.toByte())
            try {
                cipher.doFinal(wrap.encryptedDek)
            } catch (error: AEADBadTagException) {
                throw InvalidBackupException("Pogrešna lozinka za cloud sigurnosnu kopiju.", error)
            }
        } finally {
            kek.fill(0)
        }
    }

    private fun parse(bytes: ByteArray): ParsedCloudBackup =
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val fileMagic = ByteArray(4).also(data::readFully)
                require(fileMagic.contentEquals(magic)) { "Datoteka nije cloud kopija Bebina dnevnika." }
                require(data.readInt() == FORMAT_VERSION) { "Cloud format nije podržan." }
                val headerSize = data.readInt()
                val encryptedSize = data.readInt()
                require(headerSize in 1..MAX_HEADER_BYTES && encryptedSize in 16..MAX_CONTENT_BYTES) { "Cloud zaglavlje nije valjano." }
                require(data.available() == headerSize + encryptedSize) { "Cloud kopija je nepotpuna." }
                val headerBytes = ByteArray(headerSize).also(data::readFully)
                val encrypted = ByteArray(encryptedSize).also(data::readFully)
                val header = JSONObject(String(headerBytes, Charsets.UTF_8))
                ParsedCloudBackup(header, headerBytes, encrypted, header.toMetadata())
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("Cloud sigurnosnu kopiju nije moguće pročitati.", error)
        }

    private fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun CloudBackupMetadata.toHeader(
        wrap: PasswordWrappedDek,
        dataNonce: ByteArray,
    ) = JSONObject().apply {
        put("createdAt", createdAt)
        put("appVersion", appVersion)
        put("schemaVersion", schemaVersion)
        put("mealCount", mealCount)
        put("dailyCount", dailyCount)
        put("tummyCount", tummyCount)
        put("kdf", "PBKDF2-HMAC-SHA-256")
        put("kdfIterations", wrap.iterations)
        put("salt", wrap.salt.base64())
        put("passwordNonce", wrap.nonce.base64())
        put("encryptedDek", wrap.encryptedDek.base64())
        put("dataNonce", dataNonce.base64())
    }

    private fun JSONObject.toMetadata() =
        CloudBackupMetadata(
            getString("createdAt"),
            getString("appVersion"),
            getInt("schemaVersion"),
            getInt("mealCount"),
            getInt("dailyCount"),
            getInt("tummyCount"),
        )

    private fun ByteArray.base64(): String =
        java.util.Base64
            .getEncoder()
            .encodeToString(this)

    private fun JSONObject.bytes(name: String): ByteArray =
        java.util.Base64
            .getDecoder()
            .decode(getString(name))

    private data class ParsedCloudBackup(
        val header: JSONObject,
        val headerBytes: ByteArray,
        val encrypted: ByteArray,
        val metadata: CloudBackupMetadata,
    )

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
