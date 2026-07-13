package hr.bebindnevnik.app.domain

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Single, testable source for the device's current local calendar day. */
class LocalDayClock(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    fun today(): LocalDate = clock.instant().atZone(zoneProvider()).toLocalDate()

    fun millisUntilNextDay(): Long {
        val now = clock.instant().atZone(zoneProvider())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
    }
}

data class LocalDayTransition(
    val currentLocalDate: LocalDate,
    val selectedDate: LocalDate,
    val pastDateEditMode: Boolean,
    val changed: Boolean,
)

fun resolveLocalDayTransition(
    previousCurrentDate: LocalDate,
    selectedDate: LocalDate,
    newCurrentDate: LocalDate,
    pastDateEditMode: Boolean,
): LocalDayTransition =
    if (previousCurrentDate == newCurrentDate) {
        LocalDayTransition(newCurrentDate, selectedDate, pastDateEditMode, changed = false)
    } else {
        LocalDayTransition(newCurrentDate, newCurrentDate, pastDateEditMode = false, changed = true)
    }
