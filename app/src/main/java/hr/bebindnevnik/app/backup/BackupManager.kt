package hr.bebindnevnik.app.backup

import hr.bebindnevnik.app.BuildConfig
import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.ExpectedMealCountEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.IndividualFeedingTargetEntity
import hr.bebindnevnik.app.data.IndividualTummyTargetEntity
import hr.bebindnevnik.app.data.LengthMeasurementType
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.MilkCompletenessEntity
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
    val growthCount: Int,
    val complementaryFoodCount: Int = 0,
)

class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Suppress("TooManyFunctions")
object BackupManager {
    private val magic = byteArrayOf(0x42, 0x44, 0x4B, 0x31)
    private const val FORMAT_VERSION = 5
    private const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12

    fun encrypt(
        snapshot: AppSnapshot,
        password: CharArray,
    ): ByteArray = encryptWithVersion(snapshot, password, FORMAT_VERSION)

    internal fun encryptWithVersion(
        snapshot: AppSnapshot,
        password: CharArray,
        formatVersion: Int,
    ): ByteArray {
        require(formatVersion in 1..FORMAT_VERSION) { "Nepodržana verzija sigurnosne kopije." }
        require(password.size >= 8) { "Lozinka mora imati najmanje 8 znakova." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val plain = snapshotJson(snapshot, formatVersion).toByteArray(Charsets.UTF_8)
        val key = derive(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(magic + formatVersion.toByte())
            val encrypted = cipher.doFinal(plain)
            ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { data ->
                    data.write(magic)
                    data.writeInt(formatVersion)
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
                if (version !in 1..FORMAT_VERSION) throw InvalidBackupException("Nepoznata verzija sigurnosne kopije: $version.")
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
                    val snapshot = snapshotFromJson(String(plain, Charsets.UTF_8), version)
                    BackupPreview(
                        snapshot,
                        snapshot.meals.size,
                        snapshot.dailyEntries.size,
                        snapshot.tummySessions.size,
                        snapshot.growthMeasurements.size,
                        snapshot.complementaryFoodMeals.size,
                    )
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

    internal fun snapshotJson(
        snapshot: AppSnapshot,
        formatVersion: Int = FORMAT_VERSION,
    ): String = snapshot.toJson(formatVersion).toString()

    internal fun snapshotFromJson(
        json: String,
        formatVersion: Int? = null,
    ): AppSnapshot {
        val content = JSONObject(json)
        return content.toSnapshot(formatVersion ?: content.getInt("formatVersion"))
    }

    private fun AppSnapshot.toJson(formatVersion: Int) =
        JSONObject().apply {
            put("formatVersion", formatVersion)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportedAt", Instant.now().toString())
            put("meals", JSONArray().apply { meals.forEach { put(it.toJson()) } })
            put("dailyEntries", JSONArray().apply { dailyEntries.forEach { put(it.toJson(formatVersion)) } })
            put("tummySessions", JSONArray().apply { tummySessions.forEach { put(it.toJson()) } })
            put("settings", settings.toJson())
            if (formatVersion >= 3) {
                put("childProfile", childProfile?.toJson() ?: JSONObject.NULL)
                put("growthMeasurements", JSONArray().apply { growthMeasurements.forEach { put(it.toJson()) } })
            }
            if (formatVersion >= 4) {
                put("complementaryFoodMeals", JSONArray().apply { complementaryFoodMeals.forEach { put(it.toJson()) } })
            }
            if (formatVersion >= 5) {
                put("milkCompletenessHistory", JSONArray().apply { milkCompletenessHistory.forEach { put(it.toJson()) } })
                put("expectedMealCountHistory", JSONArray().apply { expectedMealCountHistory.forEach { put(it.toJson()) } })
                put("individualFeedingTargets", JSONArray().apply { individualFeedingTargets.forEach { put(it.toJson()) } })
                put("individualTummyTargets", JSONArray().apply { individualTummyTargets.forEach { put(it.toJson()) } })
            }
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

    private fun DailyEntryEntity.toJson(formatVersion: Int) =
        JSONObject().apply {
            put("date", date)
            put("waya", waya.name)
            put("exercise", exercise.name)
            put("noTummyTime", noTummyTime)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            if (formatVersion >= 2) put("stoolCount", stoolCount ?: JSONObject.NULL)
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
            put("guidelineTargetsEnabled", guidelineTargetsEnabled)
            put("guidelineWizardCompleted", guidelineWizardCompleted)
            put("guidelineWizardDismissed", guidelineWizardDismissed)
        }

    private fun ChildProfileEntity.toJson() =
        JSONObject().apply {
            put("name", name)
            put("sex", sex.name)
            put("birthDate", birthDate)
            put("gestationalWeeks", gestationalWeeks)
            put("gestationalDays", gestationalDays)
            put("birthWeightG", birthWeightG ?: JSONObject.NULL)
            put("birthLengthCm", birthLengthCm ?: JSONObject.NULL)
            put("birthHeadCircumferenceCm", birthHeadCircumferenceCm ?: JSONObject.NULL)
            put("independentMobilityDate", independentMobilityDate ?: JSONObject.NULL)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun GrowthMeasurementEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("date", date)
            put("time", time)
            put("weightG", weightG ?: JSONObject.NULL)
            put("lengthHeightCm", lengthHeightCm ?: JSONObject.NULL)
            put("lengthMeasurementType", lengthMeasurementType.name)
            put("headCircumferenceCm", headCircumferenceCm ?: JSONObject.NULL)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun ComplementaryFoodMealEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("date", date)
            put("time", time)
            put("ingredients", JSONArray(ingredients))
            put("amount", amount)
            put("unit", unit.name)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun MilkCompletenessEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("startDate", startDate)
            put("endDate", endDate ?: JSONObject.NULL)
            put("complete", complete)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun ExpectedMealCountEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("startDate", startDate)
            put("endDate", endDate ?: JSONObject.NULL)
            put("mealCount", mealCount)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun IndividualFeedingTargetEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("lowerMlPerDay", lowerMlPerDay)
            put("upperMlPerDay", upperMlPerDay)
            put("startDate", startDate)
            put("endDate", endDate ?: JSONObject.NULL)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    private fun IndividualTummyTargetEntity.toJson() =
        JSONObject().apply {
            put("id", id)
            put("minutesPerDay", minutesPerDay)
            put("startDate", startDate)
            put("endDate", endDate ?: JSONObject.NULL)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }

    @Suppress("LongMethod") // Version-gated decoding is intentionally kept atomic and auditable.
    private fun JSONObject.toSnapshot(headerVersion: Int): AppSnapshot {
        val contentVersion = getInt("formatVersion")
        if (contentVersion != headerVersion || contentVersion !in 1..FORMAT_VERSION) {
            throw InvalidBackupException("Nepoznata verzija sadržaja sigurnosne kopije.")
        }
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
                    date = item.getString("date"),
                    waya = TernaryStatus.valueOf(item.getString("waya")),
                    exercise = TernaryStatus.valueOf(item.getString("exercise")),
                    noTummyTime = item.getBoolean("noTummyTime"),
                    createdAt = item.getLong("createdAt"),
                    updatedAt = item.getLong("updatedAt"),
                    stoolCount = if (contentVersion >= 2 && !item.isNull("stoolCount")) item.getInt("stoolCount") else null,
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
                guidelineTargetsEnabled = if (contentVersion >= 5) config.optBoolean("guidelineTargetsEnabled", true) else true,
                guidelineWizardCompleted = if (contentVersion >= 5) config.optBoolean("guidelineWizardCompleted", false) else false,
                guidelineWizardDismissed = if (contentVersion >= 5) config.optBoolean("guidelineWizardDismissed", false) else false,
            )
        val profile =
            if (contentVersion >= 3 && !isNull("childProfile")) {
                getJSONObject("childProfile").let { item ->
                    ChildProfileEntity(
                        name = item.getString("name"),
                        sex = ChildSex.valueOf(item.getString("sex")),
                        birthDate = item.getString("birthDate"),
                        gestationalWeeks = item.getInt("gestationalWeeks"),
                        gestationalDays = item.getInt("gestationalDays"),
                        birthWeightG = item.optNullableInt("birthWeightG"),
                        birthLengthCm = item.optNullableDouble("birthLengthCm"),
                        birthHeadCircumferenceCm = item.optNullableDouble("birthHeadCircumferenceCm"),
                        independentMobilityDate = if (contentVersion >= 5 && !item.isNull("independentMobilityDate")) item.getString("independentMobilityDate") else null,
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                null
            }
        val growth =
            if (contentVersion >= 3) {
                getJSONArray("growthMeasurements").mapObjects { item ->
                    GrowthMeasurementEntity(
                        id = item.getLong("id"),
                        date = item.getString("date"),
                        time = item.getString("time"),
                        weightG = item.optNullableInt("weightG"),
                        lengthHeightCm = item.optNullableDouble("lengthHeightCm"),
                        lengthMeasurementType = LengthMeasurementType.valueOf(item.getString("lengthMeasurementType")),
                        headCircumferenceCm = item.optNullableDouble("headCircumferenceCm"),
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                emptyList()
            }
        val complementaryFood =
            if (contentVersion >= 4) {
                getJSONArray("complementaryFoodMeals").mapObjects { item ->
                    ComplementaryFoodMealEntity(
                        id = item.getLong("id"),
                        date = item.getString("date"),
                        time = item.getString("time"),
                        ingredients = item.getJSONArray("ingredients").mapStrings(),
                        amount = item.getInt("amount"),
                        unit = ComplementaryFoodUnit.valueOf(item.getString("unit")),
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                emptyList()
            }
        val completeness =
            if (contentVersion >= 5) {
                getJSONArray("milkCompletenessHistory").mapObjects { item ->
                    MilkCompletenessEntity(
                        id = item.getLong("id"),
                        startDate = item.getString("startDate"),
                        endDate = item.optNullableString("endDate"),
                        complete = item.getBoolean("complete"),
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                emptyList()
            }
        val mealCounts =
            if (contentVersion >= 5) {
                getJSONArray("expectedMealCountHistory").mapObjects { item ->
                    ExpectedMealCountEntity(
                        id = item.getLong("id"),
                        startDate = item.getString("startDate"),
                        endDate = item.optNullableString("endDate"),
                        mealCount = item.getInt("mealCount"),
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                emptyList()
            }
        val feedingTargets =
            if (contentVersion >= 5) {
                getJSONArray("individualFeedingTargets").mapObjects { item ->
                    IndividualFeedingTargetEntity(
                        id = item.getLong("id"),
                        lowerMlPerDay = item.getInt("lowerMlPerDay"),
                        upperMlPerDay = item.getInt("upperMlPerDay"),
                        startDate = item.getString("startDate"),
                        endDate = item.optNullableString("endDate"),
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                emptyList()
            }
        val tummyTargets =
            if (contentVersion >= 5) {
                getJSONArray("individualTummyTargets").mapObjects { item ->
                    IndividualTummyTargetEntity(
                        id = item.getLong("id"),
                        minutesPerDay = item.getInt("minutesPerDay"),
                        startDate = item.getString("startDate"),
                        endDate = item.optNullableString("endDate"),
                        createdAt = item.getLong("createdAt"),
                        updatedAt = item.getLong("updatedAt"),
                    )
                }
            } else {
                emptyList()
            }
        return AppSnapshot(
            meals,
            entries,
            sessions,
            settings,
            profile,
            growth,
            complementaryFood,
            completeness,
            mealCounts,
            feedingTargets,
            tummyTargets,
        )
    }

    private fun JSONObject.optNullableInt(name: String): Int? = if (isNull(name)) null else getInt(name)

    private fun JSONObject.optNullableDouble(name: String): Double? = if (isNull(name)) null else getDouble(name)

    private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else getString(name)

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }

    private fun JSONArray.mapStrings(): List<String> = List(length()) { getString(it) }
}
