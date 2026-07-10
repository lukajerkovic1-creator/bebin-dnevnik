package hr.bebindnevnik.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderSchedulerTest {
    @Test fun `next reminder is today when time has not passed`() {
        val delay = ReminderScheduler.delayUntilNext(LocalTime.of(18, 0), LocalDateTime.of(2026, 3, 28, 17, 15))
        assertEquals(45, delay.toMinutes())
    }

    @Test fun `next reminder rolls to next local day after time passed`() {
        val delay = ReminderScheduler.delayUntilNext(LocalTime.of(18, 0), LocalDateTime.of(2026, 10, 24, 19, 0))
        assertEquals(23 * 60, delay.toMinutes())
    }
}
