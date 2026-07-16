package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.domain.guidelines.FeedingTargetResult
import hr.bebindnevnik.app.domain.guidelines.TargetOrigin
import hr.bebindnevnik.app.domain.guidelines.TummyTargetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GuidelineUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun feedingSummaryShowsFullRangeAndRealPercentAboveOneHundredOnNarrowLargeFont() {
        var selectedMealCount = 0
        val result =
            FeedingTargetResult(
                recordedMl = 826,
                lowerMl = 700,
                upperMl = 850,
                percentOfLower = 118,
                visualProgress = 1f,
                origin = TargetOrigin.GUIDELINE,
                evidenceComplete = true,
                expectedMealCount = 6,
                perMealLowerMl = 115,
                perMealUpperMl = 140,
            )
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(320.dp)) {
                        FeedingGuidelineSummary(result, {}, { selectedMealCount = it })
                    }
                }
            }
        }
        rule.onNodeWithText("826 ml / okvirno 700–850 ml").assertIsDisplayed()
        rule.onNodeWithText("118 % donje granice").assertIsDisplayed()
        rule.onNodeWithContentDescription("Napredak 118 posto").assertIsDisplayed()
        rule.onNodeWithTag("feeding-guideline-summary").captureToImage().also { assertTrue(it.width > 0 && it.height > 0) }
        rule.onNodeWithTag("feeding-guideline-summary").performClick()
        rule.onNodeWithText("Kako se izračunava hranjenje?").assertIsDisplayed()
        rule.onNodeWithText("Promijeni očekivani broj obroka").performClick()
        rule.onNodeWithTag("number-dialog-input").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, selectedMealCount) }
    }

    @Test fun tummySummaryDisplaysActualPercentAboveOneHundredAndCapsVisualProgress() {
        val result =
            TummyTargetResult(
                recordedMinutes = 42,
                targetMinutes = 30,
                percent = 140,
                visualProgress = 1f,
                origin = TargetOrigin.INDIVIDUAL,
            )
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                TummyGuidelineSummary(result, {})
            }
        }
        rule.onNodeWithText("42/30 min · 140 %").assertIsDisplayed()
        rule.onNodeWithContentDescription("Napredak 140 posto").assertIsDisplayed()
        rule.onNodeWithTag("tummy-guideline-summary").performClick()
        rule.onNodeWithText("Kako se izračunava tummy time?").assertIsDisplayed()
    }
}
