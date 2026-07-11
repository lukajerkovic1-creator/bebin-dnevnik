package hr.bebindnevnik.app.backup

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

class BackupManagerTest {
    @Test fun `encrypted backup round trip preserves UTF-8 data and records`() {
        val snapshot = snapshot(50)
        val encrypted = BackupManager.encrypt(snapshot, "sigurna-šifra".toCharArray())
        val preview = BackupManager.decrypt(encrypted, "sigurna-šifra".toCharArray())
        assertEquals(50, preview.mealCount)
        assertEquals(snapshot.meals, preview.snapshot.meals)
        assertEquals(snapshot.dailyEntries, preview.snapshot.dailyEntries)
        assertEquals(snapshot.tummySessions, preview.snapshot.tummySessions)
    }

    @Test fun `wrong password and modified file are rejected`() {
        val encrypted = BackupManager.encrypt(snapshot(1), "sigurna-šifra".toCharArray())
        assertThrows(InvalidBackupException::class.java) { BackupManager.decrypt(encrypted, "pogrešna-šifra".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()
        assertThrows(InvalidBackupException::class.java) { BackupManager.decrypt(encrypted, "sigurna-šifra".toCharArray()) }
    }

    @Test fun `unknown format version and truncated files are rejected`() {
        val encrypted = BackupManager.encrypt(snapshot(0), "sigurna-šifra".toCharArray())
        encrypted[7] = 99
        assertThrows(InvalidBackupException::class.java) { BackupManager.decrypt(encrypted, "sigurna-šifra".toCharArray()) }
        assertThrows(InvalidBackupException::class.java) { BackupManager.decrypt(byteArrayOf(1, 2, 3), "sigurna-šifra".toCharArray()) }
    }

    @Test fun `legacy version one backup imports stool as not recorded`() {
        val source = snapshot(1)
        val encrypted = BackupManager.encryptWithVersion(source, "sigurna-šifra".toCharArray(), 1)
        val imported = BackupManager.decrypt(encrypted, "sigurna-šifra".toCharArray()).snapshot
        assertEquals(null, imported.dailyEntries.single().stoolCount)
        assertEquals(source.meals, imported.meals)
        assertEquals(source.tummySessions, imported.tummySessions)
    }

    private fun snapshot(count: Int): AppSnapshot {
        val now = 1_700_000_000_000
        return AppSnapshot(
            List(count) { MealEntity((it + 1).toLong(), "2026-01-02", "08:00", it, now, now) },
            listOf(DailyEntryEntity("2026-01-02", TernaryStatus.DA, TernaryStatus.NE, true, now, now, 0)),
            listOf(TummySessionEntity(1, "2026-01-02", "09:00", 90, TummyInputMethod.RUCNO, now, now)),
            SettingsEntity(),
        )
    }
}
