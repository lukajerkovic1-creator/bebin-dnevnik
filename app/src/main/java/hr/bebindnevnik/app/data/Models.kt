package hr.bebindnevnik.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TernaryStatus { NIJE_EVIDENTIRANO, DA, NE }

enum class TummyInputMethod { STOPERICA, RUCNO }

enum class AppTheme { SUSTAV, SVIJETLA, TAMNA }

enum class DayStatus { POTPUNO, DJELOMICNO, BEZ_PODATAKA }

enum class ChildSex { DJEVOJCICA, DJECAK }

enum class LengthMeasurementType { LEZECA_DULJINA, STOJECA_VISINA }

enum class ComplementaryFoodUnit { G, ML }

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

@Entity(tableName = "child_profile")
data class ChildProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val sex: ChildSex,
    val birthDate: String,
    val gestationalWeeks: Int,
    val gestationalDays: Int,
    val birthWeightG: Int? = null,
    val birthLengthCm: Double? = null,
    val birthHeadCircumferenceCm: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "growth_measurements",
    indices = [Index(value = ["date", "time"], name = "index_growth_measurements_date_time")],
)
data class GrowthMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val weightG: Int? = null,
    val lengthHeightCm: Double? = null,
    val lengthMeasurementType: LengthMeasurementType = LengthMeasurementType.LEZECA_DULJINA,
    val headCircumferenceCm: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "complementary_food_meals",
    indices = [Index(value = ["date", "time"], name = "index_complementary_food_meals_date_time")],
)
data class ComplementaryFoodMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val ingredients: List<String>,
    val amount: Int,
    val unit: ComplementaryFoodUnit = ComplementaryFoodUnit.G,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ComplementaryFoodDaySummary(
    val mealCount: Int,
    val totalG: Int,
    val totalMl: Int,
    val lastMeal: ComplementaryFoodMealEntity?,
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
    val childProfile: ChildProfileEntity? = null,
    val growthMeasurements: List<GrowthMeasurementEntity> = emptyList(),
    val complementaryFoodMeals: List<ComplementaryFoodMealEntity> = emptyList(),
)
