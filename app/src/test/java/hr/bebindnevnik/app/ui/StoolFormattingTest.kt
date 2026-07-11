package hr.bebindnevnik.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StoolFormattingTest {
    @Test fun `Croatian stool count labels distinguish all forms`() {
        assertEquals("Nije evidentirano", stoolCountText(null))
        assertEquals("0 stolica", stoolCountText(0))
        assertEquals("1 stolica", stoolCountText(1))
        assertEquals("2 stolice", stoolCountText(2))
        assertEquals("4 stolice", stoolCountText(4))
        assertEquals("5 stolica", stoolCountText(5))
        assertEquals("12 stolica", stoolCountText(12))
        assertEquals("22 stolice", stoolCountText(22))
    }
}
