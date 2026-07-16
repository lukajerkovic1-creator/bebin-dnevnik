package hr.bebindnevnik.app.domain.growth

import hr.bebindnevnik.app.data.ChildProfileEntity
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

private const val TERM_DAYS = 40 * 7
private const val PRETERM_WEEKS = 37
private const val FENTON_END_DAYS = 50 * 7L

fun calculateGrowthAges(
    profile: ChildProfileEntity,
    date: LocalDate,
): GrowthAges? {
    val birthDate = LocalDate.parse(profile.birthDate)
    val chronological = ChronoUnit.DAYS.between(birthDate, date)
    if (chronological < 0) return null
    val gestationalDays = profile.gestationalWeeks * 7 + profile.gestationalDays
    val correction = (TERM_DAYS - gestationalDays).coerceAtLeast(0)
    return GrowthAges(
        chronologicalDays = chronological,
        correctedDays =
            if (profile.gestationalWeeks < PRETERM_WEEKS && date.isBefore(birthDate.plusYears(2))) {
                chronological - correction
            } else {
                null
            },
        postmenstrualDays = gestationalDays + chronological,
        correctionDays = correction,
    )
}

fun referenceFor(
    profile: ChildProfileEntity,
    ages: GrowthAges,
    useCorrectedWhoAge: Boolean = true,
): Pair<GrowthReferenceSystem, GrowthAgeBasis> {
    val preterm = profile.gestationalWeeks < PRETERM_WEEKS
    if (preterm && ages.postmenstrualDays <= FENTON_END_DAYS) {
        return GrowthReferenceSystem.FENTON_2013_UNAVAILABLE to GrowthAgeBasis.POSTMENSTRUAL
    }
    return if (preterm && useCorrectedWhoAge && ages.correctedDays != null) {
        GrowthReferenceSystem.WHO_2006 to GrowthAgeBasis.CORRECTED
    } else {
        GrowthReferenceSystem.WHO_2006 to GrowthAgeBasis.CHRONOLOGICAL
    }
}

fun ageDaysFor(
    ages: GrowthAges,
    basis: GrowthAgeBasis,
): Long =
    when (basis) {
        GrowthAgeBasis.CHRONOLOGICAL -> ages.chronologicalDays
        GrowthAgeBasis.CORRECTED -> ages.correctedDays ?: ages.chronologicalDays
        GrowthAgeBasis.POSTMENSTRUAL -> ages.postmenstrualDays
    }

fun formatGrowthAge(
    birthDate: LocalDate,
    date: LocalDate,
    daysOverride: Long? = null,
): String {
    if (daysOverride != null) {
        if (daysOverride < 0) return "prije termina (${kotlin.math.abs(daysOverride)} dana)"
        val virtualBirth = date.minusDays(daysOverride)
        return formatPeriod(Period.between(virtualBirth, date), daysOverride)
    }
    val days = ChronoUnit.DAYS.between(birthDate, date)
    return formatPeriod(Period.between(birthDate, date), days)
}

private fun formatPeriod(
    period: Period,
    totalDays: Long,
): String =
    when {
        totalDays < 31 -> "$totalDays dana"
        period.years > 0 -> "${period.years} god ${period.months} mj"
        else -> "${period.months} mj ${period.days} dana"
    }
