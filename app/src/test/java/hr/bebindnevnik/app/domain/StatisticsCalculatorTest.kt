package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsCalculatorTest {
    private val today = LocalDate.of(2026, 7, 12)
    private val timestamp = 1_700_000_000_000

    @Test fun `preset and custom periods return exact inclusive windows`() {
        assertEquals(7, report(StatisticsPeriod.SEVEN_DAYS).dayCount)
        assertEquals(30, report(StatisticsPeriod.THIRTY_DAYS).dayCount)
        assertEquals(90, report(StatisticsPeriod.NINETY_DAYS).dayCount)
        val custom = calculate(StatisticsSelection(StatisticsPeriod.CUSTOM, today.minusDays(3), today.minusDays(1)))
        assertEquals(3, custom.dayCount)
        assertEquals(today.minusDays(3), custom.start)
        assertEquals(today.minusDays(1), custom.end)
    }

    @Test fun `all period starts on first data day and empty database remains one valid day`() {
        val first = today.minusDays(120)
        val all = calculate(StatisticsSelection(StatisticsPeriod.ALL), meals = listOf(meal(first, "08:00", 80)))
        assertEquals(121, all.dayCount)
        assertEquals(first, all.start)
        val empty = calculate(StatisticsSelection(StatisticsPeriod.ALL))
        assertEquals(1, empty.dayCount)
        assertEquals(
            DayStatus.BEZ_PODATAKA,
            empty.days
                .single()
                .summary.status,
        )
    }

    @Test fun `feeding totals averages maximum and time buckets are exact`() {
        val meals =
            listOf(
                meal(today, "01:00", 40),
                meal(today, "07:00", 80),
                meal(today.minusDays(1), "13:00", 120),
                meal(today.minusDays(1), "19:00", 160),
            )
        val result = calculate(StatisticsSelection(StatisticsPeriod.SEVEN_DAYS), meals = meals)
        assertEquals(400, result.feeding.totalMl)
        assertEquals(4, result.feeding.mealCount)
        assertEquals(100.0, result.feeding.averagePerMeal, 0.0)
        assertEquals(200.0, result.feeding.averagePerRecordedDay, 0.0)
        assertEquals(160, result.feeding.largestMealMl)
        assertEquals(today.minusDays(1), result.feeding.highestDay)
        assertEquals(listOf(1, 1, 1, 1), result.feeding.timeBuckets)
    }

    @Test fun `previous equal period comparison is calculated only with comparable data`() {
        val currentDate = today.minusDays(1)
        val previousDate = today.minusDays(8)
        val meals = listOf(meal(currentDate, "08:00", 200), meal(previousDate, "08:00", 100))
        val result = calculate(StatisticsSelection(StatisticsPeriod.SEVEN_DAYS), meals = meals)
        assertEquals(100, result.comparison.totalMlPercent)
        assertEquals(0, result.comparison.mealCountPercent)
        val noPrevious = calculate(StatisticsSelection(StatisticsPeriod.SEVEN_DAYS), meals = listOf(meal(today, "08:00", 100)))
        assertNull(noPrevious.comparison.totalMlPercent)
    }

    @Test fun `tummy explicit zero and sessions are distinct from missing`() {
        val zeroDay = today.minusDays(1)
        val activeDay = today
        val entries = listOf(daily(zeroDay, noTummy = true))
        val sessions = listOf(tummy(activeDay, "09:00", 60), tummy(activeDay, "10:00", 180))
        val result = calculate(StatisticsSelection(StatisticsPeriod.SEVEN_DAYS), entries = entries, sessions = sessions)
        assertEquals(2, result.tummy.recordedDays)
        assertEquals(240, result.tummy.totalSeconds)
        assertEquals(2, result.tummy.sessionCount)
        assertEquals(120.0, result.tummy.averagePerRecordedDaySeconds, 0.0)
        assertEquals(60L, result.tummy.shortestSessionSeconds)
        assertEquals(180L, result.tummy.longestSessionSeconds)
        assertTrue(result.days.first { it.date == zeroDay }.tummyRecorded)
        assertFalse(result.days.first().tummyRecorded)
    }

    @Test fun `stool recorded zero participates in average while missing does not`() {
        val entries = listOf(daily(today, stool = 0), daily(today.minusDays(1), stool = 4))
        val result = calculate(StatisticsSelection(StatisticsPeriod.SEVEN_DAYS), entries = entries)
        assertEquals(2, result.stool.recordedDays)
        assertEquals(4, result.stool.total)
        assertEquals(2.0, result.stool.averagePerRecordedDay, 0.0)
        assertEquals(1, result.stool.zeroDays)
        assertEquals(1, result.stool.positiveDays)
        assertEquals(5, result.stool.missingDays)
    }

    @Test fun `habit and completeness states preserve missing values`() {
        val completeDate = today
        val partialDate = today.minusDays(1)
        val meals = listOf(meal(completeDate, "08:00", 80), meal(partialDate, "08:00", 40))
        val entries =
            listOf(
                daily(completeDate, TernaryStatus.DA, TernaryStatus.NE, noTummy = true, stool = 0),
                daily(partialDate, TernaryStatus.NE, TernaryStatus.NIJE_EVIDENTIRANO),
            )
        val result = calculate(StatisticsSelection(StatisticsPeriod.SEVEN_DAYS), meals, entries)
        assertEquals(1, result.waya.yes)
        assertEquals(1, result.waya.no)
        assertEquals(5, result.waya.missing)
        assertEquals(1, result.exercise.no)
        assertEquals(6, result.exercise.missing)
        assertEquals(1, result.completeness.completeDays)
        assertEquals(1, result.completeness.partialDays)
        assertEquals(5, result.completeness.emptyDays)
        assertEquals("vježbanje", result.completeness.mostOftenMissing?.first)
    }

    @Test fun `large values and many records never create NaN infinity or negative results`() {
        val meals = (0 until 2_000).map { meal(today.minusDays((it % 90).toLong()), "08:00", Int.MAX_VALUE / 4_000) }
        val result = calculate(StatisticsSelection(StatisticsPeriod.NINETY_DAYS), meals = meals)
        assertTrue(result.feeding.totalMl > 0)
        assertTrue(result.feeding.averagePerMeal.isFinite())
        assertTrue(result.feeding.averagePerAllDays.isFinite())
        assertTrue(result.feeding.averagePerRecordedDay.isFinite())
        assertTrue(result.feeding.averagePerMeal >= 0)
    }

    private fun report(period: StatisticsPeriod) = calculate(StatisticsSelection(period))

    private fun calculate(
        selection: StatisticsSelection,
        meals: List<MealEntity> = emptyList(),
        entries: List<DailyEntryEntity> = emptyList(),
        sessions: List<TummySessionEntity> = emptyList(),
    ) = StatisticsCalculator.calculate(selection, today, meals, entries, sessions)

    private fun meal(
        date: LocalDate,
        time: String,
        amount: Int,
    ) = MealEntity(0, date.toString(), time, amount, timestamp, timestamp)

    private fun daily(
        date: LocalDate,
        waya: TernaryStatus = TernaryStatus.NIJE_EVIDENTIRANO,
        exercise: TernaryStatus = TernaryStatus.NIJE_EVIDENTIRANO,
        noTummy: Boolean = false,
        stool: Int? = null,
    ) = DailyEntryEntity(date.toString(), waya, exercise, noTummy, timestamp, timestamp, stool)

    private fun tummy(
        date: LocalDate,
        time: String,
        seconds: Long,
    ) = TummySessionEntity(0, date.toString(), time, seconds, TummyInputMethod.RUCNO, timestamp, timestamp)
}
