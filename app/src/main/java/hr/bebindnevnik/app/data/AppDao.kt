package hr.bebindnevnik.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface AppDao {
    @Query("SELECT * FROM meals ORDER BY date DESC, time DESC, id DESC")
    fun observeMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM daily_entries ORDER BY date DESC")
    fun observeDailyEntries(): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM tummy_sessions ORDER BY date DESC, time DESC, id DESC")
    fun observeTummySessions(): Flow<List<TummySessionEntity>>

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM child_profile WHERE id = 1")
    fun observeChildProfile(): Flow<ChildProfileEntity?>

    @Query("SELECT * FROM growth_measurements ORDER BY date DESC, time DESC, id DESC")
    fun observeGrowthMeasurements(): Flow<List<GrowthMeasurementEntity>>

    @Query("SELECT * FROM complementary_food_meals ORDER BY date DESC, time DESC, id DESC")
    fun observeComplementaryFoodMeals(): Flow<List<ComplementaryFoodMealEntity>>

    @Query("SELECT * FROM milk_completeness_history ORDER BY startDate DESC, id DESC")
    fun observeMilkCompletenessHistory(): Flow<List<MilkCompletenessEntity>>

    @Query("SELECT * FROM expected_meal_count_history ORDER BY startDate DESC, id DESC")
    fun observeExpectedMealCountHistory(): Flow<List<ExpectedMealCountEntity>>

    @Query("SELECT * FROM individual_feeding_targets ORDER BY startDate DESC, id DESC")
    fun observeIndividualFeedingTargets(): Flow<List<IndividualFeedingTargetEntity>>

    @Query("SELECT * FROM individual_tummy_targets ORDER BY startDate DESC, id DESC")
    fun observeIndividualTummyTargets(): Flow<List<IndividualTummyTargetEntity>>

    @Query("SELECT * FROM meals")
    suspend fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM daily_entries")
    suspend fun allDailyEntries(): List<DailyEntryEntity>

    @Query("SELECT * FROM tummy_sessions")
    suspend fun allTummySessions(): List<TummySessionEntity>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?

    @Query("SELECT * FROM child_profile WHERE id = 1")
    suspend fun childProfile(): ChildProfileEntity?

    @Query("SELECT * FROM growth_measurements ORDER BY date DESC, time DESC, id DESC")
    suspend fun allGrowthMeasurements(): List<GrowthMeasurementEntity>

    @Query("SELECT * FROM complementary_food_meals ORDER BY date DESC, time DESC, id DESC")
    suspend fun allComplementaryFoodMeals(): List<ComplementaryFoodMealEntity>

    @Query("SELECT * FROM milk_completeness_history ORDER BY startDate DESC, id DESC")
    suspend fun allMilkCompletenessHistory(): List<MilkCompletenessEntity>

    @Query("SELECT * FROM expected_meal_count_history ORDER BY startDate DESC, id DESC")
    suspend fun allExpectedMealCountHistory(): List<ExpectedMealCountEntity>

    @Query("SELECT * FROM individual_feeding_targets ORDER BY startDate DESC, id DESC")
    suspend fun allIndividualFeedingTargets(): List<IndividualFeedingTargetEntity>

    @Query("SELECT * FROM individual_tummy_targets ORDER BY startDate DESC, id DESC")
    suspend fun allIndividualTummyTargets(): List<IndividualTummyTargetEntity>

    @Query("SELECT * FROM daily_entries WHERE date = :date")
    suspend fun dailyEntry(date: String): DailyEntryEntity?

    @Query("SELECT COUNT(*) FROM meals WHERE date = :date")
    suspend fun mealCount(date: String): Int

    @Query("SELECT COUNT(*) FROM tummy_sessions WHERE date = :date")
    suspend fun tummyCount(date: String): Int

    @Insert suspend fun insertMeal(meal: MealEntity): Long

    @Update suspend fun updateMeal(meal: MealEntity)

    @Delete suspend fun deleteMeal(meal: MealEntity)

    @Insert suspend fun insertTummy(session: TummySessionEntity): Long

    @Update suspend fun updateTummy(session: TummySessionEntity)

    @Delete suspend fun deleteTummy(session: TummySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDaily(entry: DailyEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(settings: SettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putChildProfile(profile: ChildProfileEntity)

    @Insert
    suspend fun insertGrowthMeasurement(measurement: GrowthMeasurementEntity): Long

    @Update
    suspend fun updateGrowthMeasurement(measurement: GrowthMeasurementEntity)

    @Delete
    suspend fun deleteGrowthMeasurement(measurement: GrowthMeasurementEntity)

    @Insert
    suspend fun insertComplementaryFoodMeal(meal: ComplementaryFoodMealEntity): Long

    @Update
    suspend fun updateComplementaryFoodMeal(meal: ComplementaryFoodMealEntity)

    @Delete
    suspend fun deleteComplementaryFoodMeal(meal: ComplementaryFoodMealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMilkCompleteness(item: MilkCompletenessEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putExpectedMealCount(item: ExpectedMealCountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putIndividualFeedingTarget(item: IndividualFeedingTargetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putIndividualTummyTarget(item: IndividualTummyTargetEntity): Long

    @Delete suspend fun deleteIndividualFeedingTarget(item: IndividualFeedingTargetEntity)

    @Delete suspend fun deleteIndividualTummyTarget(item: IndividualTummyTargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeals(meals: List<MealEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyEntries(entries: List<DailyEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTummySessions(sessions: List<TummySessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrowthMeasurements(measurements: List<GrowthMeasurementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplementaryFoodMeals(meals: List<ComplementaryFoodMealEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilkCompletenessHistory(items: List<MilkCompletenessEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpectedMealCountHistory(items: List<ExpectedMealCountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndividualFeedingTargets(items: List<IndividualFeedingTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndividualTummyTargets(items: List<IndividualTummyTargetEntity>)

    @Query("DELETE FROM meals")
    suspend fun deleteAllMeals()

    @Query("DELETE FROM daily_entries")
    suspend fun deleteAllDailyEntries()

    @Query("DELETE FROM tummy_sessions")
    suspend fun deleteAllTummySessions()

    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()

    @Query("DELETE FROM child_profile")
    suspend fun deleteChildProfile()

    @Query("DELETE FROM growth_measurements")
    suspend fun deleteAllGrowthMeasurements()

    @Query("DELETE FROM complementary_food_meals")
    suspend fun deleteAllComplementaryFoodMeals()

    @Query("DELETE FROM milk_completeness_history")
    suspend fun deleteAllMilkCompletenessHistory()

    @Query("DELETE FROM expected_meal_count_history")
    suspend fun deleteAllExpectedMealCountHistory()

    @Query("DELETE FROM individual_feeding_targets")
    suspend fun deleteAllIndividualFeedingTargets()

    @Query("DELETE FROM individual_tummy_targets")
    suspend fun deleteAllIndividualTummyTargets()
}
