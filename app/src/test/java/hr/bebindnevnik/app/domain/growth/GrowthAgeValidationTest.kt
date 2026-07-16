package hr.bebindnevnik.app.domain.growth

import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class GrowthAgeValidationTest {
    @Test fun `term child uses chronological WHO age`() {
        val profile = profile(40, 0)
        val ages = calculateGrowthAges(profile, LocalDate.of(2026, 5, 11))!!
        assertEquals(130L, ages.chronologicalDays)
        assertNull(ages.correctedDays)
        assertEquals(GrowthReferenceSystem.WHO_2006 to GrowthAgeBasis.CHRONOLOGICAL, referenceFor(profile, ages))
    }

    @Test fun `preterm corrected and postmenstrual ages use exact gestational days`() {
        val profile = profile(35, 4)
        val date = LocalDate.of(2026, 3, 1)
        val ages = calculateGrowthAges(profile, date)!!
        assertEquals(59L, ages.chronologicalDays)
        assertEquals(28L, ages.correctedDays)
        assertEquals(308L, ages.postmenstrualDays)
        assertEquals(31, ages.correctionDays)
        assertEquals(GrowthReferenceSystem.FENTON_2013_UNAVAILABLE to GrowthAgeBasis.POSTMENSTRUAL, referenceFor(profile, ages))

        val afterTransition = calculateGrowthAges(profile, LocalDate.of(2026, 5, 10))!!
        assertEquals(GrowthReferenceSystem.WHO_2006 to GrowthAgeBasis.CORRECTED, referenceFor(profile, afterTransition))
        assertEquals(GrowthReferenceSystem.WHO_2006 to GrowthAgeBasis.CHRONOLOGICAL, referenceFor(profile, afterTransition, useCorrectedWhoAge = false))
    }

    @Test fun `correction stops at 24 chronological months`() {
        val profile = profile(32, 0)
        val before = calculateGrowthAges(profile, LocalDate.of(2027, 12, 31))!!
        val after = calculateGrowthAges(profile, LocalDate.of(2028, 1, 1))!!
        assertTrue(before.chronologicalDays < 730)
        assertTrue(before.correctedDays != null)
        assertEquals(730L, after.chronologicalDays)
        assertNull(after.correctedDays)
        assertEquals(GrowthAgeBasis.CHRONOLOGICAL, referenceFor(profile, after).second)
    }

    @Test fun `measurement validation permits partial and duplicate dates but blocks impossible dates and empty records`() {
        val profile = profile(40, 0)
        val today = LocalDate.of(2026, 7, 16)
        assertTrue(GrowthValidation.measurement(profile, measurement(today, weight = 6_000), today, LocalTime.of(13, 0)).valid)
        assertTrue(GrowthValidation.measurement(profile, measurement(today, head = 40.2), today, LocalTime.of(13, 0)).valid)
        assertFalse(GrowthValidation.measurement(profile, measurement(today), today, LocalTime.of(13, 0)).valid)
        assertFalse(GrowthValidation.measurement(profile, measurement(LocalDate.of(2025, 12, 31), weight = 3_000), today, LocalTime.NOON).valid)
        assertFalse(GrowthValidation.measurement(profile, measurement(today.plusDays(1), weight = 6_000), today, LocalTime.NOON).valid)
        assertFalse(GrowthValidation.measurement(profile, measurement(LocalDate.of(2031, 1, 1), weight = 20_000), LocalDate.of(2032, 1, 1), LocalTime.NOON).valid)
    }

    @Test fun `channel warning requires comparable measurements seven days apart`() {
        val base = GrowthMetricResult(GrowthIndicator.WEIGHT_FOR_AGE, 1.0, 1.0, 0.0, 50.0, GrowthReferenceSystem.WHO_2006, GrowthAgeBasis.CORRECTED)
        val shifted = base.copy(zScore = -2.1, percentile = 2.0)
        assertTrue(crossedTwoMajorChannels(base, shifted, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)))
        assertFalse(crossedTwoMajorChannels(base, shifted, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)))
        assertFalse(crossedTwoMajorChannels(base, shifted.copy(referenceSystem = GrowthReferenceSystem.FENTON_2013_UNAVAILABLE), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)))
    }

    private fun profile(
        weeks: Int,
        days: Int,
    ) = ChildProfileEntity(name = "Beba", sex = ChildSex.DJEVOJCICA, birthDate = "2026-01-01", gestationalWeeks = weeks, gestationalDays = days, createdAt = 1, updatedAt = 1)

    private fun measurement(
        date: LocalDate,
        weight: Int? = null,
        head: Double? = null,
    ) = GrowthMeasurementEntity(date = date.toString(), time = "12:00", weightG = weight, headCircumferenceCm = head, createdAt = 1, updatedAt = 1)
}
