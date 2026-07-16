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
    private val onDataChanged: () -> Unit = {},
) {
    private val dao = database.dao()
    val meals: Flow<List<MealEntity>> = dao.observeMeals()
    val dailyEntries: Flow<List<DailyEntryEntity>> = dao.observeDailyEntries()
    val tummySessions: Flow<List<TummySessionEntity>> = dao.observeTummySessions()
    val settings: Flow<SettingsEntity> = dao.observeSettings().filterNotNull()
    val childProfile: Flow<ChildProfileEntity?> = dao.observeChildProfile()
    val growthMeasurements: Flow<List<GrowthMeasurementEntity>> = dao.observeGrowthMeasurements()
    val complementaryFoodMeals: Flow<List<ComplementaryFoodMealEntity>> = dao.observeComplementaryFoodMeals()
    private val diarySnapshot: Flow<AppSnapshot> = combine(meals, dailyEntries, tummySessions, settings, ::AppSnapshot)
    val snapshot: Flow<AppSnapshot> =
        combine(diarySnapshot, childProfile, growthMeasurements, complementaryFoodMeals) { diary, profile, growth, food ->
            diary.copy(childProfile = profile, growthMeasurements = growth, complementaryFoodMeals = food)
        }

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
        return item.copy(id = dao.insertMeal(item)).also { onDataChanged() }
    }

    suspend fun updateMeal(item: MealEntity) {
        dao.updateMeal(item.copy(updatedAt = System.currentTimeMillis()))
        onDataChanged()
    }

    suspend fun deleteMeal(item: MealEntity) {
        dao.deleteMeal(item)
        onDataChanged()
    }

    suspend fun restoreMeal(item: MealEntity) = dao.insertMeal(item).also { onDataChanged() }

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
                stoolCount = old?.stoolCount,
            ),
        )
        onDataChanged()
    }

    suspend fun setStoolCount(
        date: LocalDate,
        count: Int?,
    ) {
        require(!date.isAfter(LocalDate.now())) { "Nije dopušten unos za budući datum." }
        require(count == null || count >= 0) { "Broj stolica ne smije biti negativan." }
        val key = date.toString()
        val old = dao.dailyEntry(key)
        val now = System.currentTimeMillis()
        dao.putDaily(
            DailyEntryEntity(
                date = key,
                waya = old?.waya ?: TernaryStatus.NIJE_EVIDENTIRANO,
                exercise = old?.exercise ?: TernaryStatus.NIJE_EVIDENTIRANO,
                noTummyTime = old?.noTummyTime ?: false,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
                stoolCount = count,
            ),
        )
        onDataChanged()
    }

    suspend fun resetDailyStatuses(date: LocalDate) {
        setDailyStatus(
            date,
            TernaryStatus.NIJE_EVIDENTIRANO,
            TernaryStatus.NIJE_EVIDENTIRANO,
            false,
        )
        setStoolCount(date, null)
    }

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
        return item.copy(id = id).also { onDataChanged() }
    }

    suspend fun updateTummy(item: TummySessionEntity) {
        database.withTransaction {
            setDailyStatus(LocalDate.parse(item.date), noTummy = false)
            dao.updateTummy(item.copy(updatedAt = System.currentTimeMillis()))
        }
        onDataChanged()
    }

    suspend fun deleteTummy(item: TummySessionEntity) {
        dao.deleteTummy(item)
        onDataChanged()
    }

    suspend fun restoreTummy(item: TummySessionEntity) = dao.insertTummy(item).also { onDataChanged() }

    suspend fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) {
        dao.putSettings(transform(dao.settings() ?: SettingsEntity()))
        onDataChanged()
    }

    suspend fun saveChildProfile(profile: ChildProfileEntity) {
        val old = dao.childProfile()
        val now = System.currentTimeMillis()
        dao.putChildProfile(
            profile.copy(
                id = 1,
                createdAt = old?.createdAt ?: profile.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
            ),
        )
        onDataChanged()
    }

    suspend fun addGrowthMeasurement(measurement: GrowthMeasurementEntity): GrowthMeasurementEntity {
        val now = System.currentTimeMillis()
        val item = measurement.copy(id = 0, createdAt = now, updatedAt = now)
        return item.copy(id = dao.insertGrowthMeasurement(item)).also { onDataChanged() }
    }

    suspend fun updateGrowthMeasurement(measurement: GrowthMeasurementEntity) {
        dao.updateGrowthMeasurement(measurement.copy(updatedAt = System.currentTimeMillis()))
        onDataChanged()
    }

    suspend fun deleteGrowthMeasurement(measurement: GrowthMeasurementEntity) {
        dao.deleteGrowthMeasurement(measurement)
        onDataChanged()
    }

    suspend fun restoreGrowthMeasurement(measurement: GrowthMeasurementEntity) {
        dao.insertGrowthMeasurements(listOf(measurement))
        onDataChanged()
    }

    suspend fun addComplementaryFoodMeal(meal: ComplementaryFoodMealEntity): ComplementaryFoodMealEntity {
        val now = System.currentTimeMillis()
        val item = meal.copy(id = 0, createdAt = now, updatedAt = now)
        return item.copy(id = dao.insertComplementaryFoodMeal(item)).also { onDataChanged() }
    }

    suspend fun updateComplementaryFoodMeal(meal: ComplementaryFoodMealEntity) {
        dao.updateComplementaryFoodMeal(meal.copy(updatedAt = System.currentTimeMillis()))
        onDataChanged()
    }

    suspend fun deleteComplementaryFoodMeal(meal: ComplementaryFoodMealEntity) {
        dao.deleteComplementaryFoodMeal(meal)
        onDataChanged()
    }

    suspend fun restoreComplementaryFoodMeal(meal: ComplementaryFoodMealEntity) {
        dao.insertComplementaryFoodMeals(listOf(meal))
        onDataChanged()
    }

    suspend fun deleteGrowthProfileAndMeasurements() =
        database
            .withTransaction {
                dao.deleteAllGrowthMeasurements()
                dao.deleteChildProfile()
            }.also { onDataChanged() }

    suspend fun currentSnapshot(): AppSnapshot =
        AppSnapshot(
            dao.allMeals(),
            dao.allDailyEntries(),
            dao.allTummySessions(),
            dao.settings() ?: SettingsEntity(),
            dao.childProfile(),
            dao.allGrowthMeasurements(),
            dao.allComplementaryFoodMeals(),
        )

    suspend fun summary(date: LocalDate): DaySummary {
        val snapshot = currentSnapshot()
        return AppLogic.summary(date, snapshot.meals, snapshot.dailyEntries, snapshot.tummySessions)
    }

    suspend fun replaceAll(snapshot: AppSnapshot) =
        database
            .withTransaction {
                require(
                    snapshot.complementaryFoodMeals.all { meal ->
                        meal.amount >= 0 && meal.ingredients.any { it.isNotBlank() }
                    },
                ) { "Sigurnosna kopija sadrži nevažeći obrok dohrane." }
                dao.deleteAllMeals()
                dao.deleteAllDailyEntries()
                dao.deleteAllTummySessions()
                dao.deleteAllSettings()
                dao.deleteAllGrowthMeasurements()
                dao.deleteAllComplementaryFoodMeals()
                dao.deleteChildProfile()
                dao.insertMeals(snapshot.meals)
                dao.insertDailyEntries(snapshot.dailyEntries)
                dao.insertTummySessions(snapshot.tummySessions)
                dao.putSettings(snapshot.settings.copy(id = 1, lastNotificationDate = null))
                snapshot.childProfile?.let { dao.putChildProfile(it.copy(id = 1)) }
                dao.insertGrowthMeasurements(snapshot.growthMeasurements)
                dao.insertComplementaryFoodMeals(snapshot.complementaryFoodMeals)
            }.also { onDataChanged() }

    suspend fun deleteAll() =
        database
            .withTransaction {
                dao.deleteAllMeals()
                dao.deleteAllDailyEntries()
                dao.deleteAllTummySessions()
                dao.deleteAllSettings()
                dao.deleteAllGrowthMeasurements()
                dao.deleteAllComplementaryFoodMeals()
                dao.deleteChildProfile()
                dao.putSettings(SettingsEntity(onboardingShown = true))
            }.also { onDataChanged() }
}
