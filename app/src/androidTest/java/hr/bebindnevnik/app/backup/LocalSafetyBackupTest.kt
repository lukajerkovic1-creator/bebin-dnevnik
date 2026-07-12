package hr.bebindnevnik.app.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bebindnevnik.app.data.AppSnapshot
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSafetyBackupTest {
    @Test fun preUpdateSafetyBackupIsEncryptedAndRestorableAfterUpdateStyleRestart() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val now = System.currentTimeMillis()
        val snapshot = AppSnapshot(listOf(MealEntity(1, "2026-07-12", "08:00", 80, now, now)), emptyList(), emptyList(), SettingsEntity())
        LocalSafetyBackup.create(context, snapshot)
        assertTrue(LocalSafetyBackup.exists(context))
        assertEquals(snapshot.meals, LocalSafetyBackup.restore(context).snapshot.meals)
        val raw =
            context.filesDir
                .resolve("safety-backup/latest.bdk")
                .readBytes()
                .toString(Charsets.ISO_8859_1)
        assertTrue(!raw.contains("2026-07-12"))
    }
}
