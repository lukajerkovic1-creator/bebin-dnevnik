package hr.bebindnevnik.app.domain.guidelines

import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import hr.bebindnevnik.app.data.ExpectedMealCountEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.IndividualFeedingTargetEntity
import hr.bebindnevnik.app.data.IndividualTummyTargetEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.MilkCompletenessEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.domain.growth.GrowthAgeBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GuidelineEngineTest {
    private val today = LocalDate.of(2026, 7, 16)

    @Test fun `term age uses chronological age while preterm uses corrected age until two years`() {
        val term = AgeAtDateCalculator.calculate(profile("2026-01-01"), LocalDate.of(2026, 2, 1))!!
        assertEquals(GrowthAgeBasis.CHRONOLOGICAL, term.basis)
        assertEquals(31, term.effectiveDays)
        val preterm = AgeAtDateCalculator.calculate(profile("2026-01-01", weeks = 32), LocalDate.of(2026, 4, 9))!!
        assertEquals(GrowthAgeBasis.CORRECTED, preterm.basis)
        assertEquals(42, preterm.effectiveDays)
        val afterTwo = AgeAtDateCalculator.calculate(profile("2024-01-01", weeks = 32), LocalDate.of(2026, 1, 1))!!
        assertEquals(GrowthAgeBasis.CHRONOLOGICAL, afterTwo.basis)
        assertEquals(731, afterTwo.effectiveDays)
    }

    @Test fun `preterm before forty weeks has no generic targets`() {
        val snapshot = snapshot(profile = profile("2026-07-01", weeks = 32), date = today, weight = 2_500)
        val result = GuidelineEngine.calculate(snapshot, today, today)
        assertNull(result.feeding.lowerMl)
        assertTrue(result.feeding.message!!.contains("neonatologa"))
        assertNull(result.tummy.targetMinutes)
        assertTrue(result.tummy.message!!.contains("nedonošče"))
    }

    @Test fun `weight resolver chooses latest time on or before selected date and never future`() {
        val profile = profile("2026-01-01", birthWeight = 3_000)
        val measurements =
            listOf(
                measurement("2026-02-01", "09:00", 4_000, 1),
                measurement("2026-02-01", "18:00", 4_200, 2),
                measurement("2026-02-02", "08:00", 9_999, 3),
            )
        val resolved = WeightAtDateResolver.resolve(profile, measurements, LocalDate.of(2026, 2, 1))!!
        assertEquals(4_200, resolved.grams)
        assertEquals("18:00", resolved.measurementTime.toString())
    }

    @Test fun `birth weight is fallback and old weight remains available with neutral freshness flag`() {
        val profile = profile("2026-01-01", birthWeight = 3_100)
        val birth = WeightAtDateResolver.resolve(profile, emptyList(), LocalDate.of(2026, 1, 2))!!
        assertTrue(birth.fromBirthProfile)
        val old = WeightAtDateResolver.resolve(profile, listOf(measurement("2026-01-10", "09:00", 3_500)), LocalDate.of(2026, 3, 1))!!
        assertTrue(old.olderThanThirtyDays)
        assertEquals(3_500, old.grams)
    }

    @Test fun `first week and age after twelve months have no generic feeding percentage`() {
        val firstWeek = GuidelineEngine.feeding(snapshot(profile("2026-07-12"), today, 3_500), today, today)
        assertNull(firstWeek.lowerMl)
        assertTrue(firstWeek.message!!.contains("prvom tjednu"))
        val toddlerDate = LocalDate.of(2026, 7, 16)
        val toddler = GuidelineEngine.feeding(snapshot(profile("2025-07-01"), toddlerDate, 10_000), toddlerDate, toddlerDate)
        assertNull(toddler.percentOfLower)
        assertTrue(toddler.message!!.contains("12 mjeseci"))
    }

    @Test fun `weight based feeding target rounds sensibly and complete evidence may exceed one hundred percent`() {
        val date = LocalDate.of(2026, 2, 16)
        val meals = listOf(meal(date, 500), meal(date, 400))
        val snapshot =
            snapshot(profile("2026-01-01"), date, 4_000).copy(
                meals = meals,
                milkCompletenessHistory = listOf(completeness("2026-01-01", true)),
            )
        val result = GuidelineEngine.feeding(snapshot, date, date)
        assertEquals(600, result.lowerMl)
        assertEquals(800, result.upperMl)
        assertEquals(150, result.percentOfLower)
        assertEquals(1f, result.visualProgress)
        assertNull(result.status)
    }

    @Test fun `past complete day is classified while incomplete evidence hides percentage and status`() {
        val date = today.minusDays(1)
        val base = snapshot(profile("2026-05-01"), date, 5_000).copy(meals = listOf(meal(date, 500)))
        val complete = GuidelineEngine.feeding(base.copy(milkCompletenessHistory = listOf(completeness("2026-01-01", true))), date, today)
        assertEquals(RangeStatus.BELOW, complete.status)
        val incomplete = GuidelineEngine.feeding(base.copy(milkCompletenessHistory = listOf(completeness("2026-01-01", false))), date, today)
        assertNull(incomplete.percentOfLower)
        assertNull(incomplete.status)
        assertFalse(incomplete.evidenceComplete!!)
    }

    @Test fun `individual feeding goal overrides generic target for historical date`() {
        val date = LocalDate.of(2026, 3, 1)
        val now = 1L
        val target = IndividualFeedingTargetEntity(lowerMlPerDay = 700, upperMlPerDay = 850, startDate = "2026-02-01", endDate = "2026-03-31", createdAt = now, updatedAt = now)
        val result = GuidelineEngine.feeding(snapshot(profile("2026-01-01"), date, 5_000).copy(individualFeedingTargets = listOf(target)), date, today)
        assertEquals(TargetOrigin.INDIVIDUAL, result.origin)
        assertEquals(700, result.lowerMl)
        assertEquals(850, result.upperMl)
    }

    @Test fun `manual meal count overrides seven day average and produces per meal range`() {
        val date = LocalDate.of(2026, 2, 20)
        val now = 1L
        val manual = ExpectedMealCountEntity(startDate = "2026-02-01", mealCount = 5, createdAt = now, updatedAt = now)
        val result = GuidelineEngine.feeding(snapshot(profile("2026-01-01"), date, 4_000).copy(expectedMealCountHistory = listOf(manual)), date, date)
        assertEquals(5, result.expectedMealCount)
        assertTrue(result.expectedMealCountIsManual)
        assertEquals(120, result.perMealLowerMl)
        assertEquals(160, result.perMealUpperMl)
    }

    @Test fun `automatic meal count needs three complete recorded days`() {
        val date = LocalDate.of(2026, 2, 20)
        val meals =
            (0L..2L).flatMap { offset -> List(6) { meal(date.minusDays(offset), 100, it.toLong() + offset * 10) } }
        val complete = (0L..2L).map { completeness(date.minusDays(it).toString(), true, it + 1) }
        val result = GuidelineEngine.feeding(snapshot(profile("2026-01-01"), date, 4_000).copy(meals = meals, milkCompletenessHistory = complete), date, date)
        assertEquals(6, result.expectedMealCount)
        assertFalse(result.expectedMealCountIsManual)
    }

    @Test fun `tummy progress supports zero one hundred and above one hundred`() {
        val date = LocalDate.of(2026, 3, 1)
        val base = snapshot(profile("2026-01-01"), date, 5_000)
        assertEquals(0, GuidelineEngine.tummy(base, date).percent)
        val exact = GuidelineEngine.tummy(base.copy(tummySessions = listOf(tummy(date, 30))), date)
        assertEquals(100, exact.percent)
        val above = GuidelineEngine.tummy(base.copy(tummySessions = listOf(tummy(date, 42))), date)
        assertEquals(140, above.percent)
        assertEquals(1f, above.visualProgress)
    }

    @Test fun `mobility removes generic tummy goal from effective date but individual goal has priority`() {
        val date = LocalDate.of(2026, 5, 1)
        val mobile = profile("2026-01-01").copy(independentMobilityDate = date.toString())
        val generic = GuidelineEngine.tummy(snapshot(mobile, date, 6_000), date)
        assertTrue(generic.independentlyMobile)
        assertNull(generic.targetMinutes)
        val now = 1L
        val individual = IndividualTummyTargetEntity(minutesPerDay = 20, startDate = date.toString(), createdAt = now, updatedAt = now)
        val overridden = GuidelineEngine.tummy(snapshot(mobile, date, 6_000).copy(individualTummyTargets = listOf(individual)), date)
        assertEquals(20, overridden.targetMinutes)
        assertEquals(TargetOrigin.INDIVIDUAL, overridden.origin)
    }

    @Test fun `disabled master switch hides both targets without deleting source data`() {
        val date = LocalDate.of(2026, 3, 1)
        val snapshot = snapshot(profile("2026-01-01"), date, 5_000).copy(settings = SettingsEntity(guidelineTargetsEnabled = false))
        val result = GuidelineEngine.calculate(snapshot, date, date)
        assertNull(result.feeding.lowerMl)
        assertNull(result.tummy.targetMinutes)
    }

    @Test fun `interval overlap includes touching endpoints`() {
        assertTrue(GuidelineEngine.intervalsOverlap(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), null))
        assertFalse(GuidelineEngine.intervalsOverlap(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1), null))
    }

    @Test fun `period statistics recalculate historical target for seven thirty and ninety days`() {
        val statisticsToday = LocalDate.of(2026, 4, 1)
        listOf(7, 30, 90).forEach { dayCount ->
            val start = statisticsToday.minusDays(dayCount.toLong())
            val end = statisticsToday.minusDays(1)
            val dates = generateSequence(start) { if (it < end) it.plusDays(1) else null }.toList()
            val source =
                snapshot(profile("2025-10-01"), start, 5_000).copy(
                    meals = dates.mapIndexed { index, date -> meal(date, 750, index.toLong() + 1) },
                    tummySessions = dates.map { tummy(it, 30) },
                    milkCompletenessHistory = listOf(completeness(start.toString(), true)),
                )
            val result = GuidelineEngine.statistics(source, start, end, statisticsToday)
            assertEquals(dayCount, result.feeding.withinDays)
            assertEquals(dayCount, result.tummy.achievedDays)
            assertEquals(100, result.feeding.averagePercentOfLower)
            assertEquals(100, result.tummy.averagePercent)
        }
    }

    private fun profile(
        birthDate: String,
        weeks: Int = 40,
        birthWeight: Int? = null,
    ) = ChildProfileEntity(
        name = "Ana",
        sex = ChildSex.DJEVOJCICA,
        birthDate = birthDate,
        gestationalWeeks = weeks,
        gestationalDays = 0,
        birthWeightG = birthWeight,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun snapshot(
        profile: ChildProfileEntity,
        date: LocalDate,
        weight: Int,
    ) = AppSnapshot(
        emptyList(),
        emptyList(),
        emptyList(),
        SettingsEntity(),
        profile,
        listOf(measurement(date.toString(), "08:00", weight)),
    )

    private fun measurement(
        date: String,
        time: String,
        weight: Int,
        id: Long = 1,
    ) = GrowthMeasurementEntity(id = id, date = date, time = time, weightG = weight, createdAt = 1, updatedAt = 1)

    private fun meal(
        date: LocalDate,
        amount: Int,
        id: Long = 1,
    ) = MealEntity(id, date.toString(), "08:00", amount, 1, 1)

    private fun completeness(
        date: String,
        complete: Boolean,
        id: Long = 1,
    ) = MilkCompletenessEntity(id, date, complete = complete, createdAt = 1, updatedAt = 1)

    private fun tummy(
        date: LocalDate,
        minutes: Int,
    ) = hr.bebindnevnik.app.data.TummySessionEntity(
        date = date.toString(),
        time = "09:00",
        durationSeconds = minutes * 60L,
        inputMethod = hr.bebindnevnik.app.data.TummyInputMethod.RUCNO,
        createdAt = 1,
        updatedAt = 1,
    )
}
