package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.DaySummary
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummySessionEntity
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

enum class StatisticsPeriod { SEVEN_DAYS, THIRTY_DAYS, NINETY_DAYS, ALL, CUSTOM }

data class StatisticsSelection(
    val period: StatisticsPeriod = StatisticsPeriod.SEVEN_DAYS,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
)

data class TrendComparison(
    val totalMlPercent: Int?,
    val mealCountPercent: Int?,
    val tummySecondsPercent: Int?,
    val stoolCountPercent: Int?,
    val completeDaysPercent: Int?,
)

data class FeedingStatistics(
    val totalMl: Int,
    val mealCount: Int,
    val averagePerMeal: Double,
    val averagePerAllDays: Double,
    val averagePerRecordedDay: Double,
    val largestMealMl: Int?,
    val highestDay: LocalDate?,
    val highestDayMl: Int,
    val timeBuckets: List<Int>,
)

data class TummyStatistics(
    val totalSeconds: Long,
    val recordedDays: Int,
    val sessionCount: Int,
    val averagePerRecordedDaySeconds: Double,
    val averageSessionSeconds: Double,
    val shortestSessionSeconds: Long?,
    val longestSessionSeconds: Long?,
    val mostActiveDay: LocalDate?,
    val mostActiveDaySeconds: Long,
    val mostActiveDaySessions: Int,
)

data class StoolPeriodStatistics(
    val total: Int,
    val recordedDays: Int,
    val averagePerRecordedDay: Double,
    val zeroDays: Int,
    val positiveDays: Int,
    val missingDays: Int,
    val maximum: Int?,
)

data class HabitStatistics(
    val yes: Int,
    val no: Int,
    val missing: Int,
)

data class CompletenessStatistics(
    val completeDays: Int,
    val partialDays: Int,
    val emptyDays: Int,
    val completePercent: Int,
    val missingCounts: Map<String, Int>,
    val mostOftenMissing: Pair<String, Int>?,
)

data class StatisticsDay(
    val date: LocalDate,
    val summary: DaySummary,
    val feedingRecorded: Boolean,
    val smallestMealMl: Int?,
    val largestMealMl: Int?,
    val tummyRecorded: Boolean,
    val shortestTummySeconds: Long?,
    val longestTummySeconds: Long?,
)

data class StatisticsReport(
    val selection: StatisticsSelection,
    val start: LocalDate,
    val end: LocalDate,
    val days: List<StatisticsDay>,
    val feeding: FeedingStatistics,
    val tummy: TummyStatistics,
    val stool: StoolPeriodStatistics,
    val waya: HabitStatistics,
    val exercise: HabitStatistics,
    val completeness: CompletenessStatistics,
    val complementaryFood: ComplementaryFoodStatistics,
    val comparison: TrendComparison,
) {
    val dayCount: Int get() = days.size
}

@Suppress("TooManyFunctions") // Calculation helpers stay together so every statistic uses identical period and missing-data rules.
object StatisticsCalculator {
    fun calculate(
        selection: StatisticsSelection,
        today: LocalDate,
        meals: List<MealEntity>,
        entries: List<DailyEntryEntity>,
        sessions: List<TummySessionEntity>,
        complementaryFoodMeals: List<ComplementaryFoodMealEntity> = emptyList(),
    ): StatisticsReport {
        val firstDataDate = firstDataDate(today, meals, entries, sessions, complementaryFoodMeals)
        val (start, end) = selection.bounds(today, firstDataDate)
        val days = buildDays(start, end, meals, entries, sessions)
        val previousEnd = start.minusDays(1)
        val previousStart = previousEnd.minusDays(days.size.toLong() - 1)
        val previousDays = buildDays(previousStart, previousEnd, meals, entries, sessions)
        return report(selection, start, end, days, previousDays, meals, sessions, complementaryFoodMeals)
    }

    private fun report(
        selection: StatisticsSelection,
        start: LocalDate,
        end: LocalDate,
        days: List<StatisticsDay>,
        previousDays: List<StatisticsDay>,
        meals: List<MealEntity>,
        sessions: List<TummySessionEntity>,
        complementaryFoodMeals: List<ComplementaryFoodMealEntity>,
    ): StatisticsReport {
        val dates = days.mapTo(hashSetOf()) { it.date.toString() }
        val periodMeals = meals.filter { it.date in dates }
        val periodSessions = sessions.filter { it.date in dates }
        val feeding = feeding(days, periodMeals)
        val tummy = tummy(days, periodSessions)
        val stool = stool(days)
        val completeness = completeness(days)
        val previousHasData = previousDays.any { it.summary.status != DayStatus.BEZ_PODATAKA }
        val previousComplete = previousDays.count { it.summary.status == DayStatus.POTPUNO }
        val previousStools = previousDays.mapNotNull { it.summary.stoolCount }.sum()
        val comparison =
            TrendComparison(
                totalMlPercent = percent(feeding.totalMl.toDouble(), previousDays.sumOf { it.summary.totalMl }.toDouble(), previousHasData),
                mealCountPercent = percent(feeding.mealCount.toDouble(), previousDays.sumOf { it.summary.mealCount }.toDouble(), previousHasData),
                tummySecondsPercent = percent(tummy.totalSeconds.toDouble(), previousDays.sumOf { it.summary.tummySeconds }.toDouble(), previousHasData),
                stoolCountPercent = percent(stool.total.toDouble(), previousStools.toDouble(), previousHasData && previousDays.any { it.summary.stoolCount != null }),
                completeDaysPercent = percent(completeness.completeDays.toDouble(), previousComplete.toDouble(), previousHasData),
            )
        return StatisticsReport(
            selection,
            start,
            end,
            days,
            feeding,
            tummy,
            stool,
            habit(days.map { it.summary.waya }),
            habit(days.map { it.summary.exercise }),
            completeness,
            ComplementaryFoodLogic.statistics(complementaryFoodMeals, start, end),
            comparison,
        )
    }

    private fun buildDays(
        start: LocalDate,
        end: LocalDate,
        meals: List<MealEntity>,
        entries: List<DailyEntryEntity>,
        sessions: List<TummySessionEntity>,
    ): List<StatisticsDay> {
        val mealsByDate = meals.groupBy { it.date }
        val entriesByDate = entries.associateBy { it.date }
        val sessionsByDate = sessions.groupBy { it.date }
        return generateSequence(start) { if (it < end) it.plusDays(1) else null }
            .map { date ->
                val dayMeals = mealsByDate[date.toString()].orEmpty()
                val daySessions = sessionsByDate[date.toString()].orEmpty()
                val dayEntries = entriesByDate[date.toString()]?.let(::listOf).orEmpty()
                val summary = AppLogic.summary(date, dayMeals, dayEntries, daySessions)
                StatisticsDay(
                    date = date,
                    summary = summary,
                    feedingRecorded = dayMeals.isNotEmpty(),
                    smallestMealMl = dayMeals.minOfOrNull { it.amountMl },
                    largestMealMl = dayMeals.maxOfOrNull { it.amountMl },
                    tummyRecorded = daySessions.isNotEmpty() || summary.noTummyTime,
                    shortestTummySeconds = daySessions.minOfOrNull { it.durationSeconds },
                    longestTummySeconds = daySessions.maxOfOrNull { it.durationSeconds },
                )
            }.toList()
    }

    private fun feeding(
        days: List<StatisticsDay>,
        meals: List<MealEntity>,
    ): FeedingStatistics {
        val total = meals.sumOf { it.amountMl }
        val recordedDays = days.count { it.feedingRecorded }
        val highest = days.filter { it.feedingRecorded }.maxByOrNull { it.summary.totalMl }
        val buckets = MutableList(4) { 0 }
        meals.forEach { meal -> buckets[(LocalTime.parse(meal.time).hour / 6).coerceIn(0, 3)]++ }
        return FeedingStatistics(
            totalMl = total,
            mealCount = meals.size,
            averagePerMeal = safeAverage(total.toDouble(), meals.size),
            averagePerAllDays = safeAverage(total.toDouble(), days.size),
            averagePerRecordedDay = safeAverage(total.toDouble(), recordedDays),
            largestMealMl = meals.maxOfOrNull { it.amountMl },
            highestDay = highest?.date,
            highestDayMl = highest?.summary?.totalMl ?: 0,
            timeBuckets = buckets,
        )
    }

    private fun tummy(
        days: List<StatisticsDay>,
        sessions: List<TummySessionEntity>,
    ): TummyStatistics {
        val total = sessions.sumOf { it.durationSeconds }
        val recordedDays = days.count { it.tummyRecorded }
        val active = days.filter { it.tummyRecorded }.maxByOrNull { it.summary.tummySeconds }
        return TummyStatistics(
            totalSeconds = total,
            recordedDays = recordedDays,
            sessionCount = sessions.size,
            averagePerRecordedDaySeconds = safeAverage(total.toDouble(), recordedDays),
            averageSessionSeconds = safeAverage(total.toDouble(), sessions.size),
            shortestSessionSeconds = sessions.minOfOrNull { it.durationSeconds },
            longestSessionSeconds = sessions.maxOfOrNull { it.durationSeconds },
            mostActiveDay = active?.date,
            mostActiveDaySeconds = active?.summary?.tummySeconds ?: 0,
            mostActiveDaySessions = active?.summary?.tummyCount ?: 0,
        )
    }

    private fun stool(days: List<StatisticsDay>): StoolPeriodStatistics {
        val recorded = days.mapNotNull { it.summary.stoolCount }
        return StoolPeriodStatistics(
            total = recorded.sum(),
            recordedDays = recorded.size,
            averagePerRecordedDay = if (recorded.isEmpty()) 0.0 else recorded.average(),
            zeroDays = recorded.count { it == 0 },
            positiveDays = recorded.count { it > 0 },
            missingDays = days.size - recorded.size,
            maximum = recorded.maxOrNull(),
        )
    }

    private fun habit(statuses: List<TernaryStatus>) =
        HabitStatistics(
            yes = statuses.count { it == TernaryStatus.DA },
            no = statuses.count { it == TernaryStatus.NE },
            missing = statuses.count { it == TernaryStatus.NIJE_EVIDENTIRANO },
        )

    private fun completeness(days: List<StatisticsDay>): CompletenessStatistics {
        val missing = linkedMapOf("obrok" to 0, "Waya kapi" to 0, "vježbanje" to 0, "stolica" to 0, "tummy time" to 0)
        days.forEach { day -> AppLogic.missing(day.summary).forEach { key -> missing[key] = missing.getValue(key) + 1 } }
        val complete = days.count { it.summary.status == DayStatus.POTPUNO }
        return CompletenessStatistics(
            completeDays = complete,
            partialDays = days.count { it.summary.status == DayStatus.DJELOMICNO },
            emptyDays = days.count { it.summary.status == DayStatus.BEZ_PODATAKA },
            completePercent = if (days.isEmpty()) 0 else complete * 100 / days.size,
            missingCounts = missing,
            mostOftenMissing = missing.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.toPair(),
        )
    }

    private fun percent(
        current: Double,
        previous: Double,
        hasPreviousData: Boolean,
    ): Int? {
        if (!hasPreviousData || !current.isFinite() || !previous.isFinite()) return null
        if (previous == 0.0) return if (current == 0.0) 0 else null
        return (((current - previous) / previous) * 100).roundToInt()
    }

    private fun safeAverage(
        total: Double,
        count: Int,
    ): Double = if (count <= 0) 0.0 else total / count

    private fun firstDataDate(
        today: LocalDate,
        meals: List<MealEntity>,
        entries: List<DailyEntryEntity>,
        sessions: List<TummySessionEntity>,
        complementaryFoodMeals: List<ComplementaryFoodMealEntity>,
    ): LocalDate =
        (meals.map { it.date } + entries.map { it.date } + sessions.map { it.date } + complementaryFoodMeals.map { it.date })
            .minOrNull()
            ?.let(LocalDate::parse)
            ?.coerceAtMost(today)
            ?: today

    private fun StatisticsSelection.bounds(
        today: LocalDate,
        firstDataDate: LocalDate,
    ): Pair<LocalDate, LocalDate> {
        val end = (customEnd ?: today).coerceAtMost(today)
        val start =
            when (period) {
                StatisticsPeriod.SEVEN_DAYS -> today.minusDays(6)
                StatisticsPeriod.THIRTY_DAYS -> today.minusDays(29)
                StatisticsPeriod.NINETY_DAYS -> today.minusDays(89)
                StatisticsPeriod.ALL -> firstDataDate
                StatisticsPeriod.CUSTOM -> (customStart ?: end).coerceAtMost(end)
            }
        val actualEnd = if (period == StatisticsPeriod.CUSTOM) end else today
        return start.coerceAtMost(actualEnd) to actualEnd
    }
}
