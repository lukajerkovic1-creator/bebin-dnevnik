package hr.bebindnevnik.app.ui

import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UiStateDateSelectionTest {
    @Test fun `selected date filters every day-specific value without mixing days`() {
        val today = LocalDate.of(2026, 7, 11)
        val past = today.minusDays(1)
        val meals =
            listOf(
                MealEntity(1, today.toString(), "08:00", 40, 1, 1),
                MealEntity(2, past.toString(), "09:00", 120, 1, 1),
                MealEntity(3, past.toString(), "12:00", 80, 1, 1),
            )
        val entries =
            listOf(
                DailyEntryEntity(today.toString(), TernaryStatus.NE, TernaryStatus.NE, false, 1, 1, 0),
                DailyEntryEntity(past.toString(), TernaryStatus.DA, TernaryStatus.DA, false, 1, 1, 3),
            )
        val sessions = listOf(TummySessionEntity(1, past.toString(), "10:00", 90, TummyInputMethod.RUCNO, 1, 1))

        val pastState = UiState(meals = meals, entries = entries, sessions = sessions, selectedDate = past)
        assertEquals(listOf(2L, 3L), pastState.selectedMeals.map { it.id })
        assertEquals(200, pastState.summary.totalMl)
        assertEquals(2, pastState.summary.mealCount)
        assertEquals(TernaryStatus.DA, pastState.summary.waya)
        assertEquals(TernaryStatus.DA, pastState.summary.exercise)
        assertEquals(90, pastState.summary.tummySeconds)
        assertEquals(3, pastState.summary.stoolCount)

        val todayState = pastState.copy(selectedDate = today)
        assertEquals(listOf(1L), todayState.selectedMeals.map { it.id })
        assertEquals(40, todayState.summary.totalMl)
        assertEquals(0, todayState.summary.tummyCount)
        assertEquals(TernaryStatus.NE, todayState.summary.waya)
        assertEquals(0, todayState.summary.stoolCount)
    }

    @Test fun `past date is locked by default and only selected edit mode unlocks it`() {
        val today = LocalDate.of(2026, 7, 13)
        val locked = UiState(selectedDate = today.minusDays(1), currentLocalDate = today)

        assertTrue(locked.isPastDate)
        assertFalse(locked.canEditSelectedDate)
        assertTrue(locked.copy(pastDateEditMode = true).canEditSelectedDate)
        assertFalse(locked.copy(selectedDate = today.plusDays(1), pastDateEditMode = true).canEditSelectedDate)
    }
}
