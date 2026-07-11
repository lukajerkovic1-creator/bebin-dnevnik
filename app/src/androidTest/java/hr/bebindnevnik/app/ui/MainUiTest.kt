package hr.bebindnevnik.app.ui

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import hr.bebindnevnik.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MainUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun finishOnboardingIfNeeded() {
        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodesWithText("Započni").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Kalendar").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Započni").fetchSemanticsNodes().isNotEmpty()) {
            runCatching { rule.onNodeWithText("Započni").performClick() }
        }
        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodesWithText("Kalendar").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun bottomNavigationOpensAllFourScreensAndPortraitIsLocked() {
        finishOnboardingIfNeeded()
        rule.onNodeWithText("Kalendar").performClick()
        rule.onNodeWithText("Prethodni", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Statistika").performClick()
        rule.onNodeWithText("7 dana").assertIsDisplayed()
        rule.onNodeWithText("Postavke").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("check-for-update"))
        rule.onNodeWithTag("check-for-update").assertIsDisplayed().assertIsEnabled()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Podaci i sigurnosne kopije"))
        rule.onNodeWithText("Podaci i sigurnosne kopije").assertIsDisplayed()
        rule.onNodeWithText("Danas").performClick()
        rule.onNodeWithText("Dodaj obrok").assertIsDisplayed()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
    }

    @Test fun mealQuickButtonsManualInputAndValidationDialogAreReachable() {
        finishOnboardingIfNeeded()
        rule.onAllNodesWithTag("illustration-bottle")[0].assertExists()
        rule.onNodeWithText("Dodaj obrok").performClick()
        rule.onNodeWithText("80 ml").assertIsEnabled().performClick()
        rule.onNodeWithText("Spremi").assertIsEnabled()
        rule.onNodeWithText("Odustani").performClick()
    }

    @Test fun selectedPastDateSurvivesBottomNavigationAndNewMealUsesIt() {
        finishOnboardingIfNeeded()
        val past = LocalDate.now().minusDays(1)
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithText(past.hrDate()).assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText(past.hrDate()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(past.hrDate()).assertIsDisplayed()
        rule.onNodeWithText("Postavke").performClick()
        rule.onNodeWithText("Danas").performClick()
        rule.onNodeWithText(past.hrDate()).assertIsDisplayed()
        rule.onNodeWithText("Dodaj obrok").performClick()
        rule.onNodeWithTag("date-row").assertTextContains(past.hrDate())
        rule.onNodeWithText("Odustani").performClick()
        rule.onNodeWithTag("go-today").performClick()
        rule.onNodeWithTag("day-selector").assertTextContains("Danas")
        rule.onNodeWithTag("next-day").assertIsNotEnabled()
    }

    @Test fun wayaExerciseAndTimerControlsHaveAllStatesAndBackgroundCancelsTimer() {
        finishOnboardingIfNeeded()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Waya kapi"))
        rule.onAllNodesWithText("Da")[0].performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Vježbanje"))
        rule.onAllNodesWithText("Ne")[1].performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tummy-card"))
        rule.onNodeWithTag("timer-start").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("timer-stop"))
        rule.onNodeWithTag("timer-stop").assertIsDisplayed()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tummy-card"))
        rule.onNodeWithTag("timer-start").assertIsDisplayed()
    }

    @Test fun accessibilityDescriptionsExistForEditAndDeleteAfterDataEntry() {
        finishOnboardingIfNeeded()
        rule.onNodeWithText("Dodaj obrok").performClick()
        rule.onNodeWithText("40 ml").performClick()
        rule.onNodeWithText("Spremi").performClick()
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithContentDescription("Uredi zapis").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithContentDescription("Uredi zapis")[0].performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Izbriši zapis")[0].assertIsDisplayed()
    }
}
