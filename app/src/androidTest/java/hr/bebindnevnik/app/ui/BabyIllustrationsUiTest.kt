package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import org.junit.Rule
import org.junit.Test

class BabyIllustrationsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun allOriginalIllustrationsRenderInLightTheme() {
        showAllIllustrations(AppTheme.SVIJETLA)
        assertAllIllustrationsExist()
    }

    @Test
    fun allOriginalIllustrationsRenderInDarkTheme() {
        showAllIllustrations(AppTheme.TAMNA)
        assertAllIllustrationsExist()
    }

    private fun showAllIllustrations(theme: AppTheme) {
        rule.setContent {
            BebinDnevnikTheme(theme) {
                Row {
                    BabyIllustrationKind.entries.forEach { kind ->
                        BabyIllustration(kind, Modifier.size(64.dp))
                    }
                }
            }
        }
    }

    private fun assertAllIllustrationsExist() {
        BabyIllustrationKind.entries.forEach { kind ->
            rule.onNodeWithTag("illustration-${kind.name.lowercase()}").assertExists()
        }
    }

    @Test
    fun illustrationsRemainVisibleOnNarrowScreenWithLargeFont() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BebinDnevnikTheme(AppTheme.SVIJETLA) {
                    Column(Modifier.width(280.dp)) {
                        BabyIllustration(BabyIllustrationKind.BOTTLE, Modifier.size(70.dp))
                        BabyIllustration(BabyIllustrationKind.DROPS, Modifier.size(70.dp))
                        BabyIllustration(BabyIllustrationKind.EXERCISE, Modifier.size(70.dp))
                        BabyIllustration(BabyIllustrationKind.TUMMY, Modifier.size(70.dp))
                    }
                }
            }
        }
        rule.onNodeWithTag("illustration-bottle").assertExists()
        rule.onNodeWithTag("illustration-drops").assertExists()
        rule.onNodeWithTag("illustration-exercise").assertExists()
        rule.onNodeWithTag("illustration-tummy").assertExists()
    }
}
