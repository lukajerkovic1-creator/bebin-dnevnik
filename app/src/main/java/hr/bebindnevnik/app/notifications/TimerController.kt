package hr.bebindnevnik.app.notifications

import android.os.SystemClock
import hr.bebindnevnik.app.data.AppRepository
import hr.bebindnevnik.app.data.TummyInputMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TimerState(
    val running: Boolean = false,
    val elapsedSeconds: Long = 0,
)

class TimerController(
    private val repository: AppRepository,
    private val notifications: NotificationHelper,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = mutableState.asStateFlow()
    private var startedAt = 0L
    private var ticker: Job? = null

    fun start() {
        if (mutableState.value.running) return
        startedAt = SystemClock.elapsedRealtime()
        mutableState.value = TimerState(running = true)
        notifications.showTimer()
        ticker =
            scope.launch {
                while (true) {
                    mutableState.value = TimerState(true, ((SystemClock.elapsedRealtime() - startedAt) / 1_000).coerceAtLeast(0))
                    delay(250)
                }
            }
    }

    fun stopAndSave() {
        val elapsed = elapsedNow()
        clear()
        if (elapsed >= 0) scope.launch { repository.addTummy(LocalDate.now(), LocalTime.now(), elapsed, TummyInputMethod.STOPERICA) }
    }

    fun cancel() = clear()

    fun onBackgrounded() = clear()

    private fun elapsedNow(): Long = if (mutableState.value.running) ((SystemClock.elapsedRealtime() - startedAt) / 1_000).coerceAtLeast(0) else -1

    private fun clear() {
        ticker?.cancel()
        ticker = null
        startedAt = 0
        mutableState.value = TimerState()
        notifications.cancelTimer()
    }
}
