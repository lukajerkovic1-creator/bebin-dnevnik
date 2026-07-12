package hr.bebindnevnik.app.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bebindnevnik.app.cloud.CloudBackupCodec
import hr.bebindnevnik.app.cloud.CloudKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudKeyManagerTest {
    @Test fun dekSurvivesProcessStyleRecreationAndPasswordCanBeChanged() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = CloudKeyManager(context)
        first.clear()
        first.enable("prva-lozinka".toCharArray())
        val dek = first.localDek()
        val recreated = CloudKeyManager(context)
        assertArrayEquals(dek, recreated.localDek())
        val newWrap = recreated.changePassword("druga-lozinka".toCharArray())
        val snapshot =
            hr.bebindnevnik.app.data
                .AppSnapshot(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    hr.bebindnevnik.app.data
                        .SettingsEntity(),
                )
        val bytes = CloudBackupCodec.encode(snapshot, dek, newWrap, 3)
        CloudBackupCodec.decode(bytes, "druga-lozinka".toCharArray())
        dek.fill(0)
        recreated.clear()
    }
}
