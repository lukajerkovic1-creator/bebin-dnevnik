package hr.bebindnevnik.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM meals ORDER BY date DESC, time DESC, id DESC")
    fun observeMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM daily_entries ORDER BY date DESC")
    fun observeDailyEntries(): Flow<List<DailyEntryEntity>>

    @Query("SELECT * FROM tummy_sessions ORDER BY date DESC, time DESC, id DESC")
    fun observeTummySessions(): Flow<List<TummySessionEntity>>

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM meals")
    suspend fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM daily_entries")
    suspend fun allDailyEntries(): List<DailyEntryEntity>

    @Query("SELECT * FROM tummy_sessions")
    suspend fun allTummySessions(): List<TummySessionEntity>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?

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
    suspend fun insertMeals(meals: List<MealEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyEntries(entries: List<DailyEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTummySessions(sessions: List<TummySessionEntity>)

    @Query("DELETE FROM meals")
    suspend fun deleteAllMeals()

    @Query("DELETE FROM daily_entries")
    suspend fun deleteAllDailyEntries()

    @Query("DELETE FROM tummy_sessions")
    suspend fun deleteAllTummySessions()

    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()
}
