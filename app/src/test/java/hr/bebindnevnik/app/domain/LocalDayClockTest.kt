package hr.bebindnevnik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LocalDayClockTest {
    @Test fun `new local day selects today and clears past edit mode`() {
        val previous = LocalDate.of(2026, 7, 12)

        val result = resolveLocalDayTransition(previous, previous.minusDays(2), previous.plusDays(1), pastDateEditMode = true)

        assertTrue(result.changed)
        assertEquals(previous.plusDays(1), result.currentLocalDate)
        assertEquals(previous.plusDays(1), result.selectedDate)
        assertFalse(result.pastDateEditMode)
    }

    @Test fun `same local day preserves selection and edit mode`() {
        val today = LocalDate.of(2026, 7, 13)
        val selected = today.minusDays(1)

        val result = resolveLocalDayTransition(today, selected, today, pastDateEditMode = true)

        assertFalse(result.changed)
        assertEquals(selected, result.selectedDate)
        assertTrue(result.pastDateEditMode)
    }

    @Test fun `timezone change recalculates current date from same instant`() {
        val instant = Instant.parse("2026-07-12T22:30:00Z")
        var zone = ZoneId.of("Europe/Zagreb")
        val source = LocalDayClock(Clock.fixed(instant, ZoneId.of("UTC"))) { zone }
        assertEquals(LocalDate.of(2026, 7, 13), source.today())

        zone = ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026, 7, 12), source.today())
    }

    @Test fun `delay targets next local midnight across daylight saving change`() {
        val zone = ZoneId.of("Europe/Zagreb")
        val beforeSpringChange = Clock.fixed(Instant.parse("2026-03-28T23:00:00Z"), ZoneId.of("UTC"))

        assertEquals(82_800_000L, LocalDayClock(beforeSpringChange) { zone }.millisUntilNextDay())
    }
}
