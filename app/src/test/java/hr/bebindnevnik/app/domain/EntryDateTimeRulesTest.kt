package hr.bebindnevnik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EntryDateTimeRulesTest {
    private val zone = ZoneId.of("Europe/Zagreb")
    private val middayClock = Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), zone)

    @Test fun `future date and future time today are rejected`() {
        assertEquals(
            EntryDateTimeError.FUTURE_DATE,
            EntryDateTimeRules.validate(LocalDate.of(2026, 7, 12), LocalTime.NOON, middayClock),
        )
        assertEquals(
            EntryDateTimeError.FUTURE_TIME,
            EntryDateTimeRules.validate(LocalDate.of(2026, 7, 11), LocalTime.of(12, 1), middayClock),
        )
    }

    @Test fun `past date permits any valid time and earlier time today is accepted`() {
        assertNull(EntryDateTimeRules.validate(LocalDate.of(2026, 7, 10), LocalTime.of(23, 59), middayClock))
        assertNull(EntryDateTimeRules.validate(LocalDate.of(2026, 7, 11), LocalTime.of(11, 59), middayClock))
    }

    @Test fun `daylight saving gap is rejected and overlap is accepted`() {
        val afterSpringChange = Clock.fixed(Instant.parse("2026-03-29T04:00:00Z"), zone)
        assertEquals(
            EntryDateTimeError.INVALID_LOCAL_TIME,
            EntryDateTimeRules.validate(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), afterSpringChange),
        )
        val afterAutumnChange = Clock.fixed(Instant.parse("2026-10-25T04:00:00Z"), zone)
        assertNull(EntryDateTimeRules.validate(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), afterAutumnChange))
    }
}
