package hr.bebindnevnik.app.domain.growth

import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.LengthMeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

class WhoGrowthReferenceTest {
    private val reference =
        WhoGrowthReference.fromOpener { assetName ->
            File("src/main/assets/$assetName").inputStream()
        }
    private val calculator = GrowthCalculator(reference)

    @Test fun `official WHO medians at birth match embedded daily LMS tables for both sexes`() {
        assertEquals(3.3464, reference.ageParameters(GrowthIndicator.WEIGHT_FOR_AGE, ChildSex.DJECAK, 0)!!.m, 0.00001)
        assertEquals(3.2322, reference.ageParameters(GrowthIndicator.WEIGHT_FOR_AGE, ChildSex.DJEVOJCICA, 0)!!.m, 0.00001)
        assertEquals(49.8842, reference.ageParameters(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, ChildSex.DJECAK, 0)!!.m, 0.00001)
        assertEquals(49.1477, reference.ageParameters(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, ChildSex.DJEVOJCICA, 0)!!.m, 0.00001)
        assertEquals(34.4618, reference.ageParameters(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, ChildSex.DJECAK, 0)!!.m, 0.00001)
        assertEquals(33.8787, reference.ageParameters(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, ChildSex.DJEVOJCICA, 0)!!.m, 0.00001)
    }

    @Test fun `LMS round trips exact 3 15 50 85 and 97 percentiles and z plus minus two`() {
        val zValues = listOf(-2.0, -1.880793608151251, -1.0364333894937898, 0.0, 1.0364333894937898, 1.8807936081512509, 2.0)
        ChildSex.entries.forEach { sex ->
            GrowthIndicator.entries.filter { it != GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT }.forEach { indicator ->
                val parameters = requireNotNull(reference.ageParameters(indicator, sex, 365))
                zValues.forEach { expected ->
                    val value = lmsValueAtZ(expected, parameters)
                    val actual = lmsZScore(value, parameters, indicator == GrowthIndicator.WEIGHT_FOR_AGE)
                    assertEquals(expected, actual, 1e-8)
                    assertTrue(actual.isFinite())
                }
            }
        }
        assertEquals(50.0, normalCdf(0.0) * 100.0, 0.00002)
        assertEquals(2.275, normalCdf(-2.0) * 100.0, 0.002)
        assertEquals(97.725, normalCdf(2.0) * 100.0, 0.002)
    }

    @Test fun `weight for length and height cover official boundaries without extrapolation`() {
        ChildSex.entries.forEach { sex ->
            assertNotNull(reference.weightForMeasureParameters(sex, 45.0, true))
            assertNotNull(reference.weightForMeasureParameters(sex, 110.0, true))
            assertNull(reference.weightForMeasureParameters(sex, 44.9, true))
            assertNull(reference.weightForMeasureParameters(sex, 110.1, true))
            assertNotNull(reference.weightForMeasureParameters(sex, 65.0, false))
            assertNotNull(reference.weightForMeasureParameters(sex, 120.0, false))
            assertNull(reference.weightForMeasureParameters(sex, 64.9, false))
            assertNull(reference.weightForMeasureParameters(sex, 120.1, false))
        }
    }

    @Test fun `length height correction preserves source and changes calculation only`() {
        val profile = profile(ChildSex.DJECAK, LocalDate.of(2025, 1, 1), 40, 0)
        val standingInfant = measurement(LocalDate.of(2025, 7, 1), length = 65.0, type = LengthMeasurementType.STOJECA_VISINA)
        val infantResult = calculator.assess(profile, standingInfant).metric(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE)!!
        assertEquals(65.0, infantResult.rawValue, 0.0)
        assertEquals(65.7, infantResult.calculationValue, 0.0001)
        assertEquals(0.7, infantResult.lengthCorrectionCm, 0.0001)

        val recumbentChild = measurement(LocalDate.of(2027, 2, 1), length = 90.0, type = LengthMeasurementType.LEZECA_DULJINA)
        val childResult = calculator.assess(profile, recumbentChild).metric(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE)!!
        assertEquals(90.0, childResult.rawValue, 0.0)
        assertEquals(89.3, childResult.calculationValue, 0.0001)
        assertEquals(-0.7, childResult.lengthCorrectionCm, 0.0001)
    }

    @Test fun `weight for length is calculated only from same complete record`() {
        val profile = profile(ChildSex.DJEVOJCICA, LocalDate.of(2025, 1, 1), 40, 0)
        val weightOnly = measurement(LocalDate.of(2025, 6, 1), weight = 6_500)
        val lengthOnly = measurement(LocalDate.of(2025, 6, 2), length = 64.0)
        val complete = measurement(LocalDate.of(2025, 6, 3), weight = 6_600, length = 64.2)
        assertNull(calculator.assess(profile, weightOnly).metric(GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT))
        assertNull(calculator.assess(profile, lengthOnly).metric(GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT))
        assertNotNull(calculator.assess(profile, complete).metric(GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT))
    }

    @Test fun `embedded WHO file hashes are pinned`() {
        WhoReferenceFiles.sha256.forEach { (name, expected) ->
            val bytes = File("src/main/assets/growth/who/$name").readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expected, actual)
        }
    }

    @Suppress("NestedBlockDepth")
    @Test
    fun `no NaN infinity or age extrapolation at boundaries`() {
        ChildSex.entries.forEach { sex ->
            GrowthIndicator.entries.filter { it != GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT }.forEach { indicator ->
                listOf(0, 1826).forEach { day ->
                    val parameters = reference.ageParameters(indicator, sex, day)
                    assertNotNull(parameters)
                    listOf(-3.0, 0.0, 3.0).forEach { z ->
                        val value = lmsValueAtZ(z, parameters!!)
                        assertTrue(value.isFinite())
                        assertFalse(lmsZScore(value, parameters, indicator == GrowthIndicator.WEIGHT_FOR_AGE).isNaN())
                    }
                }
                assertNull(reference.ageParameters(indicator, sex, -1))
                assertNull(reference.ageParameters(indicator, sex, 1827))
            }
        }
    }

    private fun profile(
        sex: ChildSex,
        birth: LocalDate,
        weeks: Int,
        days: Int,
    ) = ChildProfileEntity(name = "Test", sex = sex, birthDate = birth.toString(), gestationalWeeks = weeks, gestationalDays = days, createdAt = 1, updatedAt = 1)

    private fun measurement(
        date: LocalDate,
        weight: Int? = null,
        length: Double? = null,
        type: LengthMeasurementType = LengthMeasurementType.LEZECA_DULJINA,
    ) = GrowthMeasurementEntity(date = date.toString(), time = "12:00", weightG = weight, lengthHeightCm = length, lengthMeasurementType = type, createdAt = 1, updatedAt = 1)
}
