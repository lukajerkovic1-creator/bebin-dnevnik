package hr.bebindnevnik.app.domain.growth

import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.LengthMeasurementType
import java.time.LocalDate

enum class GrowthIndicator {
    WEIGHT_FOR_AGE,
    LENGTH_HEIGHT_FOR_AGE,
    HEAD_CIRCUMFERENCE_FOR_AGE,
    WEIGHT_FOR_LENGTH_HEIGHT,
}

enum class GrowthReferenceSystem {
    WHO_2006,
    FENTON_2013_UNAVAILABLE,
}

enum class GrowthAgeBasis {
    CHRONOLOGICAL,
    CORRECTED,
    POSTMENSTRUAL,
}

data class GrowthAges(
    val chronologicalDays: Long,
    val correctedDays: Long?,
    val postmenstrualDays: Long,
    val correctionDays: Int,
)

data class GrowthMetricResult(
    val indicator: GrowthIndicator,
    val rawValue: Double,
    val calculationValue: Double,
    val zScore: Double?,
    val percentile: Double?,
    val referenceSystem: GrowthReferenceSystem,
    val ageBasis: GrowthAgeBasis,
    val lengthCorrectionCm: Double = 0.0,
    val unavailableReason: String? = null,
) {
    val isLow: Boolean get() = (percentile != null && percentile < 3.0) || (zScore != null && zScore < -2.0)
    val isHigh: Boolean get() = (percentile != null && percentile > 97.0) || (zScore != null && zScore > 2.0)
}

data class GrowthAssessment(
    val measurementId: Long,
    val date: LocalDate,
    val ages: GrowthAges,
    val referenceSystem: GrowthReferenceSystem,
    val ageBasis: GrowthAgeBasis,
    val metrics: List<GrowthMetricResult>,
    val temporallyValid: Boolean,
) {
    fun metric(indicator: GrowthIndicator): GrowthMetricResult? = metrics.firstOrNull { it.indicator == indicator }
}

data class GrowthReferencePoint(
    val x: Double,
    val value: Double,
)

data class GrowthReferenceLine(
    val percentile: Int,
    val points: List<GrowthReferencePoint>,
)

data class GrowthHistoryItem(
    val measurement: GrowthMeasurementEntity,
    val assessment: GrowthAssessment,
    val atBirth: Boolean = false,
)

fun ChildProfileEntity.birthMeasurement(): GrowthMeasurementEntity? {
    if (birthWeightG == null && birthLengthCm == null && birthHeadCircumferenceCm == null) return null
    return GrowthMeasurementEntity(
        id = Long.MIN_VALUE,
        date = birthDate,
        time = "00:00",
        weightG = birthWeightG,
        lengthHeightCm = birthLengthCm,
        lengthMeasurementType = LengthMeasurementType.LEZECA_DULJINA,
        headCircumferenceCm = birthHeadCircumferenceCm,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
