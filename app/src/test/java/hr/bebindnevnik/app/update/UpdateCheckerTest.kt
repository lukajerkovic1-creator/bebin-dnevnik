package hr.bebindnevnik.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun parsesTrustedUpdateManifest() {
        val update =
            UpdateChecker.parse(
                """
                {
                  "versionCode": 2,
                  "versionName": "1.1.0",
                  "downloadUrl": "https://lukajerkovic1-creator.github.io/bebin-dnevnik/download/",
                  "releaseNotes": "Novi izgled"
                }
                """.trimIndent(),
            )

        assertEquals(2, update.versionCode)
        assertEquals("1.1.0", update.versionName)
        assertEquals("Novi izgled", update.releaseNotes)
    }

    @Test
    fun rejectsUntrustedOrInsecureDownloadUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateChecker.parse(
                """
                {
                  "versionCode": 2,
                  "versionName": "1.1.0",
                  "downloadUrl": "http://example.com/app.apk"
                }
                """.trimIndent(),
            )
        }
    }
}
