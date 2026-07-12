package hr.bebindnevnik.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudRetentionTest {
    @Test fun retainsNewestFiveAndSelectsOnlyOlderVersionsForDeletion() {
        val files = List(8) { index -> DriveBackupFile(index.toString(), "backup-$index", "2026-07-${12 - index}", 10) }
        assertEquals(listOf("5", "6", "7"), CloudRetention.filesToDelete(files).map { it.id })
        assertEquals(emptyList<DriveBackupFile>(), CloudRetention.filesToDelete(files.take(5)))
    }
}
