package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AppLogicTest {
    private val date = LocalDate.of(2026, 1, 2)
    private val time = LocalTime.of(8, 30)
    private val now = 1_700_000_000_000

    @Test fun `meal warnings cover zero large and duplicate values`() {
        val existing = meal(7, 80)
        assertEquals(setOf(EntryWarning.ZERO_ML, EntryWarning.DUPLICATE_TIME), AppLogic.mealWarnings(0, date, time, listOf(existing)))
        assertEquals(setOf(EntryWarning.OVER_500_ML), AppLogic.mealWarnings(501, date, time.plusMinutes(1), listOf(existing)))
        assertTrue(AppLogic.mealWarnings(120, date, time, listOf(existing), editedId = 7).isEmpty())
    }

    @Test fun `negative amount and future values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AppLogic.mealWarnings(-1, date, time, emptyList()) }
        val tomorrow = LocalDate.now().plusDays(1)
        assertThrows(IllegalArgumentException::class.java) { AppLogic.mealWarnings(80, tomorrow, time, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { AppLogic.tummyWarnings(10, tomorrow, time) }
    }

    @Test fun `summary calculates totals average last meal and tummy time`() {
        val meals = listOf(meal(1, 40), meal(2, 80, LocalTime.of(12, 0)))
        val sessions = listOf(tummy(1, 61), tummy(2, 119))
        val entry = daily(TernaryStatus.DA, TernaryStatus.NE)
        val summary = AppLogic.summary(date, meals, listOf(entry), sessions)
        assertEquals(120, summary.totalMl)
        assertEquals(2, summary.mealCount)
        assertEquals(60.0, summary.averageMl, 0.0)
        assertEquals("12:00", summary.lastMealTime)
        assertEquals(180, summary.tummySeconds)
        assertEquals(2, summary.tummyCount)
        assertEquals(DayStatus.POTPUNO, summary.status)
    }

    @Test fun `day completeness distinguishes partial empty and explicit no tummy`() {
        assertEquals(DayStatus.BEZ_PODATAKA, AppLogic.summary(date, emptyList(), emptyList(), emptyList()).status)
        assertEquals(DayStatus.DJELOMICNO, AppLogic.summary(date, listOf(meal(1, 80)), emptyList(), emptyList()).status)
        val completeWithoutSession =
            AppLogic.summary(
                date,
                listOf(meal(1, 80)),
                listOf(daily(TernaryStatus.DA, TernaryStatus.NE, true)),
                emptyList(),
            )
        assertEquals(DayStatus.POTPUNO, completeWithoutSession.status)
        assertTrue(completeWithoutSession.noTummyTime)
    }

    @Test fun `missing list contains exactly incomplete categories`() {
        val summary =
            AppLogic.summary(
                date,
                listOf(meal(1, 80)),
                listOf(daily(TernaryStatus.DA, TernaryStatus.NIJE_EVIDENTIRANO)),
                emptyList(),
            )
        assertEquals(listOf("vježbanje", "tummy time"), AppLogic.missing(summary))
        assertFalse(
            AppLogic
                .missing(
                    AppLogic.summary(date, listOf(meal(1, 80)), listOf(daily(TernaryStatus.DA, TernaryStatus.NE, true)), emptyList()),
                ).isNotEmpty(),
        )
    }

    @Test fun `elapsed time uses minutes hours and days`() {
        val event = LocalDateTime.of(date, time)
        assertEquals("prije 42 min", AppLogic.elapsedText(date, time, event.plusMinutes(42)))
        assertEquals("prije 2 h 5 min", AppLogic.elapsedText(date, time, event.plusHours(2).plusMinutes(5)))
        assertEquals("prije 1 d 3 h", AppLogic.elapsedText(date, time, event.plusDays(1).plusHours(3)))
    }

    @Test fun `tummy warnings cover short and long sessions`() {
        assertEquals(setOf(EntryWarning.UNDER_5_SECONDS), AppLogic.tummyWarnings(4, date, time))
        assertEquals(setOf(EntryWarning.OVER_60_MINUTES), AppLogic.tummyWarnings(3_601, date, time))
        assertTrue(AppLogic.tummyWarnings(300, date, time).isEmpty())
    }

    @Test fun `statistics returns exact 7 30 and all-period windows`() {
        val today = LocalDate.of(2026, 2, 15)
        val first = today.minusDays(44)
        val meals = listOf(MealEntity(1, first.toString(), "08:00", 80, now, now), MealEntity(2, today.toString(), "09:00", 120, now, now))
        val seven = AppLogic.statistics(StatisticsRange.SEVEN_DAYS, today, first, meals, emptyList(), emptyList())
        val thirty = AppLogic.statistics(StatisticsRange.THIRTY_DAYS, today, first, meals, emptyList(), emptyList())
        val all = AppLogic.statistics(StatisticsRange.ALL, today, first, meals, emptyList(), emptyList())
        assertEquals(7, seven.size)
        assertEquals(30, thirty.size)
        assertEquals(45, all.size)
        assertEquals(120, seven.sumOf { it.totalMl })
        assertEquals(200, all.sumOf { it.totalMl })
    }

    @Test fun `reminder is sent only with missing data and at most once a day`() {
        val today = LocalDate.of(2026, 2, 15)
        assertTrue(AppLogic.shouldSendReminder(listOf("obrok"), null, today))
        assertFalse(AppLogic.shouldSendReminder(emptyList(), null, today))
        assertFalse(AppLogic.shouldSendReminder(listOf("obrok"), today.toString(), today))
        assertTrue(AppLogic.shouldSendReminder(listOf("obrok"), today.minusDays(1).toString(), today))
    }

    private fun meal(
        id: Long,
        amount: Int,
        at: LocalTime = time,
    ) = MealEntity(id, date.toString(), at.toString(), amount, now, now)

    private fun tummy(
        id: Long,
        seconds: Long,
    ) = TummySessionEntity(id, date.toString(), time.toString(), seconds, TummyInputMethod.RUCNO, now, now)

    private fun daily(
        waya: TernaryStatus,
        exercise: TernaryStatus,
        noTummy: Boolean = false,
    ) = DailyEntryEntity(date.toString(), waya, exercise, noTummy, now, now)
}
