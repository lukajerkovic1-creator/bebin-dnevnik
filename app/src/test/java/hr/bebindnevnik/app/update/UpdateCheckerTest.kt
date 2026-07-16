package hr.bebindnevnik.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val validRelease =
        """
        {
          "tag_name":"v1.2.0", "draft":false, "prerelease":false,
          "body":"Sigurno ažuriranje", "published_at":"2026-07-12T10:00:00Z",
          "assets":[
            {"name":"BebinDnevnik.apk","browser_download_url":"https://github.com/lukajerkovic1-creator/bebin-dnevnik/releases/download/v1.2.0/BebinDnevnik.apk"},
            {"name":"BebinDnevnik.apk.sha256","browser_download_url":"https://github.com/lukajerkovic1-creator/bebin-dnevnik/releases/download/v1.2.0/BebinDnevnik.apk.sha256"}
          ]
        }
        """.trimIndent()

    @Test fun parsesOfficialProductionReleaseAndVersionManifest() {
        val update = UpdateChecker.parseRelease(validRelease)
        val manifest = UpdateChecker.parseVersionManifest("""{"versionName":"1.2.0","versionCode":1002000}""")
        assertEquals("1.2.0", update.versionName)
        assertEquals("Sigurno ažuriranje", update.releaseNotes)
        assertEquals(1002000, manifest.second)
    }

    @Test fun rejectsDraftPrereleaseMissingAssetAndUntrustedUrl() {
        assertThrows(IllegalArgumentException::class.java) { UpdateChecker.parseRelease(validRelease.replace("\"draft\":false", "\"draft\":true")) }
        assertThrows(IllegalArgumentException::class.java) { UpdateChecker.parseRelease(validRelease.replace("\"prerelease\":false", "\"prerelease\":true")) }
        assertThrows(IllegalArgumentException::class.java) { UpdateChecker.parseRelease(validRelease.replace("BebinDnevnik.apk.sha256", "other.sha256")) }
        assertThrows(IllegalArgumentException::class.java) { UpdateChecker.parseRelease(validRelease.replace("https://github.com", "http://example.com")) }
    }

    @Test fun apkRulesAcceptOnlyNewerMatchingProductionPackageAndCertificate() {
        val certificate = ApkVerificationRules.PRODUCTION_CERT_SHA256
        val installed = ApkMetadata(ApkVerificationRules.PRODUCTION_PACKAGE, 5, certificate)
        assertEquals(ApkValidation.Valid, ApkVerificationRules.validate(installed.copy(versionCode = 6), installed, certificate))
        assertTrue(ApkVerificationRules.validate(installed.copy(versionCode = 5), installed, certificate) is ApkValidation.Invalid)
        assertTrue(ApkVerificationRules.validate(installed.copy(packageName = "other.app", versionCode = 6), installed, certificate) is ApkValidation.Invalid)
        assertTrue(ApkVerificationRules.validate(installed.copy(versionCode = 6, certificateSha256 = "00"), installed, certificate) is ApkValidation.Invalid)
    }

    @Test fun semanticTagProvidesMonotonicFallbackWhenPagesManifestIsStale() {
        assertEquals(1_005_000, UpdateChecker.semanticVersionCode("1.5.0"))
        assertEquals(2_010_003, UpdateChecker.semanticVersionCode("2.10.3"))
        assertThrows(IllegalArgumentException::class.java) { UpdateChecker.semanticVersionCode("1.5") }
        assertThrows(IllegalArgumentException::class.java) { UpdateChecker.semanticVersionCode("1.1000.0") }
    }
}
