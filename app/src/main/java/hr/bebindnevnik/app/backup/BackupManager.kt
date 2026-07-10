package hr.bebindnevnik.app.backup

import hr.bebindnevnik.app.BuildConfig
import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.json.JSONArray
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

data class BackupPreview(
    val snapshot: AppSnapshot,
    val mealCount: Int,
    val dailyCount: Int,
    val tummyCount: Int,
)

class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object BackupManager {
    private val magic = byteArrayOf(0x42, 0x44, 0x4B, 0x31)
    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12

    fun encrypt(
        snapshot: AppSnapshot,
        password: CharArray,
    ): ByteArray {
        require(password.size >= 8) { "Lozinka mora imati najmanje 8 znakova." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val plain = snapshot.toJson().toString().toByteArray(Charsets.UTF_8)
        val key = derive(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(magic + FORMAT_VERSION.toByte())
            val encrypted = cipher.doFinal(plain)
            ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { data ->
                    data.write(magic)
                    data.writeInt(FORMAT_VERSION)
                    data.writeInt(ITERATIONS)
                    data.writeInt(salt.size)
                    data.writeInt(nonce.size)
                    data.writeInt(encrypted.size)
                    data.write(salt)
                    data.write(nonce)
                    data.write(encrypted)
                }
                output.toByteArray()
            }
        } finally {
            plain.fill(0)
            key.fill(0)
            password.fill('\u0000')
        }
    }

    fun decrypt(
        bytes: ByteArray,
        password: CharArray,
    ): BackupPreview {
        require(password.size >= 8) { "Lozinka mora imati najmanje 8 znakova." }
        var key = ByteArray(0)
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val fileMagic = ByteArray(4).also(data::readFully)
                if (!fileMagic.contentEquals(magic)) throw InvalidBackupException("Datoteka nije sigurnosna kopija Bebina dnevnika.")
                val version = data.readInt()
                if (version != FORMAT_VERSION) throw InvalidBackupException("Nepoznata verzija sigurnosne kopije: $version.")
                val iterations = data.readInt()
                val saltSize = data.readInt()
                val nonceSize = data.readInt()
                val encryptedSize = data.readInt()
                if (iterations !in 100_000..2_000_000 || saltSize !in 16..64 || nonceSize != NONCE_BYTES ||
                    encryptedSize !in 16..100_000_000
                ) {
                    throw InvalidBackupException("Zaglavlje sigurnosne kopije je nevažeće.")
                }
                val expected = saltSize.toLong() + nonceSize + encryptedSize
                if (data.available().toLong() != expected) throw InvalidBackupException("Sigurnosna kopija je nepotpuna ili oštećena.")
                val salt = ByteArray(saltSize).also(data::readFully)
                val nonce = ByteArray(nonceSize).also(data::readFully)
                val encrypted = ByteArray(encryptedSize).also(data::readFully)
                key = derive(password, salt, iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(magic + version.toByte())
                val plain =
                    try {
                        cipher.doFinal(encrypted)
                    } catch (error: AEADBadTagException) {
                        throw InvalidBackupException("Pogrešna lozinka ili oštećena sigurnosna kopija.", error)
                    }
                return try {
                    val snapshot = JSONObject(String(plain, Charsets.UTF_8)).toSnapshot()
                    BackupPreview(snapshot, snapshot.meals.size, snapshot.dailyEntries.size, snapshot.tummySessions.size)
                } finally {
                    plain.fill(0)
                }
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("Sigurnosnu kopiju nije moguće pročitati.", error)
        } finally {
            key.fill(0)
            password.fill('\u0000')
        }
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

    private fun AppSnapshot.toJson() =
        JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportedAt", Instant.now().toString())
            put("meals", JSONArray().apply { meals.forEach { put(it.toJson()) } })
            put("dailyEntries", JSONArray().apply { dailyEntries.forEach { put(it.toJson()) } })
            put("tummySessions", JSONArray().apply { tummySessions.forEach { put(it.toJson()) } })
            put("settings", settings.toJson())
        }

    private fun MealEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("date", date)
            put("time", time)
            put("amountMl", amountMl)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun DailyEntryEntity.toJson() =
        JSONObject().apply {
            put("date", date)
            put("waya", waya.name)
            put("exercise", exercise.name)
            put("noTummyTime", noTummyTime)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun TummySessionEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("date", date)
            put("time", time)
            put("durationSeconds", durationSeconds)
            put("inputMethod", inputMethod.name)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun SettingsEntity.toJson() =
        JSONObject().apply {
            put("reminderEnabled", reminderEnabled)
            put("reminderTime", reminderTime)
            put("theme", theme.name)
            put("onboardingShown", onboardingShown)
        }

    private fun JSONObject.toSnapshot(): AppSnapshot {
        if (getInt("formatVersion") != FORMAT_VERSION) throw InvalidBackupException("Nepoznata verzija sadržaja sigurnosne kopije.")
        val meals =
            getJSONArray("meals").mapObjects { item ->
                MealEntity(
                    item.getLong("id"),
                    item.getString("date"),
                    item.getString("time"),
                    item.getInt("amountMl"),
                    item.getLong("createdAt"),
                    item.getLong("updatedAt"),
                )
            }
        val entries =
            getJSONArray("dailyEntries").mapObjects { item ->
                DailyEntryEntity(
                    item.getString("date"),
                    TernaryStatus.valueOf(item.getString("waya")),
                    TernaryStatus.valueOf(item.getString("exercise")),
                    item.getBoolean("noTummyTime"),
                    item.getLong("createdAt"),
                    item.getLong("updatedAt"),
                )
            }
        val sessions =
            getJSONArray("tummySessions").mapObjects { item ->
                TummySessionEntity(
                    item.getLong("id"),
                    item.getString("date"),
                    item.getString("time"),
                    item.getLong("durationSeconds"),
                    TummyInputMethod.valueOf(item.getString("inputMethod")),
                    item.getLong("createdAt"),
                    item.getLong("updatedAt"),
                )
            }
        val config = getJSONObject("settings")
        val settings =
            SettingsEntity(
                reminderEnabled = config.getBoolean("reminderEnabled"),
                reminderTime = config.getString("reminderTime"),
                theme = AppTheme.valueOf(config.getString("theme")),
                onboardingShown = config.getBoolean("onboardingShown"),
            )
        return AppSnapshot(meals, entries, sessions, settings)
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }
}
