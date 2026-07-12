package hr.bebindnevnik.app.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuildIdentityTest {
    @Test fun debugBuildHasSeparatePackageAndVisibleDebugLabel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("hr.bebindnevnik.app.debug", context.packageName)
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        assertEquals("Bebin dnevnik – Debug", label)
    }
}
