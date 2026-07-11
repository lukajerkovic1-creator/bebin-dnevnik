package hr.bebindnevnik.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FormattersTest {
    @Test fun `date uses Croatian format and time is always 24 hour`() {
        assertEquals("05.01.2026.", LocalDate.of(2026, 1, 5).hrDate())
        assertEquals("21:07", LocalTime.of(21, 7).hrTime())
    }
}
