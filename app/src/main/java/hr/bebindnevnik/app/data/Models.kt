package hr.bebindnevnik.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TernaryStatus { NIJE_EVIDENTIRANO, DA, NE }

enum class TummyInputMethod { STOPERICA, RUCNO }

enum class AppTheme { SUSTAV, SVIJETLA, TAMNA }

enum class DayStatus { POTPUNO, DJELOMICNO, BEZ_PODATAKA }

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val amountMl: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "daily_entries")
data class DailyEntryEntity(
    @PrimaryKey val date: String,
    val waya: TernaryStatus = TernaryStatus.NIJE_EVIDENTIRANO,
    val exercise: TernaryStatus = TernaryStatus.NIJE_EVIDENTIRANO,
    val noTummyTime: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val stoolCount: Int? = null,
)

@Entity(tableName = "tummy_sessions")
data class TummySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val durationSeconds: Long,
    val inputMethod: TummyInputMethod,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val reminderEnabled: Boolean = true,
    val reminderTime: String = "18:00",
    val theme: AppTheme = AppTheme.SUSTAV,
    val onboardingShown: Boolean = false,
    val lastNotificationDate: String? = null,
)

data class DaySummary(
    val date: String,
    val totalMl: Int,
    val mealCount: Int,
    val averageMl: Double,
    val lastMealTime: String?,
    val waya: TernaryStatus,
    val exercise: TernaryStatus,
    val tummySeconds: Long,
    val tummyCount: Int,
    val noTummyTime: Boolean,
    val stoolCount: Int?,
    val status: DayStatus,
)

data class AppSnapshot(
    val meals: List<MealEntity>,
    val dailyEntries: List<DailyEntryEntity>,
    val tummySessions: List<TummySessionEntity>,
    val settings: SettingsEntity,
)
