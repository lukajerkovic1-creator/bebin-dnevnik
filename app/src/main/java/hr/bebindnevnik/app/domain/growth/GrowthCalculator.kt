package hr.bebindnevnik.app.domain.growth

import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.LengthMeasurementType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class GrowthCalculator(
    private val who: WhoGrowthReference,
) {
    fun assess(
        profile: ChildProfileEntity,
        measurement: GrowthMeasurementEntity,
        useCorrectedWhoAge: Boolean = true,
    ): GrowthAssessment {
        val date = LocalDate.parse(measurement.date)
        val ages = calculateGrowthAges(profile, date) ?: GrowthAges(-1, null, -1, 0)
        val (reference, ageBasis) = referenceFor(profile, ages, useCorrectedWhoAge)
        val birthDate = LocalDate.parse(profile.birthDate)
        val temporallyValid = !date.isBefore(birthDate) && date.isBefore(birthDate.plusYears(5))
        if (reference == GrowthReferenceSystem.FENTON_2013_UNAVAILABLE || !temporallyValid) {
            val reason =
                if (!temporallyValid) {
                    OUTSIDE_RANGE
                } else {
                    "Fentonovi numerički podaci nisu uključeni bez odgovarajuće licence; mjerenje je sačuvano bez percentila."
                }
            return GrowthAssessment(
                measurement.id,
                date,
                ages,
                reference,
                ageBasis,
                unavailableMetrics(measurement, reference, ageBasis, reason),
                temporallyValid,
            )
        }
        val ageDays = ageDaysFor(ages, ageBasis).toInt()
        val (standardizedLength, correction) = standardizedLength(measurement, ageDays)
        val metrics =
            buildList {
                measurement.weightG?.let { grams ->
                    add(ageMetric(GrowthIndicator.WEIGHT_FOR_AGE, grams.toDouble(), grams / 1000.0, profile, ageDays, ageBasis))
                }
                measurement.lengthHeightCm?.let { raw ->
                    add(
                        ageMetric(
                            GrowthIndicator.LENGTH_HEIGHT_FOR_AGE,
                            raw,
                            standardizedLength ?: raw,
                            profile,
                            ageDays,
                            ageBasis,
                            correction,
                        ),
                    )
                }
                measurement.headCircumferenceCm?.let { head ->
                    add(ageMetric(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, head, head, profile, ageDays, ageBasis))
                }
                if (measurement.weightG != null && standardizedLength != null) {
                    val useLength = ageDays < 731
                    val params = who.weightForMeasureParameters(profile.sex, standardizedLength, useLength)
                    add(
                        metric(
                            GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT,
                            measurement.weightG.toDouble(),
                            measurement.weightG / 1000.0,
                            params,
                            GrowthReferenceSystem.WHO_2006,
                            ageBasis,
                            correction,
                        ),
                    )
                }
            }
        return GrowthAssessment(measurement.id, date, ages, reference, ageBasis, metrics, temporallyValid)
    }

    fun ageReferenceLines(
        profile: ChildProfileEntity,
        indicator: GrowthIndicator,
        maxAgeDays: Int,
        useCorrectedWhoAge: Boolean = true,
    ): List<GrowthReferenceLine> {
        require(indicator != GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT)
        val step =
            when {
                maxAgeDays <= 365 -> 7
                maxAgeDays <= 730 -> 14
                else -> 30
            }
        val start =
            if (profile.gestationalWeeks < 37 && useCorrectedWhoAge) {
                (50 * 7 - 40 * 7).coerceAtLeast(0)
            } else {
                0
            }
        return PERCENTILE_Z.map { (percentile, z) ->
            GrowthReferenceLine(
                percentile,
                (start..maxAgeDays step step).mapNotNull { day ->
                    who.ageParameters(indicator, profile.sex, day)?.let { params ->
                        GrowthReferencePoint(day.toDouble(), lmsValueAtZ(z, params))
                    }
                },
            )
        }
    }

    fun measureReferenceLines(
        profile: ChildProfileEntity,
        useLengthTable: Boolean,
    ): List<GrowthReferenceLine> {
        val start = if (useLengthTable) 450 else 650
        val end = if (useLengthTable) 1100 else 1200
        return PERCENTILE_Z.map { (percentile, z) ->
            GrowthReferenceLine(
                percentile,
                (start..end step 5).mapNotNull { tenth ->
                    val cm = tenth / 10.0
                    who.weightForMeasureParameters(profile.sex, cm, useLengthTable)?.let { params ->
                        GrowthReferencePoint(cm, lmsValueAtZ(z, params))
                    }
                },
            )
        }
    }

    private fun ageMetric(
        indicator: GrowthIndicator,
        rawValue: Double,
        calculationValue: Double,
        profile: ChildProfileEntity,
        ageDays: Int,
        ageBasis: GrowthAgeBasis,
        correction: Double = 0.0,
    ): GrowthMetricResult =
        metric(
            indicator,
            rawValue,
            calculationValue,
            who.ageParameters(indicator, profile.sex, ageDays),
            GrowthReferenceSystem.WHO_2006,
            ageBasis,
            correction,
        )

    private fun metric(
        indicator: GrowthIndicator,
        rawValue: Double,
        calculationValue: Double,
        parameters: LmsParameters?,
        reference: GrowthReferenceSystem,
        ageBasis: GrowthAgeBasis,
        correction: Double,
    ): GrowthMetricResult {
        if (parameters == null) {
            return GrowthMetricResult(
                indicator,
                rawValue,
                calculationValue,
                null,
                null,
                reference,
                ageBasis,
                correction,
                OUTSIDE_RANGE,
            )
        }
        val adjust = indicator == GrowthIndicator.WEIGHT_FOR_AGE || indicator == GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT
        val z = lmsZScore(calculationValue, parameters, adjust)
        val valid =
            when (indicator) {
                GrowthIndicator.WEIGHT_FOR_AGE -> z in -6.0..5.0

                GrowthIndicator.LENGTH_HEIGHT_FOR_AGE -> abs(z) <= 6.0

                GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE,
                GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT,
                -> abs(z) <= 5.0
            }
        return GrowthMetricResult(
            indicator = indicator,
            rawValue = rawValue,
            calculationValue = calculationValue,
            zScore = z.takeIf { valid },
            percentile = normalCdf(z).times(100.0).takeIf { valid },
            referenceSystem = reference,
            ageBasis = ageBasis,
            lengthCorrectionCm = correction,
            unavailableReason = OUTSIDE_RANGE.takeUnless { valid },
        )
    }

    private fun standardizedLength(
        measurement: GrowthMeasurementEntity,
        ageDays: Int,
    ): Pair<Double?, Double> {
        val raw = measurement.lengthHeightCm ?: return null to 0.0
        return when {
            ageDays < 731 && measurement.lengthMeasurementType == LengthMeasurementType.STOJECA_VISINA -> raw + 0.7 to 0.7
            ageDays >= 731 && measurement.lengthMeasurementType == LengthMeasurementType.LEZECA_DULJINA -> raw - 0.7 to -0.7
            else -> raw to 0.0
        }
    }

    private fun unavailableMetrics(
        measurement: GrowthMeasurementEntity,
        reference: GrowthReferenceSystem,
        basis: GrowthAgeBasis,
        reason: String,
    ): List<GrowthMetricResult> =
        buildList {
            measurement.weightG?.let {
                add(GrowthMetricResult(GrowthIndicator.WEIGHT_FOR_AGE, it.toDouble(), it / 1000.0, null, null, reference, basis, unavailableReason = reason))
            }
            measurement.lengthHeightCm?.let {
                add(GrowthMetricResult(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, it, it, null, null, reference, basis, unavailableReason = reason))
            }
            measurement.headCircumferenceCm?.let {
                add(GrowthMetricResult(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, it, it, null, null, reference, basis, unavailableReason = reason))
            }
            if (measurement.weightG != null && measurement.lengthHeightCm != null) {
                add(
                    GrowthMetricResult(
                        GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT,
                        measurement.weightG.toDouble(),
                        measurement.weightG / 1000.0,
                        null,
                        null,
                        reference,
                        basis,
                        unavailableReason = reason,
                    ),
                )
            }
        }

    companion object {
        const val OUTSIDE_RANGE = "Percentil se ne može pouzdano izračunati izvan referentnog raspona."
        private val PERCENTILE_Z =
            listOf(
                3 to -1.880793608151251,
                15 to -1.0364333894937898,
                50 to 0.0,
                85 to 1.0364333894937898,
                97 to 1.8807936081512509,
            )
    }
}

fun normalCdf(z: Double): Double {
    val x = abs(z) / sqrt(2.0)
    val t = 1.0 / (1.0 + 0.3275911 * x)
    val erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
    return if (z >= 0) (1.0 + erf) / 2.0 else (1.0 - erf) / 2.0
}

fun crossedTwoMajorChannels(
    previous: GrowthMetricResult,
    current: GrowthMetricResult,
    previousDate: LocalDate,
    currentDate: LocalDate,
): Boolean {
    if (previous.referenceSystem != current.referenceSystem || previous.ageBasis != current.ageBasis) return false
    if (ChronoUnit.DAYS.between(previousDate, currentDate) < 7) return false
    val previousPercentile = previous.percentile ?: return false
    val currentPercentile = current.percentile ?: return false

    fun channel(value: Double): Int =
        when {
            value < 3 -> 0
            value < 15 -> 1
            value < 50 -> 2
            value < 85 -> 3
            value < 97 -> 4
            else -> 5
        }
    return abs(channel(previousPercentile) - channel(currentPercentile)) >= 2
}
