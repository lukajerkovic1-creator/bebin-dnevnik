package hr.bebindnevnik.app.data

import androidx.room.withTransaction
import hr.bebindnevnik.app.domain.AppLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate
import java.time.LocalTime

class AppRepository(
    private val database: AppDatabase,
) {
    private val dao = database.dao()
    val meals: Flow<List<MealEntity>> = dao.observeMeals()
    val dailyEntries: Flow<List<DailyEntryEntity>> = dao.observeDailyEntries()
    val tummySessions: Flow<List<TummySessionEntity>> = dao.observeTummySessions()
    val settings: Flow<SettingsEntity> = dao.observeSettings().filterNotNull()
    val snapshot: Flow<AppSnapshot> = combine(meals, dailyEntries, tummySessions, settings, ::AppSnapshot)

    suspend fun initialize() {
        if (dao.settings() == null) dao.putSettings(SettingsEntity())
    }

    suspend fun addMeal(
        date: LocalDate,
        time: LocalTime,
        amount: Int,
    ): MealEntity {
        val now = System.currentTimeMillis()
        val item =
            MealEntity(date = date.toString(), time = time.withNano(0).toString(), amountMl = amount, createdAt = now, updatedAt = now)
        return item.copy(id = dao.insertMeal(item))
    }

    suspend fun updateMeal(item: MealEntity) = dao.updateMeal(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteMeal(item: MealEntity) = dao.deleteMeal(item)

    suspend fun restoreMeal(item: MealEntity) = dao.insertMeal(item)

    suspend fun setDailyStatus(
        date: LocalDate,
        waya: TernaryStatus? = null,
        exercise: TernaryStatus? = null,
        noTummy: Boolean? = null,
    ) {
        val key = date.toString()
        val old = dao.dailyEntry(key)
        val now = System.currentTimeMillis()
        dao.putDaily(
            DailyEntryEntity(
                date = key,
                waya = waya ?: old?.waya ?: TernaryStatus.NIJE_EVIDENTIRANO,
                exercise = exercise ?: old?.exercise ?: TernaryStatus.NIJE_EVIDENTIRANO,
                noTummyTime = noTummy ?: old?.noTummyTime ?: false,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun resetDailyStatuses(date: LocalDate) =
        setDailyStatus(
            date,
            TernaryStatus.NIJE_EVIDENTIRANO,
            TernaryStatus.NIJE_EVIDENTIRANO,
            false,
        )

    suspend fun markNoTummy(date: LocalDate): Boolean {
        if (dao.tummyCount(date.toString()) > 0) return false
        setDailyStatus(date, noTummy = true)
        return true
    }

    suspend fun addTummy(
        date: LocalDate,
        time: LocalTime,
        seconds: Long,
        method: TummyInputMethod,
    ): TummySessionEntity {
        val now = System.currentTimeMillis()
        val item =
            TummySessionEntity(
                date = date.toString(),
                time = time.withNano(0).toString(),
                durationSeconds = seconds,
                inputMethod = method,
                createdAt = now,
                updatedAt = now,
            )
        val id =
            database.withTransaction {
                setDailyStatus(date, noTummy = false)
                dao.insertTummy(item)
            }
        return item.copy(id = id)
    }

    suspend fun updateTummy(item: TummySessionEntity) {
        database.withTransaction {
            setDailyStatus(LocalDate.parse(item.date), noTummy = false)
            dao.updateTummy(item.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteTummy(item: TummySessionEntity) = dao.deleteTummy(item)

    suspend fun restoreTummy(item: TummySessionEntity) = dao.insertTummy(item)

    suspend fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) = dao.putSettings(transform(dao.settings() ?: SettingsEntity()))

    suspend fun currentSnapshot(): AppSnapshot =
        AppSnapshot(
            dao.allMeals(),
            dao.allDailyEntries(),
            dao.allTummySessions(),
            dao.settings() ?: SettingsEntity(),
        )

    suspend fun summary(date: LocalDate): DaySummary {
        val snapshot = currentSnapshot()
        return AppLogic.summary(date, snapshot.meals, snapshot.dailyEntries, snapshot.tummySessions)
    }

    suspend fun replaceAll(snapshot: AppSnapshot) =
        database.withTransaction {
            dao.deleteAllMeals()
            dao.deleteAllDailyEntries()
            dao.deleteAllTummySessions()
            dao.deleteAllSettings()
            dao.insertMeals(snapshot.meals)
            dao.insertDailyEntries(snapshot.dailyEntries)
            dao.insertTummySessions(snapshot.tummySessions)
            dao.putSettings(snapshot.settings.copy(id = 1, lastNotificationDate = null))
        }

    suspend fun deleteAll() =
        database.withTransaction {
            dao.deleteAllMeals()
            dao.deleteAllDailyEntries()
            dao.deleteAllTummySessions()
            dao.deleteAllSettings()
            dao.putSettings(SettingsEntity(onboardingShown = true))
        }
}
