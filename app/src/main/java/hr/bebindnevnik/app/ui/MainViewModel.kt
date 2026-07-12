package hr.bebindnevnik.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import hr.bebindnevnik.app.AppContainer
import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.DaySummary
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import hr.bebindnevnik.app.domain.AppLogic
import hr.bebindnevnik.app.domain.EntryWarning
import hr.bebindnevnik.app.notifications.TimerCancelReason
import hr.bebindnevnik.app.notifications.TimerEvent
import hr.bebindnevnik.app.notifications.TimerPhase
import hr.bebindnevnik.app.notifications.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val nowMinute: Long = 0,
) {
    val selectedMeals get() = meals.filter { it.date == selectedDate.toString() }
    val selectedSessions get() = sessions.filter { it.date == selectedDate.toString() }
    val summary: DaySummary get() = AppLogic.summary(selectedDate, meals, entries, sessions)
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
}

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val minute = MutableStateFlow(System.currentTimeMillis() / 60_000)
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val highlight = MutableStateFlow<Set<String>>(emptySet())
    val timer: StateFlow<TimerState> = container.timerController.state

    val state: StateFlow<UiState> =
        combine(container.repository.snapshot, selectedDate, minute) { snapshot, date, tick ->
            UiState(snapshot.meals, snapshot.dailyEntries, snapshot.tummySessions, snapshot.settings, date, tick)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            while (true) {
                minute.value = System.currentTimeMillis() / 60_000
                delay(30_000)
            }
        }
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
        if (!date.isAfter(LocalDate.now())) {
            if (date != selectedDate.value && timer.value.phase != TimerPhase.IDLE) {
                container.timerController.cancel(TimerCancelReason.DATE_CHANGED)
            }
            selectedDate.value = date
        }
    }

    fun startTimer() {
        if (selectedDate.value == LocalDate.now()) container.timerController.start()
    }

    fun stopTimer() = container.timerController.stopAndSave()

    fun cancelTimer() = container.timerController.cancel()

    fun confirmTimer() = container.timerController.confirmSave()

    fun onBackgrounded() = container.timerController.onBackgrounded()

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
    ) = viewModelScope.launch {
        if (id == 0L) {
            container.repository.addMeal(date, time, amount)
        } else {
            val old = state.value.meals.first { it.id == id }
            container.repository.updateMeal(old.copy(date = date.toString(), time = time.withNano(0).toString(), amountMl = amount))
        }
        checkCompleteness()
    }

    fun deleteMeal(item: MealEntity) =
        viewModelScope.launch {
            container.repository.deleteMeal(item)
            messages.emit(UiMessage.MealDeleted(item))
        }

    fun deleteTummy(item: TummySessionEntity) =
        viewModelScope.launch {
            container.repository.deleteTummy(item)
            messages.emit(UiMessage.TummyDeleted(item))
        }

    fun undo(message: UiMessage) =
        viewModelScope.launch {
            when (message) {
                is UiMessage.MealDeleted -> container.repository.restoreMeal(message.item)
                is UiMessage.TummyDeleted -> container.repository.restoreTummy(message.item)
                is UiMessage.Text -> Unit
            }
        }

    fun setWaya(status: TernaryStatus) =
        viewModelScope.launch {
            container.repository.setDailyStatus(selectedDate.value, waya = status)
            checkCompleteness()
        }

    fun setExercise(status: TernaryStatus) =
        viewModelScope.launch {
            container.repository.setDailyStatus(selectedDate.value, exercise = status)
            checkCompleteness()
        }

    fun stoolWarnings(
        count: Int,
        date: LocalDate = selectedDate.value,
    ): Set<EntryWarning> = AppLogic.stoolWarnings(count, date)

    fun setStoolCount(count: Int?) =
        viewModelScope.launch {
            container.repository.setStoolCount(selectedDate.value, count)
            checkCompleteness()
        }

    fun resetStatuses() = viewModelScope.launch { container.repository.resetDailyStatuses(selectedDate.value) }

    fun markNoTummy() =
        viewModelScope.launch {
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
    ) = viewModelScope.launch {
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

    fun setTheme(theme: AppTheme) = viewModelScope.launch { container.repository.updateSettings { it.copy(theme = theme) } }

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

    fun highlightMissing(items: Array<String>?) {
        if (items.isNullOrEmpty()) return
        highlight.value = items.toSet()
        viewModelScope.launch {
            delay(4_000)
            highlight.value = emptySet()
        }
    }

    private suspend fun checkCompleteness() {
        if (AppLogic.missing(container.repository.summary(LocalDate.now())).isEmpty()) container.notifications.cancelReminder()
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
