package hr.bebindnevnik.app.backup

import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.ExpectedMealCountEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.IndividualFeedingTargetEntity
import hr.bebindnevnik.app.data.IndividualTummyTargetEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.MilkCompletenessEntity
import hr.bebindnevnik.app.data.SettingsEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        assertEquals(snapshot.childProfile, preview.snapshot.childProfile)
        assertEquals(snapshot.growthMeasurements, preview.snapshot.growthMeasurements)
        assertEquals(snapshot.complementaryFoodMeals, preview.snapshot.complementaryFoodMeals)
        assertEquals(snapshot.milkCompletenessHistory, preview.snapshot.milkCompletenessHistory)
        assertEquals(snapshot.expectedMealCountHistory, preview.snapshot.expectedMealCountHistory)
        assertEquals(snapshot.individualFeedingTargets, preview.snapshot.individualFeedingTargets)
        assertEquals(snapshot.individualTummyTargets, preview.snapshot.individualTummyTargets)
        assertEquals(1, preview.growthCount)
        assertEquals(1, preview.complementaryFoodCount)
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
        assertNull(imported.childProfile)
        assertTrue(imported.growthMeasurements.isEmpty())
        assertTrue(imported.complementaryFoodMeals.isEmpty())
    }

    @Test fun `legacy version two backup imports without growth data`() {
        val source = snapshot(1)
        val encrypted = BackupManager.encryptWithVersion(source, "sigurna-šifra".toCharArray(), 2)
        val imported = BackupManager.decrypt(encrypted, "sigurna-šifra".toCharArray()).snapshot
        assertEquals(source.meals, imported.meals)
        assertNull(imported.childProfile)
        assertTrue(imported.growthMeasurements.isEmpty())
        assertTrue(imported.complementaryFoodMeals.isEmpty())
    }

    @Test fun `legacy version three backup imports without complementary food data`() {
        val source = snapshot(1)
        val encrypted = BackupManager.encryptWithVersion(source, "sigurna-šifra".toCharArray(), 3)
        val imported = BackupManager.decrypt(encrypted, "sigurna-šifra".toCharArray()).snapshot
        assertEquals(source.growthMeasurements, imported.growthMeasurements)
        assertTrue(imported.complementaryFoodMeals.isEmpty())
    }

    private fun snapshot(count: Int): AppSnapshot {
        val now = 1_700_000_000_000
        return AppSnapshot(
            List(count) { MealEntity((it + 1).toLong(), "2026-01-02", "08:00", it, now, now) },
            listOf(DailyEntryEntity("2026-01-02", TernaryStatus.DA, TernaryStatus.NE, true, now, now, 0)),
            listOf(TummySessionEntity(1, "2026-01-02", "09:00", 90, TummyInputMethod.RUCNO, now, now)),
            SettingsEntity(),
            ChildProfileEntity(name = "Žana", sex = ChildSex.DJEVOJCICA, birthDate = "2026-01-01", gestationalWeeks = 35, gestationalDays = 4, createdAt = now, updatedAt = now),
            listOf(GrowthMeasurementEntity(1, "2026-01-02", "10:00", 2_100, 44.2, headCircumferenceCm = 31.4, createdAt = now, updatedAt = now)),
            listOf(ComplementaryFoodMealEntity(1, "2026-01-02", "11:00", listOf("mrkva", "krumpir"), 45, ComplementaryFoodUnit.G, now, now)),
            listOf(MilkCompletenessEntity(1, "2026-01-01", complete = true, createdAt = now, updatedAt = now)),
            listOf(ExpectedMealCountEntity(1, "2026-01-01", mealCount = 6, createdAt = now, updatedAt = now)),
            listOf(IndividualFeedingTargetEntity(1, 650, 800, "2026-01-01", createdAt = now, updatedAt = now)),
            listOf(IndividualTummyTargetEntity(1, 30, "2026-01-01", createdAt = now, updatedAt = now)),
        )
    }
}
