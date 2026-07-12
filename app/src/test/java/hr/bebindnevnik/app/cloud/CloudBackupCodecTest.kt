package hr.bebindnevnik.app.cloud

import hr.bebindnevnik.app.backup.InvalidBackupException
import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudBackupCodecTest {
    @Test fun cloudEnvelopeRoundTripPreservesAllCategoriesAndMetadata() {
        val snapshot = snapshot()
        val (dek, wrap) = CloudBackupCodec.createKeyMaterial("cloud-lozinka".toCharArray())
        val encrypted = CloudBackupCodec.encode(snapshot, dek, wrap, 3)
        dek.fill(0)
        val decoded = CloudBackupCodec.decode(encrypted, "cloud-lozinka".toCharArray())
        assertEquals(snapshot, decoded.snapshot)
        assertEquals(1, decoded.metadata.mealCount)
        assertEquals(1, decoded.metadata.dailyCount)
        assertEquals(1, decoded.metadata.tummyCount)
        assertEquals(3, decoded.metadata.schemaVersion)
    }

    @Test fun wrongPasswordModifiedAndTruncatedCloudBackupsAreRejected() {
        val (dek, wrap) = CloudBackupCodec.createKeyMaterial("cloud-lozinka".toCharArray())
        val encrypted = CloudBackupCodec.encode(snapshot(), dek, wrap, 3)
        dek.fill(0)
        assertThrows(InvalidBackupException::class.java) { CloudBackupCodec.decode(encrypted, "kriva-lozinka".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()
        assertThrows(InvalidBackupException::class.java) { CloudBackupCodec.decode(encrypted, "cloud-lozinka".toCharArray()) }
        assertThrows(InvalidBackupException::class.java) { CloudBackupCodec.metadata(encrypted.copyOf(10)) }
    }

    @Test fun newPasswordWrapCanUseSameDekForFutureBackups() {
        val (dek, oldWrap) = CloudBackupCodec.createKeyMaterial("stara-lozinka".toCharArray())
        val oldBytes = CloudBackupCodec.encode(snapshot(), dek, oldWrap, 3)
        val newWrap = CloudBackupCodec.rewrapDek(dek, "nova-lozinka".toCharArray())
        val newBytes = CloudBackupCodec.encode(snapshot(), dek, newWrap, 3)
        dek.fill(0)
        assertEquals(snapshot(), CloudBackupCodec.decode(oldBytes, "stara-lozinka".toCharArray()).snapshot)
        assertEquals(snapshot(), CloudBackupCodec.decode(newBytes, "nova-lozinka".toCharArray()).snapshot)
    }

    private fun snapshot(): AppSnapshot {
        val now = 1_700_000_000_000
        return AppSnapshot(
            listOf(MealEntity(1, "2026-01-02", "08:00", 80, now, now)),
            listOf(DailyEntryEntity("2026-01-02", TernaryStatus.DA, TernaryStatus.NE, false, now, now, 2)),
            listOf(TummySessionEntity(1, "2026-01-02", "09:00", 90, TummyInputMethod.RUCNO, now, now)),
            SettingsEntity(reminderEnabled = false, reminderTime = "19:00", onboardingShown = true),
        )
    }
}
