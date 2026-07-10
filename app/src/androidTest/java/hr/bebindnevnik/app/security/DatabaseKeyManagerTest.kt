package hr.bebindnevnik.app.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseKeyManagerTest {
    @Test fun passphraseIsRandomPersistedAndStoredOnlyWrapped() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferencesName = "database_key_test"
        context
            .getSharedPreferences(preferencesName, 0)
            .edit()
            .clear()
            .commit()
        val manager = DatabaseKeyManager(context, preferencesName, "bebin_dnevnik_database_key_test")
        val first = manager.getOrCreatePassphrase()
        val second = manager.getOrCreatePassphrase()
        assertArrayEquals(first, second)
        val stored = context.getSharedPreferences(preferencesName, 0).getString("wrapped", "").orEmpty()
        assertFalse(stored.contains(android.util.Base64.encodeToString(first, android.util.Base64.NO_WRAP)))
        first.fill(0)
        second.fill(0)
    }
}
