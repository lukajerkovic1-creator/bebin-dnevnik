package hr.bebindnevnik.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hr.bebindnevnik.app.AppContainer
import hr.bebindnevnik.app.backup.CsvExporter
import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ComplementaryFoodDaySummary
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.DaySummary
import hr.bebindnevnik.app.data.ExpectedMealCountEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.IndividualFeedingTargetEntity
import hr.bebindnevnik.app.data.IndividualTummyTargetEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.MilkCompletenessEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import hr.bebindnevnik.app.domain.AppLogic
import hr.bebindnevnik.app.domain.ComplementaryFoodLogic
import hr.bebindnevnik.app.domain.ComplementaryFoodValidation
import hr.bebindnevnik.app.domain.EntryWarning
import hr.bebindnevnik.app.domain.LocalDayClock
import hr.bebindnevnik.app.domain.StatisticsCalculator
import hr.bebindnevnik.app.domain.StatisticsReport
import hr.bebindnevnik.app.domain.StatisticsSelection
import hr.bebindnevnik.app.domain.growth.GrowthAssessment
import hr.bebindnevnik.app.domain.growth.GrowthIndicator
import hr.bebindnevnik.app.domain.growth.GrowthReferenceLine
import hr.bebindnevnik.app.domain.growth.GrowthValidation
import hr.bebindnevnik.app.domain.guidelines.DailyGuidelineResult
import hr.bebindnevnik.app.domain.guidelines.GuidelineEngine
import hr.bebindnevnik.app.domain.resolveLocalDayTransition
import hr.bebindnevnik.app.notifications.TimerCancelReason
import hr.bebindnevnik.app.notifications.TimerEvent
import hr.bebindnevnik.app.notifications.TimerPhase
import hr.bebindnevnik.app.notifications.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class UiState(
    val meals: List<MealEntity> = emptyList(),
    val entries: List<DailyEntryEntity> = emptyList(),
    val sessions: List<TummySessionEntity> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val selectedDate: LocalDate = LocalDate.now(),
    val currentLocalDate: LocalDate = LocalDate.now(),
    val pastDateEditMode: Boolean = false,
    val rolloverPreviousDate: LocalDate? = null,
    val nowMinute: Long = 0,
    val childProfile: ChildProfileEntity? = null,
    val growthMeasurements: List<GrowthMeasurementEntity> = emptyList(),
    val complementaryFoodMeals: List<ComplementaryFoodMealEntity> = emptyList(),
    val milkCompletenessHistory: List<MilkCompletenessEntity> = emptyList(),
    val expectedMealCountHistory: List<ExpectedMealCountEntity> = emptyList(),
    val individualFeedingTargets: List<IndividualFeedingTargetEntity> = emptyList(),
    val individualTummyTargets: List<IndividualTummyTargetEntity> = emptyList(),
    val guidelineResult: DailyGuidelineResult =
        GuidelineEngine.calculate(AppSnapshot(emptyList(), emptyList(), emptyList(), SettingsEntity()), LocalDate.now(), LocalDate.now()),
) {
    val selectedMeals get() = meals.filter { it.date == selectedDate.toString() }
    val selectedSessions get() = sessions.filter { it.date == selectedDate.toString() }
    val selectedComplementaryFoodMeals get() = complementaryFoodMeals.filter { it.date == selectedDate.toString() }
    val complementaryFoodSummary: ComplementaryFoodDaySummary get() =
        ComplementaryFoodLogic.daySummary(selectedDate, complementaryFoodMeals)
    val summary: DaySummary get() = AppLogic.summary(selectedDate, meals, entries, sessions)
    val isPastDate: Boolean get() = selectedDate.isBefore(currentLocalDate)
    val isFutureDate: Boolean get() = selectedDate.isAfter(currentLocalDate)
    val canEditSelectedDate: Boolean get() = !isFutureDate && (!isPastDate || pastDateEditMode)
}

sealed interface UiMessage {
    data class Text(
        val value: String,
    ) : UiMessage

    data class MealDeleted(
        val item: MealEntity,
    ) : UiMessage

    data class TummyDeleted(
        val item: TummySessionEntity,
    ) : UiMessage

    data class GrowthDeleted(
        val item: GrowthMeasurementEntity,
    ) : UiMessage

    data class ComplementaryFoodDeleted(
        val item: ComplementaryFoodMealEntity,
    ) : UiMessage
}

@Suppress("TooManyFunctions")
class MainViewModel(
    private val container: AppContainer,
    private val localDayClock: LocalDayClock = LocalDayClock(),
) : ViewModel() {
    private val currentLocalDate = MutableStateFlow(localDayClock.today())
    private val selectedDate = MutableStateFlow(currentLocalDate.value)
    private val pastDateEditMode = MutableStateFlow(false)
    private val rolloverPreviousDate = MutableStateFlow<LocalDate?>(null)
    private val editorOpen = MutableStateFlow(false)
    private val editorDate = MutableStateFlow<LocalDate?>(null)
    private val mutableStatisticsSelection = MutableStateFlow(StatisticsSelection())
    private val minute = MutableStateFlow(System.currentTimeMillis() / 60_000)
    private val mutableGrowthUnlockedId = MutableStateFlow<Long?>(null)
    private var midnightJob: Job? = null
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val highlight = MutableStateFlow<Set<String>>(emptySet())
    val timer: StateFlow<TimerState> = container.timerController.state
    val statisticsSelection: StateFlow<StatisticsSelection> = mutableStatisticsSelection.asStateFlow()
    val growthUnlockedId: StateFlow<Long?> = mutableGrowthUnlockedId.asStateFlow()

    val state: StateFlow<UiState> =
        combine(
            container.repository.snapshot,
            selectedDate,
            currentLocalDate,
            pastDateEditMode,
            rolloverPreviousDate,
            minute,
        ) { values ->
            val snapshot = values[0] as AppSnapshot
            UiState(
                meals = snapshot.meals,
                entries = snapshot.dailyEntries,
                sessions = snapshot.tummySessions,
                settings = snapshot.settings,
                selectedDate = values[1] as LocalDate,
                currentLocalDate = values[2] as LocalDate,
                pastDateEditMode = values[3] as Boolean,
                rolloverPreviousDate = values[4] as LocalDate?,
                nowMinute = values[5] as Long,
                childProfile = snapshot.childProfile,
                growthMeasurements = snapshot.growthMeasurements,
                complementaryFoodMeals = snapshot.complementaryFoodMeals,
                milkCompletenessHistory = snapshot.milkCompletenessHistory,
                expectedMealCountHistory = snapshot.expectedMealCountHistory,
                individualFeedingTargets = snapshot.individualFeedingTargets,
                individualTummyTargets = snapshot.individualTummyTargets,
                guidelineResult = GuidelineEngine.calculate(snapshot, values[1] as LocalDate, values[2] as LocalDate),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val statisticsReport: StateFlow<StatisticsReport> =
        combine(container.repository.snapshot, mutableStatisticsSelection, currentLocalDate) { snapshot, selection, today ->
            val base =
                StatisticsCalculator.calculate(
                    selection = selection,
                    today = today,
                    meals = snapshot.meals,
                    entries = snapshot.dailyEntries,
                    sessions = snapshot.tummySessions,
                    complementaryFoodMeals = snapshot.complementaryFoodMeals,
                )
            base.copy(
                guideline =
                    GuidelineEngine.statistics(
                        snapshot,
                        base.start,
                        base.end,
                        today,
                    ),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                StatisticsCalculator.calculate(
                    StatisticsSelection(),
                    currentLocalDate.value,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )

    init {
        viewModelScope.launch {
            while (true) {
                minute.value = System.currentTimeMillis() / 60_000
                delay(30_000)
            }
        }
        scheduleMidnightCheck()
        viewModelScope.launch {
            container.timerController.events.collect { event ->
                when (event) {
                    is TimerEvent.Saved -> {
                        messages.emit(UiMessage.Text("Tummy-time sesija spremljena."))
                    }

                    is TimerEvent.Cancelled -> {
                        when (event.reason) {
                            TimerCancelReason.DATE_CHANGED -> {
                                messages.emit(UiMessage.Text("Aktivna tummy-time sesija poništena je zbog promjene datuma."))
                            }

                            TimerCancelReason.BACKGROUNDED -> {
                                messages.emit(UiMessage.Text("Aktivna tummy-time sesija poništena je jer je aplikacija napustila zaslon."))
                            }

                            TimerCancelReason.USER -> {
                                Unit
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        refreshLocalDate()
        if (!date.isAfter(currentLocalDate.value)) {
            if (date != selectedDate.value && timer.value.phase != TimerPhase.IDLE) {
                container.timerController.cancel(TimerCancelReason.DATE_CHANGED)
            }
            pastDateEditMode.value = false
            selectedDate.value = date
        }
    }

    fun enablePastDateEditing() {
        if (selectedDate.value.isBefore(currentLocalDate.value)) pastDateEditMode.value = true
    }

    fun finishPastDateEditing() {
        pastDateEditMode.value = false
    }

    fun setEditorOpen(
        open: Boolean,
        date: LocalDate? = null,
    ) {
        editorOpen.value = open
        editorDate.value = date.takeIf { open }
    }

    fun acknowledgeDateRollover() {
        rolloverPreviousDate.value = null
    }

    fun onForegrounded() {
        refreshLocalDate()
        scheduleMidnightCheck()
    }

    fun onSystemDateTimeChanged() {
        refreshLocalDate()
        scheduleMidnightCheck()
    }

    fun onMainScreenChanged(route: String?) {
        if (route != "today") pastDateEditMode.value = false
        if (route != "growth") mutableGrowthUnlockedId.value = null
    }

    fun selectStatisticsRange(selection: StatisticsSelection) {
        mutableStatisticsSelection.value = selection
    }

    fun startTimer() {
        if (selectedDate.value == currentLocalDate.value) container.timerController.start()
    }

    fun stopTimer() = container.timerController.stopAndSave()

    fun cancelTimer() = container.timerController.cancel()

    fun confirmTimer() = container.timerController.confirmSave()

    fun onBackgrounded() {
        pastDateEditMode.value = false
        mutableGrowthUnlockedId.value = null
        midnightJob?.cancel()
        container.timerController.onBackgrounded()
    }

    fun mealWarnings(
        amount: Int,
        date: LocalDate,
        time: LocalTime,
        editedId: Long = 0,
    ): Set<EntryWarning> = AppLogic.mealWarnings(amount, date, time, state.value.meals, editedId)

    fun tummyWarnings(
        seconds: Long,
        date: LocalDate,
        time: LocalTime,
    ): Set<EntryWarning> = AppLogic.tummyWarnings(seconds, date, time)

    fun saveMeal(
        id: Long,
        date: LocalDate,
        time: LocalTime,
        amount: Int,
    ): Job {
        val authorized = canMutate(date)
        return viewModelScope.launch {
            if (!authorized) return@launch
            if (id == 0L) {
                container.repository.addMeal(date, time, amount)
            } else {
                val old = state.value.meals.first { it.id == id }
                container.repository.updateMeal(old.copy(date = date.toString(), time = time.withNano(0).toString(), amountMl = amount))
            }
            checkCompleteness()
        }
    }

    fun deleteMeal(item: MealEntity) =
        viewModelScope.launch {
            if (!canMutate(LocalDate.parse(item.date))) return@launch
            container.repository.deleteMeal(item)
            messages.emit(UiMessage.MealDeleted(item))
        }

    fun deleteTummy(item: TummySessionEntity) =
        viewModelScope.launch {
            if (!canMutate(LocalDate.parse(item.date))) return@launch
            container.repository.deleteTummy(item)
            messages.emit(UiMessage.TummyDeleted(item))
        }

    fun undo(message: UiMessage) =
        viewModelScope.launch {
            when (message) {
                is UiMessage.MealDeleted -> container.repository.restoreMeal(message.item)
                is UiMessage.TummyDeleted -> container.repository.restoreTummy(message.item)
                is UiMessage.GrowthDeleted -> container.repository.restoreGrowthMeasurement(message.item)
                is UiMessage.ComplementaryFoodDeleted -> container.repository.restoreComplementaryFoodMeal(message.item)
                is UiMessage.Text -> Unit
            }
        }

    fun setWaya(status: TernaryStatus) =
        viewModelScope.launch {
            if (!canMutate(selectedDate.value)) return@launch
            container.repository.setDailyStatus(selectedDate.value, waya = status)
            checkCompleteness()
        }

    fun setExercise(status: TernaryStatus) =
        viewModelScope.launch {
            if (!canMutate(selectedDate.value)) return@launch
            container.repository.setDailyStatus(selectedDate.value, exercise = status)
            checkCompleteness()
        }

    fun stoolWarnings(
        count: Int,
        date: LocalDate = selectedDate.value,
    ): Set<EntryWarning> = AppLogic.stoolWarnings(count, date)

    fun setStoolCount(
        count: Int?,
        date: LocalDate = selectedDate.value,
    ): Job {
        val authorized = canMutate(date)
        return viewModelScope.launch {
            if (!authorized) return@launch
            container.repository.setStoolCount(date, count)
            checkCompleteness()
        }
    }

    fun resetStatuses() =
        viewModelScope.launch {
            if (canMutate(selectedDate.value)) container.repository.resetDailyStatuses(selectedDate.value)
        }

    fun markNoTummy() =
        viewModelScope.launch {
            if (!canMutate(selectedDate.value)) return@launch
            if (!container.repository.markNoTummy(
                    selectedDate.value,
                )
            ) {
                messages.emit(UiMessage.Text("Sesije već postoje; prvo ih pojedinačno uredite ili izbrišite."))
            }
            checkCompleteness()
        }

    fun saveTummy(
        id: Long,
        date: LocalDate,
        time: LocalTime,
        seconds: Long,
    ): Job {
        val authorized = canMutate(date)
        return viewModelScope.launch {
            if (!authorized) return@launch
            if (id == 0L) {
                container.repository.addTummy(date, time, seconds, TummyInputMethod.RUCNO)
            } else {
                val old = state.value.sessions.first { it.id == id }
                container.repository.updateTummy(
                    old.copy(date = date.toString(), time = time.withNano(0).toString(), durationSeconds = seconds),
                )
            }
            checkCompleteness()
        }
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch { container.repository.updateSettings { it.copy(theme = theme) } }

    fun setGuidelineTargets(enabled: Boolean) = viewModelScope.launch { container.repository.updateSettings { it.copy(guidelineTargetsEnabled = enabled) } }

    fun dismissGuidelineWizard() =
        viewModelScope.launch {
            container.repository.updateSettings { it.copy(guidelineWizardDismissed = true) }
        }

    fun completeGuidelineWizard() =
        viewModelScope.launch {
            container.repository.updateSettings {
                it.copy(guidelineWizardCompleted = true, guidelineWizardDismissed = false)
            }
        }

    fun restartGuidelineWizard() =
        viewModelScope.launch {
            container.repository.updateSettings {
                it.copy(guidelineWizardCompleted = false, guidelineWizardDismissed = false)
            }
        }

    fun setMilkEvidenceComplete(
        complete: Boolean,
        startDate: LocalDate = state.value.selectedDate,
    ): Job =
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val history = state.value.milkCompletenessHistory
            val sameDay = history.firstOrNull { it.startDate == startDate.toString() }
            val nextStart =
                history
                    .map { LocalDate.parse(it.startDate) }
                    .filter { it.isAfter(startDate) }
                    .minOrNull()
            history
                .filter {
                    val start = LocalDate.parse(it.startDate)
                    val end = it.endDate?.let(LocalDate::parse)
                    sameDay == null && !start.isAfter(startDate) && (end == null || !end.isBefore(startDate))
                }.forEach { container.repository.saveMilkCompleteness(it.copy(endDate = startDate.minusDays(1).toString())) }
            container.repository.saveMilkCompleteness(
                (sameDay ?: MilkCompletenessEntity(startDate = startDate.toString(), complete = complete, createdAt = now, updatedAt = now))
                    .copy(complete = complete, endDate = nextStart?.minusDays(1)?.toString()),
            )
        }

    fun setExpectedMealCount(
        count: Int,
        startDate: LocalDate = state.value.selectedDate,
    ): Job =
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val history = state.value.expectedMealCountHistory
            val sameDay = history.firstOrNull { it.startDate == startDate.toString() }
            val nextStart =
                history
                    .map { LocalDate.parse(it.startDate) }
                    .filter { it.isAfter(startDate) }
                    .minOrNull()
            history
                .filter {
                    val start = LocalDate.parse(it.startDate)
                    val end = it.endDate?.let(LocalDate::parse)
                    sameDay == null && !start.isAfter(startDate) && (end == null || !end.isBefore(startDate))
                }.forEach { container.repository.saveExpectedMealCount(it.copy(endDate = startDate.minusDays(1).toString())) }
            container.repository.saveExpectedMealCount(
                (sameDay ?: ExpectedMealCountEntity(startDate = startDate.toString(), mealCount = count, createdAt = now, updatedAt = now))
                    .copy(mealCount = count, endDate = nextStart?.minusDays(1)?.toString()),
            )
        }

    fun saveIndividualFeedingTarget(target: IndividualFeedingTargetEntity): Job =
        viewModelScope.launch {
            val start = LocalDate.parse(target.startDate)
            val end = target.endDate?.let(LocalDate::parse)
            val conflict =
                state.value.individualFeedingTargets.any {
                    it.id != target.id &&
                        GuidelineEngine.intervalsOverlap(
                            start,
                            end,
                            LocalDate.parse(it.startDate),
                            it.endDate?.let(LocalDate::parse),
                        )
                }
            if (conflict) {
                messages.emit(UiMessage.Text("Individualni ciljevi hranjenja ne smiju se vremenski preklapati."))
            } else {
                container.repository.saveIndividualFeedingTarget(target)
            }
        }

    fun deleteIndividualFeedingTarget(target: IndividualFeedingTargetEntity): Job = viewModelScope.launch { container.repository.deleteIndividualFeedingTarget(target) }

    fun saveIndividualTummyTarget(target: IndividualTummyTargetEntity): Job =
        viewModelScope.launch {
            val start = LocalDate.parse(target.startDate)
            val end = target.endDate?.let(LocalDate::parse)
            val conflict =
                state.value.individualTummyTargets.any {
                    it.id != target.id &&
                        GuidelineEngine.intervalsOverlap(
                            start,
                            end,
                            LocalDate.parse(it.startDate),
                            it.endDate?.let(LocalDate::parse),
                        )
                }
            if (conflict) {
                messages.emit(UiMessage.Text("Individualni tummy-time ciljevi ne smiju se vremenski preklapati."))
            } else {
                container.repository.saveIndividualTummyTarget(target)
            }
        }

    fun deleteIndividualTummyTarget(target: IndividualTummyTargetEntity): Job = viewModelScope.launch { container.repository.deleteIndividualTummyTarget(target) }

    fun setIndependentMobilityDate(date: LocalDate?): Job =
        viewModelScope.launch {
            val profile = state.value.childProfile ?: return@launch
            container.repository.saveChildProfile(profile.copy(independentMobilityDate = date?.toString()))
        }

    fun saveChildProfile(profile: ChildProfileEntity): Job =
        viewModelScope.launch {
            val validation = GrowthValidation.profile(profile, currentLocalDate.value)
            if (!validation.valid) {
                messages.emit(UiMessage.Text(validation.errors.first()))
                return@launch
            }
            container.repository.saveChildProfile(profile.copy(name = profile.name.trim()))
            mutableGrowthUnlockedId.value = null
            messages.emit(UiMessage.Text("Profil djeteta je spremljen."))
        }

    fun assessGrowth(
        profile: ChildProfileEntity,
        measurement: GrowthMeasurementEntity,
        useCorrectedWhoAge: Boolean = true,
    ): GrowthAssessment = container.growthCalculator.assess(profile, measurement, useCorrectedWhoAge)

    fun growthAgeReferenceLines(
        profile: ChildProfileEntity,
        indicator: GrowthIndicator,
        maxAgeDays: Int,
        useCorrectedWhoAge: Boolean,
    ): List<GrowthReferenceLine> = container.growthCalculator.ageReferenceLines(profile, indicator, maxAgeDays, useCorrectedWhoAge)

    fun growthMeasureReferenceLines(
        profile: ChildProfileEntity,
        useLengthTable: Boolean,
    ): List<GrowthReferenceLine> = container.growthCalculator.measureReferenceLines(profile, useLengthTable)

    fun saveGrowthMeasurement(measurement: GrowthMeasurementEntity): Job =
        viewModelScope.launch {
            val profile = state.value.childProfile ?: return@launch
            val validation = GrowthValidation.measurement(profile, measurement, currentLocalDate.value, LocalTime.now())
            if (!validation.valid) {
                messages.emit(UiMessage.Text(validation.errors.first()))
                return@launch
            }
            val original = state.value.growthMeasurements.firstOrNull { it.id == measurement.id }
            if (original != null && LocalDate.parse(original.date).isBefore(currentLocalDate.value) && mutableGrowthUnlockedId.value != original.id) {
                messages.emit(UiMessage.Text("Prvo otključajte prošlo mjerenje za uređivanje."))
                return@launch
            }
            if (measurement.id == 0L) {
                container.repository.addGrowthMeasurement(measurement)
            } else {
                container.repository.updateGrowthMeasurement(measurement)
            }
            mutableGrowthUnlockedId.value = null
            messages.emit(UiMessage.Text("Mjerenje je spremljeno."))
        }

    fun unlockGrowthMeasurement(id: Long) {
        mutableGrowthUnlockedId.value = id
    }

    fun complementaryFoodValidation(
        ingredients: List<String>,
        amount: Int?,
        date: LocalDate,
        time: LocalTime,
        editedId: Long = 0,
    ): ComplementaryFoodValidation =
        ComplementaryFoodLogic.validate(
            ingredients,
            amount,
            date,
            time,
            currentLocalDate.value,
            LocalTime.now(),
            state.value.complementaryFoodMeals,
            editedId,
        )

    fun complementaryFoodSuggestions(): List<String> = ComplementaryFoodLogic.suggestions(state.value.complementaryFoodMeals)

    fun saveComplementaryFoodMeal(meal: ComplementaryFoodMealEntity): Job {
        val date = LocalDate.parse(meal.date)
        val authorized = canMutate(date)
        return viewModelScope.launch {
            if (!authorized) return@launch
            val validation =
                complementaryFoodValidation(meal.ingredients, meal.amount, date, LocalTime.parse(meal.time), meal.id)
            if (!validation.valid) {
                messages.emit(UiMessage.Text(validation.error.orEmpty()))
                return@launch
            }
            val normalized = meal.copy(ingredients = ComplementaryFoodLogic.normalizeIngredients(meal.ingredients))
            if (meal.id == 0L) {
                container.repository.addComplementaryFoodMeal(normalized)
            } else {
                container.repository.updateComplementaryFoodMeal(normalized)
            }
            messages.emit(UiMessage.Text("Obrok dohrane je spremljen."))
        }
    }

    fun deleteComplementaryFoodMeal(item: ComplementaryFoodMealEntity): Job =
        viewModelScope.launch {
            if (!canMutate(LocalDate.parse(item.date))) return@launch
            container.repository.deleteComplementaryFoodMeal(item)
            messages.emit(UiMessage.ComplementaryFoodDeleted(item))
        }

    fun lockGrowthMeasurement() {
        mutableGrowthUnlockedId.value = null
    }

    fun deleteGrowthMeasurement(item: GrowthMeasurementEntity): Job =
        viewModelScope.launch {
            if (LocalDate.parse(item.date).isBefore(currentLocalDate.value) && mutableGrowthUnlockedId.value != item.id) {
                messages.emit(UiMessage.Text("Prvo otključajte prošlo mjerenje za brisanje."))
                return@launch
            }
            container.repository.deleteGrowthMeasurement(item)
            mutableGrowthUnlockedId.value = null
            messages.emit(UiMessage.GrowthDeleted(item))
        }

    fun deleteGrowthProfileAndMeasurements(): Job =
        viewModelScope.launch {
            container.repository.deleteGrowthProfileAndMeasurements()
            mutableGrowthUnlockedId.value = null
            messages.emit(UiMessage.Text("Profil i mjerenja rasta su izbrisani."))
        }

    fun setReminder(enabled: Boolean) =
        viewModelScope.launch {
            container.repository.updateSettings { it.copy(reminderEnabled = enabled) }
            container.reminderScheduler.schedule(enabled, LocalTime.parse(state.value.settings.reminderTime))
        }

    fun setReminderTime(time: LocalTime) =
        viewModelScope.launch {
            container.repository.updateSettings { it.copy(reminderTime = time.withSecond(0).withNano(0).toString()) }
            container.reminderScheduler.schedule(state.value.settings.reminderEnabled, time)
        }

    fun finishOnboarding() = viewModelScope.launch { container.repository.updateSettings { it.copy(onboardingShown = true) } }

    fun replaceAll(snapshot: AppSnapshot) =
        viewModelScope.launch {
            container.repository.replaceAll(snapshot)
            val settings = snapshot.settings
            container.reminderScheduler.schedule(settings.reminderEnabled, LocalTime.parse(settings.reminderTime))
            messages.emit(UiMessage.Text("Sigurnosna kopija je uspješno uvezena."))
        }

    fun deleteAll() =
        viewModelScope.launch {
            container.repository.deleteAll()
            messages.emit(UiMessage.Text("Svi podaci su izbrisani."))
        }

    suspend fun snapshot(): AppSnapshot = container.repository.currentSnapshot()

    suspend fun csvExport(): ByteArray = CsvExporter.createZip(container.repository.currentSnapshot(), container.growthCalculator)

    fun highlightMissing(items: Array<String>?) {
        if (items.isNullOrEmpty()) return
        highlight.value = items.toSet()
        viewModelScope.launch {
            delay(4_000)
            highlight.value = emptySet()
        }
    }

    private suspend fun checkCompleteness() {
        if (AppLogic.missing(container.repository.summary(currentLocalDate.value)).isEmpty()) container.notifications.cancelReminder()
    }

    private fun canMutate(date: LocalDate): Boolean =
        date == rolloverPreviousDate.value ||
            (
                date == selectedDate.value &&
                    !date.isAfter(currentLocalDate.value) &&
                    (!date.isBefore(currentLocalDate.value) || pastDateEditMode.value)
            )

    private fun refreshLocalDate() {
        val newDate = localDayClock.today()
        val previousDate = currentLocalDate.value
        val transition = resolveLocalDayTransition(previousDate, selectedDate.value, newDate, pastDateEditMode.value)
        if (!transition.changed) return
        currentLocalDate.value = transition.currentLocalDate
        pastDateEditMode.value = transition.pastDateEditMode
        if (editorOpen.value) rolloverPreviousDate.value = editorDate.value ?: previousDate
        container.timerController.onLocalDateChanged(newDate)
        selectedDate.value = transition.selectedDate
        val settings = state.value.settings
        container.reminderScheduler.schedule(settings.reminderEnabled, LocalTime.parse(settings.reminderTime))
    }

    private fun scheduleMidnightCheck() {
        midnightJob?.cancel()
        midnightJob =
            viewModelScope.launch {
                while (true) {
                    delay(localDayClock.millisUntilNextDay() + 250L)
                    refreshLocalDate()
                }
            }
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
