package hr.bebindnevnik.app.domain.growth

import android.content.res.AssetManager
import hr.bebindnevnik.app.data.ChildSex
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

data class LmsParameters(
    val l: Double,
    val m: Double,
    val s: Double,
)

class WhoGrowthReference private constructor(
    private val opener: (String) -> InputStream,
) {
    private val weightAge by lazy { loadAgeTable(WEIGHT_AGE) }
    private val lengthAge by lazy { loadAgeTable(LENGTH_AGE) }
    private val headAge by lazy { loadAgeTable(HEAD_AGE) }
    private val weightLength by lazy { loadMeasureTable(WEIGHT_LENGTH) }
    private val weightHeight by lazy { loadMeasureTable(WEIGHT_HEIGHT) }

    fun ageParameters(
        indicator: GrowthIndicator,
        sex: ChildSex,
        ageDays: Int,
    ): LmsParameters? {
        val table =
            when (indicator) {
                GrowthIndicator.WEIGHT_FOR_AGE -> weightAge
                GrowthIndicator.LENGTH_HEIGHT_FOR_AGE -> lengthAge
                GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE -> headAge
                GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT -> return null
            }
        return table[ageKey(sex, ageDays)]
    }

    fun weightForMeasureParameters(
        sex: ChildSex,
        standardizedLengthHeightCm: Double,
        useLengthTable: Boolean,
    ): LmsParameters? {
        val table = if (useLengthTable) weightLength else weightHeight
        val scaled = standardizedLengthHeightCm * 10.0
        val lower = floor(scaled + 1e-8).toInt()
        val fraction = scaled - lower
        val low = table[measureKey(sex, lower)] ?: return null
        if (fraction < 1e-8) return low
        val high = table[measureKey(sex, lower + 1)] ?: return null
        return LmsParameters(
            l = low.l + fraction * (high.l - low.l),
            m = low.m + fraction * (high.m - low.m),
            s = low.s + fraction * (high.s - low.s),
        )
    }

    private fun loadAgeTable(name: String): Map<Long, LmsParameters> =
        opener(name).bufferedReader().useLines { lines ->
            lines
                .drop(1)
                .mapNotNull { line ->
                    val columns = line.split('\t')
                    if (columns.size < 5) return@mapNotNull null
                    val sex = columns[0].toInt()
                    val age = columns[1].toInt()
                    ageKey(sex, age) to LmsParameters(columns[2].toDouble(), columns[3].toDouble(), columns[4].toDouble())
                }.toMap()
        }

    private fun loadMeasureTable(name: String): Map<Long, LmsParameters> =
        opener(name).bufferedReader().useLines { lines ->
            lines
                .drop(1)
                .mapNotNull { line ->
                    val columns = line.split('\t')
                    if (columns.size < 5) return@mapNotNull null
                    val sex = columns[0].toInt()
                    val measure = (columns[1].toDouble() * 10.0 + 0.001).toInt()
                    measureKey(sex, measure) to LmsParameters(columns[2].toDouble(), columns[3].toDouble(), columns[4].toDouble())
                }.toMap()
        }

    companion object {
        private const val ROOT = "growth/who/"
        private const val WEIGHT_AGE = ROOT + "weight_for_age.tsv"
        private const val LENGTH_AGE = ROOT + "length_height_for_age.tsv"
        private const val HEAD_AGE = ROOT + "head_circumference_for_age.tsv"
        private const val WEIGHT_LENGTH = ROOT + "weight_for_length.tsv"
        private const val WEIGHT_HEIGHT = ROOT + "weight_for_height.tsv"

        fun fromAssets(assets: AssetManager): WhoGrowthReference = WhoGrowthReference(assets::open)

        internal fun fromOpener(opener: (String) -> InputStream): WhoGrowthReference = WhoGrowthReference(opener)

        private fun sexCode(sex: ChildSex): Int = if (sex == ChildSex.DJECAK) 1 else 2

        private fun ageKey(
            sex: ChildSex,
            age: Int,
        ): Long = ageKey(sexCode(sex), age)

        private fun ageKey(
            sex: Int,
            age: Int,
        ): Long = sex.toLong() * 10_000L + age

        private fun measureKey(
            sex: ChildSex,
            measureTenths: Int,
        ): Long = measureKey(sexCode(sex), measureTenths)

        private fun measureKey(
            sex: Int,
            measureTenths: Int,
        ): Long = sex.toLong() * 10_000L + measureTenths
    }
}

fun lmsZScore(
    value: Double,
    parameters: LmsParameters,
    adjustWeightExtremes: Boolean,
): Double {
    val raw =
        if (abs(parameters.l) < 1e-12) {
            ln(value / parameters.m) / parameters.s
        } else {
            ((value / parameters.m).pow(parameters.l) - 1.0) / (parameters.s * parameters.l)
        }
    if (!adjustWeightExtremes || raw in -3.0..3.0) return raw
    val sd3Positive = lmsValueAtZ(3.0, parameters)
    val sd2Positive = lmsValueAtZ(2.0, parameters)
    val sd3Negative = lmsValueAtZ(-3.0, parameters)
    val sd2Negative = lmsValueAtZ(-2.0, parameters)
    return if (raw > 3.0) {
        3.0 + (value - sd3Positive) / (sd3Positive - sd2Positive)
    } else {
        -3.0 + (value - sd3Negative) / (sd2Negative - sd3Negative)
    }
}

fun lmsValueAtZ(
    z: Double,
    parameters: LmsParameters,
): Double =
    if (abs(parameters.l) < 1e-12) {
        parameters.m * kotlin.math.exp(parameters.s * z)
    } else {
        parameters.m * (1.0 + parameters.l * parameters.s * z).pow(1.0 / parameters.l)
    }

object WhoReferenceFiles {
    const val SOURCE_VERSION = "WHO Child Growth Standards / anthro 1.1.0.9000"
    const val INCLUDED_ON = "2026-07-16"
    val sha256 =
        mapOf(
            "head_circumference_for_age.tsv" to "e794e46f06b91223ad2c6435148dc08794a1d75b67613a652c3151201a98bf7c",
            "length_height_for_age.tsv" to "709f7a11881451daf7820f022d363d5bdb93746b5361d6bd9218af6ff838e0c2",
            "weight_for_age.tsv" to "bc15a6a623dd1d5beaeed1497666332aa54bc4ccd15ff9658c487d79694ab77b",
            "weight_for_height.tsv" to "0050d31041c2f7d4f8a34f27e8066fd73a24806835b9e4a0de1a7ee46d54d582",
            "weight_for_length.tsv" to "ad470cf41b147bd2a16026e57fd17968df7bf746b9f0bf2ff85df95239643778",
        )
}
