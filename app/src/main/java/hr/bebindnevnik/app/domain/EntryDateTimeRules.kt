package hr.bebindnevnik.app.domain

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

enum class EntryDateTimeError {
    FUTURE_DATE,
    FUTURE_TIME,
    INVALID_LOCAL_TIME,
}

object EntryDateTimeRules {
    fun validate(
        date: LocalDate,
        time: LocalTime,
        clock: Clock = Clock.systemDefaultZone(),
    ): EntryDateTimeError? {
        val today = LocalDate.now(clock)
        if (date.isAfter(today)) return EntryDateTimeError.FUTURE_DATE

        val localDateTime = LocalDateTime.of(date, time)
        val validOffsets = clock.zone.rules.getValidOffsets(localDateTime)
        if (validOffsets.isEmpty()) return EntryDateTimeError.INVALID_LOCAL_TIME

        if (date == today) {
            val now = clock.instant()
            val earliestOccurrence = ZonedDateTime.ofLocal(localDateTime, clock.zone, validOffsets.first()).toInstant()
            if (earliestOccurrence.isAfter(now)) return EntryDateTimeError.FUTURE_TIME
        }
        return null
    }

    fun message(error: EntryDateTimeError): String =
        when (error) {
            EntryDateTimeError.FUTURE_DATE -> "Budući datum nije dopušten."
            EntryDateTimeError.FUTURE_TIME -> "Vrijeme za današnji datum ne smije biti u budućnosti."
            EntryDateTimeError.INVALID_LOCAL_TIME -> "Odabrano vrijeme ne postoji zbog promjene računanja vremena."
        }

    fun requireValid(
        date: LocalDate,
        time: LocalTime,
        clock: Clock = Clock.systemDefaultZone(),
    ) {
        validate(date, time, clock)?.let { error -> throw IllegalArgumentException(message(error)) }
    }
}
