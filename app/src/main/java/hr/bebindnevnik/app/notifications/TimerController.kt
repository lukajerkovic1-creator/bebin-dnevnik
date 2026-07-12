package hr.bebindnevnik.app.notifications

import android.os.SystemClock
import hr.bebindnevnik.app.data.AppRepository
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class TimerPhase { IDLE, RUNNING, CONFIRMING }

enum class TimerCancelReason { USER, DATE_CHANGED, BACKGROUNDED }

data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val elapsedSeconds: Long = 0,
) {
    val running: Boolean get() = phase == TimerPhase.RUNNING
}

sealed interface TimerEvent {
    data class Saved(
        val session: TummySessionEntity,
    ) : TimerEvent

    data class Cancelled(
        val reason: TimerCancelReason,
    ) : TimerEvent
}

interface TimerNotifications {
    fun showTimer()

    fun cancelTimer()
}

internal data class TimerEnvironment(
    val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    val currentDate: () -> LocalDate = LocalDate::now,
    val currentTime: () -> LocalTime = LocalTime::now,
    val tickDelayMillis: Long = 250,
)

class TimerController internal constructor(
    private val saveSession: suspend (LocalDate, LocalTime, Long, TummyInputMethod) -> TummySessionEntity,
    private val notifications: TimerNotifications,
    private val scope: CoroutineScope,
    private val environment: TimerEnvironment = TimerEnvironment(),
) {
    constructor(
        repository: AppRepository,
        notifications: NotificationHelper,
        scope: CoroutineScope,
    ) : this(repository::addTummy, notifications, scope)

    private val lock = Any()
    private val mutableState = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<TimerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TimerEvent> = mutableEvents.asSharedFlow()
    private var startedAtElapsed = 0L
    private var startedDate: LocalDate? = null
    private var startedTime: LocalTime? = null
    private var pendingSession: PendingTimerSession? = null
    private var ticker: Job? = null

    fun start() {
        synchronized(lock) {
            if (mutableState.value.phase != TimerPhase.IDLE) return
            startedAtElapsed = environment.elapsedRealtime()
            startedDate = environment.currentDate()
            startedTime = environment.currentTime().withNano(0)
            mutableState.value = TimerState(TimerPhase.RUNNING)
            notifications.showTimer()
            ticker = scope.launch { runTicker() }
        }
    }

    fun stopAndSave() {
        val session =
            synchronized(lock) {
                if (mutableState.value.phase != TimerPhase.RUNNING) return
                val pending = pendingFromRunning()
                stopTickerAndNotification()
                if (pending.needsConfirmation) {
                    pendingSession = pending
                    mutableState.value = TimerState(TimerPhase.CONFIRMING, pending.durationSeconds)
                    null
                } else {
                    resetToIdle()
                    pending
                }
            }
        session?.let(::save)
    }

    fun confirmSave() {
        val session =
            synchronized(lock) {
                if (mutableState.value.phase != TimerPhase.CONFIRMING) return
                pendingSession.also { resetToIdle() }
            }
        session?.let(::save)
    }

    fun cancel(reason: TimerCancelReason = TimerCancelReason.USER) {
        synchronized(lock) {
            if (mutableState.value.phase == TimerPhase.IDLE) return
            stopTickerAndNotification()
            resetToIdle()
        }
        mutableEvents.tryEmit(TimerEvent.Cancelled(reason))
    }

    fun onBackgrounded() = cancel(TimerCancelReason.BACKGROUNDED)

    private suspend fun runTicker() {
        while (true) {
            synchronized(lock) {
                if (mutableState.value.phase != TimerPhase.RUNNING) return
                mutableState.value = TimerState(TimerPhase.RUNNING, elapsedNow())
            }
            delay(environment.tickDelayMillis)
        }
    }

    private fun pendingFromRunning() =
        PendingTimerSession(
            date = requireNotNull(startedDate),
            time = requireNotNull(startedTime),
            durationSeconds = elapsedNow(),
        )

    private fun elapsedNow(): Long = ((environment.elapsedRealtime() - startedAtElapsed) / 1_000).coerceAtLeast(0)

    private fun stopTickerAndNotification() {
        ticker?.cancel()
        ticker = null
        notifications.cancelTimer()
    }

    private fun resetToIdle() {
        startedAtElapsed = 0
        startedDate = null
        startedTime = null
        pendingSession = null
        mutableState.value = TimerState()
    }

    private fun save(session: PendingTimerSession) {
        scope.launch {
            val saved = saveSession(session.date, session.time, session.durationSeconds, TummyInputMethod.STOPERICA)
            mutableEvents.emit(TimerEvent.Saved(saved))
        }
    }

    private data class PendingTimerSession(
        val date: LocalDate,
        val time: LocalTime,
        val durationSeconds: Long,
    ) {
        val needsConfirmation: Boolean get() = durationSeconds < 5 || durationSeconds > 60 * 60
    }
}
