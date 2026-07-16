package hr.bebindnevnik.app.ui

import hr.bebindnevnik.app.domain.growth.GrowthAgeBasis
import hr.bebindnevnik.app.domain.growth.GrowthIndicator
import hr.bebindnevnik.app.domain.growth.GrowthMetricResult
import hr.bebindnevnik.app.domain.growth.GrowthReferenceSystem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val growthDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.", Locale.forLanguageTag("hr-HR"))
internal val growthTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("hr-HR"))

internal fun LocalDate.growthDate(): String = format(growthDateFormatter)

internal fun LocalTime.growthTime(): String = format(growthTimeFormatter)

internal fun GrowthReferenceSystem.label(): String =
    when (this) {
        GrowthReferenceSystem.WHO_2006 -> "WHO 2006"
        GrowthReferenceSystem.FENTON_2013_UNAVAILABLE -> "Fenton razdoblje"
    }

internal fun GrowthAgeBasis.label(): String =
    when (this) {
        GrowthAgeBasis.CHRONOLOGICAL -> "kronološka dob"
        GrowthAgeBasis.CORRECTED -> "korigirana dob"
        GrowthAgeBasis.POSTMENSTRUAL -> "postmenstrualna dob"
    }

internal fun GrowthIndicator.label(): String =
    when (this) {
        GrowthIndicator.WEIGHT_FOR_AGE -> "Težina prema dobi"
        GrowthIndicator.LENGTH_HEIGHT_FOR_AGE -> "Duljina/visina prema dobi"
        GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE -> "Opseg glave prema dobi"
        GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT -> "Težina prema duljini/visini"
    }

internal fun GrowthMetricResult.percentileText(): String =
    when {
        percentile == null -> unavailableReason ?: "Nema izračuna"
        percentile < .1 -> "ispod 0,1. percentila"
        percentile > 99.9 -> "iznad 99,9. percentila"
        else -> "${"%.1f".format(Locale.forLanguageTag("hr-HR"), percentile)}. percentil"
    }

internal fun GrowthMetricResult.zText(): String = zScore?.let { "z = ${"%.2f".format(Locale.forLanguageTag("hr-HR"), it)}" } ?: "z nije dostupan"
