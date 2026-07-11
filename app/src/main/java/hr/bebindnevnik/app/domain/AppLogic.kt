package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.DaySummary
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummySessionEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class EntryWarning { ZERO_ML, OVER_500_ML, DUPLICATE_TIME, UNDER_5_SECONDS, OVER_60_MINUTES }

enum class StatisticsRange { SEVEN_DAYS, THIRTY_DAYS, ALL }

object AppLogic {
    fun mealWarnings(
        amountMl: Int,
        date: LocalDate,
        time: LocalTime,
        meals: List<MealEntity>,
        editedId: Long = 0,
    ): Set<EntryWarning> {
        require(amountMl >= 0) { "Količina ne smije biti negativna." }
        EntryDateTimeRules.requireValid(date, time)
        return buildSet {
            if (amountMl == 0) add(EntryWarning.ZERO_ML)
            if (amountMl > 500) add(EntryWarning.OVER_500_ML)
            if (meals.any { it.id != editedId && it.date == date.toString() && it.time == time.withNano(0).toString() }) {
                add(EntryWarning.DUPLICATE_TIME)
            }
        }
    }

    fun tummyWarnings(
        seconds: Long,
        date: LocalDate,
        time: LocalTime,
    ): Set<EntryWarning> {
        require(seconds >= 0) { "Trajanje ne smije biti negativno." }
        EntryDateTimeRules.requireValid(date, time)
        return buildSet {
            if (seconds < 5) add(EntryWarning.UNDER_5_SECONDS)
            if (seconds > 3_600) add(EntryWarning.OVER_60_MINUTES)
        }
    }

    fun summary(
        date: LocalDate,
        meals: List<MealEntity>,
        entries: List<DailyEntryEntity>,
        sessions: List<TummySessionEntity>,
    ): DaySummary {
        val key = date.toString()
        val dayMeals = meals.filter { it.date == key }
        val daySessions = sessions.filter { it.date == key }
        val entry = entries.firstOrNull { it.date == key }
        val total = dayMeals.sumOf { it.amountMl }
        val waya = entry?.waya ?: TernaryStatus.NIJE_EVIDENTIRANO
        val exercise = entry?.exercise ?: TernaryStatus.NIJE_EVIDENTIRANO
        val noTummy = entry?.noTummyTime == true
        val complete =
            dayMeals.isNotEmpty() && waya != TernaryStatus.NIJE_EVIDENTIRANO &&
                exercise != TernaryStatus.NIJE_EVIDENTIRANO && (daySessions.isNotEmpty() || noTummy)
        val hasAny =
            dayMeals.isNotEmpty() || daySessions.isNotEmpty() || entry?.let {
                it.waya != TernaryStatus.NIJE_EVIDENTIRANO || it.exercise != TernaryStatus.NIJE_EVIDENTIRANO || it.noTummyTime
            } == true
        return DaySummary(
            date = key,
            totalMl = total,
            mealCount = dayMeals.size,
            averageMl = if (dayMeals.isEmpty()) 0.0 else total.toDouble() / dayMeals.size,
            lastMealTime = dayMeals.maxByOrNull { it.time }?.time,
            waya = waya,
            exercise = exercise,
            tummySeconds = daySessions.sumOf { it.durationSeconds },
            tummyCount = daySessions.size,
            noTummyTime = noTummy,
            status =
                when {
                    complete -> DayStatus.POTPUNO
                    hasAny -> DayStatus.DJELOMICNO
                    else -> DayStatus.BEZ_PODATAKA
                },
        )
    }

    fun elapsedText(
        date: LocalDate,
        time: LocalTime,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val minutes = Duration.between(LocalDateTime.of(date, time), now).toMinutes().coerceAtLeast(0)
        val days = minutes / 1_440
        val hours = (minutes % 1_440) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "prije $days d $hours h"
            hours > 0 -> "prije $hours h $mins min"
            else -> "prije $mins min"
        }
    }

    fun missing(summary: DaySummary): List<String> =
        buildList {
            if (summary.mealCount == 0) add("obrok")
            if (summary.waya == TernaryStatus.NIJE_EVIDENTIRANO) add("Waya kapi")
            if (summary.exercise == TernaryStatus.NIJE_EVIDENTIRANO) add("vježbanje")
            if (summary.tummyCount == 0 && !summary.noTummyTime) add("tummy time")
        }

    fun statistics(
        range: StatisticsRange,
        today: LocalDate,
        firstDataDate: LocalDate,
        meals: List<MealEntity>,
        entries: List<DailyEntryEntity>,
        sessions: List<TummySessionEntity>,
    ): List<DaySummary> {
        val start =
            when (range) {
                StatisticsRange.SEVEN_DAYS -> today.minusDays(6)
                StatisticsRange.THIRTY_DAYS -> today.minusDays(29)
                StatisticsRange.ALL -> minOf(firstDataDate, today)
            }
        return generateSequence(start) { date -> if (date < today) date.plusDays(1) else null }
            .map { date -> summary(date, meals, entries, sessions) }
            .toList()
    }

    fun shouldSendReminder(
        missing: List<String>,
        lastNotificationDate: String?,
        today: LocalDate,
    ): Boolean = missing.isNotEmpty() && lastNotificationDate != today.toString()
}
