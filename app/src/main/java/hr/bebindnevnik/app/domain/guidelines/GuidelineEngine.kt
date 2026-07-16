package hr.bebindnevnik.app.domain.guidelines

import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ExpectedMealCountEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.IndividualFeedingTargetEntity
import hr.bebindnevnik.app.data.IndividualTummyTargetEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.MilkCompletenessEntity
import hr.bebindnevnik.app.domain.growth.GrowthAgeBasis
import hr.bebindnevnik.app.domain.growth.calculateGrowthAges
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

const val GUIDELINE_DISCLAIMER = "Okvirna informacija; individualne potrebe može odrediti pedijatar."

enum class TargetOrigin { GUIDELINE, INDIVIDUAL }

enum class RangeStatus { BELOW, WITHIN, ABOVE }

data class GuidelineDefinition(
    val guidelineId: String,
    val version: String,
    val validFrom: LocalDate,
    val sourceName: String,
    val sourceTitle: String,
    val sourceDate: String,
    val ageFromDays: Int,
    val ageToDays: Int,
    val calculationType: String,
    val lowerValue: Int,
    val upperValue: Int,
    val unit: String,
    val limitation: String,
)

object GuidelineCatalog {
    val infantMilk =
        GuidelineDefinition(
            guidelineId = "NHS_FORMULA_ML_KG_2026_01",
            version = "1.0.0",
            validFrom = LocalDate.of(2026, 7, 16),
            sourceName = "NHS",
            sourceTitle = "Formula feeding – how much formula milk does my baby need?",
            sourceDate = "provjereno 16.07.2026.",
            ageFromDays = 7,
            ageToDays = 182,
            calculationType = "RANGE_ML_PER_KG_DAY",
            lowerValue = 150,
            upperValue = 200,
            unit = "ml/kg/dan",
            limitation = "Gruba smjernica za potpuno evidentirane ml; ne procjenjuje izravno dojenje.",
        )

    val olderInfantMilk =
        GuidelineDefinition(
            guidelineId = "NHS_COMPLEMENTARY_MILK_2026_01",
            version = "1.0.0",
            validFrom = LocalDate.of(2026, 7, 16),
            sourceName = "NHS / First Steps Nutrition Trust",
            sourceTitle = "Eating well: the first year",
            sourceDate = "provjereno 16.07.2026.",
            ageFromDays = 183,
            ageToDays = 364,
            calculationType = "BROAD_RANGE_ML_DAY",
            lowerValue = 400,
            upperValue = 600,
            unit = "ml/dan",
            limitation = "Širok raspon uz dohranu, izveden iz približno 600 ml u 7–9 i 400 ml u 10–12 mjeseci.",
        )

    val tummyTime =
        GuidelineDefinition(
            guidelineId = "WHO_TUMMY_TIME_2019_01",
            version = "1.0.0",
            validFrom = LocalDate.of(2019, 4, 2),
            sourceName = "World Health Organization",
            sourceTitle = "Guidelines on physical activity, sedentary behaviour and sleep for children under 5 years",
            sourceDate = "2019; provjereno 16.07.2026.",
            ageFromDays = 0,
            ageToDays = 364,
            calculationType = "MINUTES_PER_DAY",
            lowerValue = 30,
            upperValue = 30,
            unit = "min/dan",
            limitation = "Za budno, nadzirano i još nepokretno dojenče; raspoređeno tijekom dana.",
        )
}

data class AgeAtDate(
    val chronologicalDays: Long,
    val effectiveDays: Long,
    val postmenstrualDays: Long,
    val basis: GrowthAgeBasis,
    val preterm: Boolean,
    val beforeTerm: Boolean,
)

object AgeAtDateCalculator {
    fun calculate(
        profile: ChildProfileEntity,
        date: LocalDate,
    ): AgeAtDate? {
        val ages = calculateGrowthAges(profile, date) ?: return null
        val preterm = profile.gestationalWeeks < 37
        val beforeTerm = preterm && ages.postmenstrualDays < 40 * 7
        val corrected = ages.correctedDays
        return AgeAtDate(
            chronologicalDays = ages.chronologicalDays,
            effectiveDays = if (preterm && corrected != null) corrected else ages.chronologicalDays,
            postmenstrualDays = ages.postmenstrualDays,
            basis = if (preterm && corrected != null) GrowthAgeBasis.CORRECTED else GrowthAgeBasis.CHRONOLOGICAL,
            preterm = preterm,
            beforeTerm = beforeTerm,
        )
    }
}

data class ResolvedWeight(
    val grams: Int,
    val measurementDate: LocalDate,
    val measurementTime: LocalTime?,
    val fromBirthProfile: Boolean,
    val olderThanThirtyDays: Boolean,
)

object WeightAtDateResolver {
    fun resolve(
        profile: ChildProfileEntity,
        measurements: List<GrowthMeasurementEntity>,
        date: LocalDate,
    ): ResolvedWeight? {
        val eligible =
            measurements
                .asSequence()
                .filter { it.weightG != null && !LocalDate.parse(it.date).isAfter(date) }
                .maxWithOrNull(
                    compareBy<GrowthMeasurementEntity>({ LocalDate.parse(it.date) }, { LocalTime.parse(it.time) }, { it.id }),
                )
        val measurementDate = eligible?.let { LocalDate.parse(it.date) }
        if (eligible != null && measurementDate != null) {
            return ResolvedWeight(
                grams = requireNotNull(eligible.weightG),
                measurementDate = measurementDate,
                measurementTime = LocalTime.parse(eligible.time),
                fromBirthProfile = false,
                olderThanThirtyDays = ChronoUnit.DAYS.between(measurementDate, date) > 30,
            )
        }
        val birthDate = LocalDate.parse(profile.birthDate)
        val birthWeight = profile.birthWeightG ?: return null
        if (birthDate.isAfter(date)) return null
        return ResolvedWeight(
            grams = birthWeight,
            measurementDate = birthDate,
            measurementTime = null,
            fromBirthProfile = true,
            olderThanThirtyDays = ChronoUnit.DAYS.between(birthDate, date) > 30,
        )
    }
}

data class FeedingTargetResult(
    val recordedMl: Int,
    val lowerMl: Int? = null,
    val upperMl: Int? = null,
    val percentOfLower: Int? = null,
    val visualProgress: Float = 0f,
    val status: RangeStatus? = null,
    val origin: TargetOrigin? = null,
    val message: String? = null,
    val age: AgeAtDate? = null,
    val weight: ResolvedWeight? = null,
    val evidenceComplete: Boolean? = null,
    val expectedMealCount: Int? = null,
    val expectedMealCountIsManual: Boolean = false,
    val perMealLowerMl: Int? = null,
    val perMealUpperMl: Int? = null,
    val guideline: GuidelineDefinition? = null,
)

data class TummyTargetResult(
    val recordedMinutes: Int,
    val targetMinutes: Int? = null,
    val percent: Int? = null,
    val visualProgress: Float = 0f,
    val origin: TargetOrigin? = null,
    val message: String? = null,
    val age: AgeAtDate? = null,
    val independentlyMobile: Boolean = false,
    val guideline: GuidelineDefinition? = null,
)

data class DailyGuidelineResult(
    val feeding: FeedingTargetResult,
    val tummy: TummyTargetResult,
)

data class FeedingGuidelineStatistics(
    val averageRecordedMl: Int,
    val belowDays: Int,
    val withinDays: Int,
    val aboveDays: Int,
    val incompleteDays: Int,
    val withoutCalculationDays: Int,
    val averagePercentOfLower: Int?,
)

data class TummyGuidelineStatistics(
    val averageRecordedMinutes: Int,
    val achievedDays: Int,
    val notAchievedDays: Int,
    val withoutGoalOrEvidenceDays: Int,
    val averagePercent: Int?,
)

data class GuidelinePeriodStatistics(
    val enabled: Boolean,
    val feeding: FeedingGuidelineStatistics,
    val tummy: TummyGuidelineStatistics,
) {
    companion object {
        val EMPTY =
            GuidelinePeriodStatistics(
                false,
                FeedingGuidelineStatistics(0, 0, 0, 0, 0, 0, null),
                TummyGuidelineStatistics(0, 0, 0, 0, null),
            )
    }
}

@Suppress("TooManyFunctions") // Pure helpers are colocated so all target calculations share identical history rules.
object GuidelineEngine {
    fun calculate(
        snapshot: AppSnapshot,
        selectedDate: LocalDate,
        today: LocalDate,
    ): DailyGuidelineResult =
        DailyGuidelineResult(
            feeding = feeding(snapshot, selectedDate, today),
            tummy = tummy(snapshot, selectedDate),
        )

    fun statistics(
        snapshot: AppSnapshot,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
    ): GuidelinePeriodStatistics {
        val dates = generateSequence(start) { if (it < end) it.plusDays(1) else null }.toList()
        val feedingResults = dates.map { it to feeding(snapshot, it, today) }
        val completedFeeding = feedingResults.filter { (date, result) -> date.isBefore(today) && result.evidenceComplete == true }
        val feedingPercents = completedFeeding.mapNotNull { it.second.percentOfLower }
        val incomplete = feedingResults.count { it.second.evidenceComplete == false }
        val calculated = completedFeeding.filter { it.second.status != null }
        val tummyResults = dates.map { tummy(snapshot, it) }
        val tummyWithEvidence = tummyResults.filter { result -> result.targetMinutes != null && result.recordedMinutes > 0 }
        val tummyPercents = tummyWithEvidence.mapNotNull { it.percent }
        return GuidelinePeriodStatistics(
            enabled = snapshot.settings.guidelineTargetsEnabled,
            feeding =
                FeedingGuidelineStatistics(
                    averageRecordedMl = safeAverage(feedingResults.sumOf { it.second.recordedMl }, dates.size),
                    belowDays = calculated.count { it.second.status == RangeStatus.BELOW },
                    withinDays = calculated.count { it.second.status == RangeStatus.WITHIN },
                    aboveDays = calculated.count { it.second.status == RangeStatus.ABOVE },
                    incompleteDays = incomplete,
                    withoutCalculationDays = feedingResults.count { it.second.lowerMl == null },
                    averagePercentOfLower = feedingPercents.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                ),
            tummy =
                TummyGuidelineStatistics(
                    averageRecordedMinutes = safeAverage(tummyResults.sumOf { it.recordedMinutes }, dates.size),
                    achievedDays = tummyWithEvidence.count { requireNotNull(it.percent) >= 100 },
                    notAchievedDays = tummyWithEvidence.count { requireNotNull(it.percent) < 100 },
                    withoutGoalOrEvidenceDays = tummyResults.size - tummyWithEvidence.size,
                    averagePercent = tummyPercents.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                ),
        )
    }

    @Suppress("ReturnCount") // Each early return represents a medically significant no-target guard.
    fun feeding(
        snapshot: AppSnapshot,
        selectedDate: LocalDate,
        today: LocalDate,
    ): FeedingTargetResult {
        val recorded = snapshot.meals.filter { it.date == selectedDate.toString() }.sumOf { it.amountMl }
        if (!snapshot.settings.guidelineTargetsEnabled) return FeedingTargetResult(recordedMl = recorded)
        val profile =
            snapshot.childProfile
                ?: return FeedingTargetResult(recordedMl = recorded, message = "Za izračun dodajte datum rođenja i tjelesnu težinu.")
        val age =
            AgeAtDateCalculator.calculate(profile, selectedDate)
                ?: return FeedingTargetResult(recordedMl = recorded, message = "Odabrani datum je prije rođenja djeteta.")
        val weight =
            WeightAtDateResolver.resolve(profile, snapshot.growthMeasurements, selectedDate)
                ?: return FeedingTargetResult(
                    recordedMl = recorded,
                    age = age,
                    message = "Za izračun dodajte datum rođenja i tjelesnu težinu.",
                )
        val complete = active(snapshot.milkCompletenessHistory, selectedDate)?.complete
        val individual = activeFeedingTarget(snapshot.individualFeedingTargets, selectedDate)
        if (individual != null) {
            return feedingResult(
                recorded,
                individual.lowerMlPerDay,
                individual.upperMlPerDay,
                TargetOrigin.INDIVIDUAL,
                selectedDate,
                today,
                complete,
                age,
                weight,
                snapshot,
                null,
            )
        }
        if (age.beforeTerm) {
            return FeedingTargetResult(
                recordedMl = recorded,
                message = "Koristite individualni plan pedijatra/neonatologa.",
                age = age,
                weight = weight,
                evidenceComplete = complete,
            )
        }
        if (age.effectiveDays < 7) {
            return FeedingTargetResult(
                recordedMl = recorded,
                message = "U prvom tjednu koristite individualni plan hranjenja i znakove gladi i sitosti.",
                age = age,
                weight = weight,
                evidenceComplete = complete,
            )
        }
        val rule =
            when (age.effectiveDays) {
                in 7..182 -> GuidelineCatalog.infantMilk
                in 183..364 -> GuidelineCatalog.olderInfantMilk
                else -> null
            }
        if (rule == null) {
            return FeedingTargetResult(
                recordedMl = recorded,
                message = "Nakon 12 mjeseci prikazuje se stvarno evidentirani unos; cilj može odrediti pedijatar.",
                age = age,
                weight = weight,
                evidenceComplete = complete,
            )
        }
        val (lower, upper) =
            if (rule.calculationType == "RANGE_ML_PER_KG_DAY") {
                roundToTen(weight.grams / 1000.0 * rule.lowerValue) to roundToTen(weight.grams / 1000.0 * rule.upperValue)
            } else {
                rule.lowerValue to rule.upperValue
            }
        return feedingResult(
            recorded,
            lower,
            upper,
            TargetOrigin.GUIDELINE,
            selectedDate,
            today,
            complete,
            age,
            weight,
            snapshot,
            rule,
        )
    }

    @Suppress("ReturnCount") // Each early return represents a medically significant no-target guard.
    fun tummy(
        snapshot: AppSnapshot,
        selectedDate: LocalDate,
    ): TummyTargetResult {
        val minutes = (snapshot.tummySessions.filter { it.date == selectedDate.toString() }.sumOf { it.durationSeconds } / 60).toInt()
        if (!snapshot.settings.guidelineTargetsEnabled) return TummyTargetResult(recordedMinutes = minutes)
        val profile =
            snapshot.childProfile
                ?: return TummyTargetResult(recordedMinutes = minutes, message = "Za izračun dodajte datum rođenja i tjelesnu težinu.")
        val age =
            AgeAtDateCalculator.calculate(profile, selectedDate)
                ?: return TummyTargetResult(recordedMinutes = minutes, message = "Odabrani datum je prije rođenja djeteta.")
        val individual = activeTummyTarget(snapshot.individualTummyTargets, selectedDate)
        if (individual != null) {
            return tummyResult(minutes, individual.minutesPerDay, TargetOrigin.INDIVIDUAL, age, null)
        }
        val mobilityDate = profile.independentMobilityDate?.let(LocalDate::parse)
        if (mobilityDate != null && !selectedDate.isBefore(mobilityDate)) {
            return TummyTargetResult(
                recordedMinutes = minutes,
                message = "Dijete je od ovog datuma označeno kao samostalno pokretno.",
                age = age,
                independentlyMobile = true,
            )
        }
        if (age.beforeTerm) {
            return TummyTargetResult(
                recordedMinutes = minutes,
                message = "Za nedonošče prije termina koristite individualni plan pedijatra/fizioterapeuta.",
                age = age,
            )
        }
        if (age.effectiveDays !in 0..364) {
            return TummyTargetResult(
                recordedMinutes = minutes,
                message = "Za ovu dob nema generičkog tummy-time cilja; evidentirane minute ostaju vidljive.",
                age = age,
            )
        }
        return tummyResult(minutes, GuidelineCatalog.tummyTime.lowerValue, TargetOrigin.GUIDELINE, age, GuidelineCatalog.tummyTime)
    }

    fun intervalsOverlap(
        firstStart: LocalDate,
        firstEnd: LocalDate?,
        secondStart: LocalDate,
        secondEnd: LocalDate?,
    ): Boolean =
        !(
            (firstEnd != null && firstEnd < secondStart) ||
                (secondEnd != null && secondEnd < firstStart)
        )

    @Suppress("LongParameterList") // Inputs are explicit to keep the derived result deterministic and side-effect free.
    private fun feedingResult(
        recorded: Int,
        lower: Int,
        upper: Int,
        origin: TargetOrigin,
        selectedDate: LocalDate,
        today: LocalDate,
        complete: Boolean?,
        age: AgeAtDate,
        weight: ResolvedWeight,
        snapshot: AppSnapshot,
        rule: GuidelineDefinition?,
    ): FeedingTargetResult {
        val percent = if (complete == true && lower > 0) (recorded.toDouble() / lower * 100).roundToInt() else null
        val status =
            if (complete == true && selectedDate.isBefore(today)) {
                when {
                    recorded < lower -> RangeStatus.BELOW
                    recorded > upper -> RangeStatus.ABOVE
                    else -> RangeStatus.WITHIN
                }
            } else {
                null
            }
        val manual = activeExpectedMealCount(snapshot.expectedMealCountHistory, selectedDate)
        val automatic = if (manual == null) automaticMealCount(snapshot.meals, snapshot.milkCompletenessHistory, selectedDate) else null
        val expected = manual?.mealCount ?: automatic
        return FeedingTargetResult(
            recordedMl = recorded,
            lowerMl = lower,
            upperMl = upper,
            percentOfLower = percent,
            visualProgress = ((percent ?: 0) / 100f).coerceIn(0f, 1f),
            status = status,
            origin = origin,
            age = age,
            weight = weight,
            evidenceComplete = complete,
            expectedMealCount = expected,
            expectedMealCountIsManual = manual != null,
            perMealLowerMl = expected?.takeIf { it > 0 }?.let { roundToFive(lower.toDouble() / it) },
            perMealUpperMl = expected?.takeIf { it > 0 }?.let { roundToFive(upper.toDouble() / it) },
            guideline = rule,
        )
    }

    private fun tummyResult(
        recorded: Int,
        target: Int,
        origin: TargetOrigin,
        age: AgeAtDate,
        rule: GuidelineDefinition?,
    ): TummyTargetResult {
        val percent = if (target > 0) (recorded.toDouble() / target * 100).roundToInt() else 0
        return TummyTargetResult(
            recordedMinutes = recorded,
            targetMinutes = target,
            percent = percent,
            visualProgress = (percent / 100f).coerceIn(0f, 1f),
            origin = origin,
            age = age,
            guideline = rule,
        )
    }

    private fun automaticMealCount(
        meals: List<MealEntity>,
        completeness: List<MilkCompletenessEntity>,
        date: LocalDate,
    ): Int? {
        val counts =
            (0L..6L)
                .map { date.minusDays(it) }
                .filter { active(completeness, it)?.complete == true }
                .mapNotNull { day -> meals.count { it.date == day.toString() }.takeIf { it > 0 } }
        return if (counts.size >= 3) counts.average().roundToInt().coerceAtLeast(1) else null
    }

    private fun active(
        values: List<MilkCompletenessEntity>,
        date: LocalDate,
    ): MilkCompletenessEntity? =
        values
            .filter { active(LocalDate.parse(it.startDate), it.endDate?.let(LocalDate::parse), date) }
            .maxWithOrNull(compareBy({ LocalDate.parse(it.startDate) }, { it.id }))

    private fun activeExpectedMealCount(
        values: List<ExpectedMealCountEntity>,
        date: LocalDate,
    ): ExpectedMealCountEntity? =
        values
            .filter { active(LocalDate.parse(it.startDate), it.endDate?.let(LocalDate::parse), date) }
            .maxWithOrNull(compareBy({ LocalDate.parse(it.startDate) }, { it.id }))

    private fun activeFeedingTarget(
        values: List<IndividualFeedingTargetEntity>,
        date: LocalDate,
    ): IndividualFeedingTargetEntity? =
        values
            .filter { active(LocalDate.parse(it.startDate), it.endDate?.let(LocalDate::parse), date) }
            .maxWithOrNull(compareBy({ LocalDate.parse(it.startDate) }, { it.id }))

    private fun activeTummyTarget(
        values: List<IndividualTummyTargetEntity>,
        date: LocalDate,
    ): IndividualTummyTargetEntity? =
        values
            .filter { active(LocalDate.parse(it.startDate), it.endDate?.let(LocalDate::parse), date) }
            .maxWithOrNull(compareBy({ LocalDate.parse(it.startDate) }, { it.id }))

    private fun active(
        start: LocalDate,
        end: LocalDate?,
        date: LocalDate,
    ): Boolean = !date.isBefore(start) && (end == null || !date.isAfter(end))

    private fun roundToTen(value: Double): Int = (value / 10.0).roundToInt() * 10

    private fun roundToFive(value: Double): Int = (value / 5.0).roundToInt() * 5

    private fun safeAverage(
        total: Int,
        count: Int,
    ): Int = if (count <= 0) 0 else (total.toDouble() / count).roundToInt()
}
