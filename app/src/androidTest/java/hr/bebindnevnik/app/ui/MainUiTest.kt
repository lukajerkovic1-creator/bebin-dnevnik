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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MainUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun finishOnboardingIfNeeded() {
        var onboardingFinished = false
        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodesWithText("Započni bez vraćanja").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Kalendar").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Započni bez vraćanja").fetchSemanticsNodes().isNotEmpty()) {
            runCatching { rule.onNodeWithText("Započni bez vraćanja").performClick() }
            onboardingFinished = true
        }
        rule.waitUntil(timeoutMillis = 60_000) {
            rule.onAllNodesWithText("Kalendar").fetchSemanticsNodes().isNotEmpty()
        }
        if (onboardingFinished) {
            runCatching {
                rule.waitUntil(timeoutMillis = 3_000) {
                    rule.onAllNodesWithText("Ne sada").fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        if (rule.onAllNodesWithText("Ne sada").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Ne sada").performClick()
        }
        if (rule.onAllNodesWithText("Postavite okvirne ciljeve").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Odustani").performClick()
        }
    }

    @Test fun bottomNavigationOpensAllFourScreensAndPortraitIsLocked() {
        finishOnboardingIfNeeded()
        rule.onNodeWithText("Kalendar").performClick()
        rule.onNodeWithText("Prethodni", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Statistika").performClick()
        rule.onNodeWithTag("statistics-period-seven_days").assertIsDisplayed()
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("check-for-update"))
        rule.onNodeWithTag("check-for-update").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("Provjeri i preuzmi novu verziju").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Podaci i sigurnosne kopije"))
        rule.onNodeWithText("Podaci i sigurnosne kopije").assertIsDisplayed()
        rule.onNodeWithText("Danas").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("add-meal"))
        rule.onNodeWithTag("add-meal").assertIsDisplayed()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
    }

    @Test fun guidelineSettingsSwitchAndRestartableWizardRemainNonBlocking() {
        finishOnboardingIfNeeded()
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("start-guideline-wizard"))
        rule.onNodeWithTag("start-guideline-wizard").assertIsDisplayed().performClick()
        rule.onNodeWithText("Postavite okvirne ciljeve").assertIsDisplayed()
        rule.onNodeWithText("Korak 1 od 4").assertIsDisplayed()
        rule.onNodeWithText("Preskoči").performClick()
        rule.onNodeWithText("Korak 2 od 4").assertIsDisplayed()
        rule.onNodeWithText("Natrag").performClick()
        rule.onNodeWithText("Korak 1 od 4").assertIsDisplayed()
        rule.onNodeWithText("Odustani").performClick()
        rule.onNodeWithTag("start-guideline-wizard").assertIsDisplayed()
    }

    @Test fun mealQuickButtonsManualInputAndValidationDialogAreReachable() {
        finishOnboardingIfNeeded()
        rule.onAllNodesWithTag("illustration-bottle")[0].assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("add-meal"))
        rule.onNodeWithTag("add-meal").performClick()
        rule.onNodeWithText("80 ml").assertIsEnabled().performClick()
        rule.onNodeWithText("Spremi").assertIsEnabled()
        rule.onNodeWithText("Odustani").performClick()
    }

    @Test fun selectedPastDateSurvivesRecreationAndRelocksAfterBottomNavigation() {
        finishOnboardingIfNeeded()
        val past = LocalDate.now().minusDays(1)
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithText(past.hrDate()).assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText(past.hrDate()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(past.hrDate()).assertIsDisplayed()
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNodeWithText("Danas").performClick()
        rule.onNodeWithText(past.hrDate()).assertIsDisplayed()
        rule.onNodeWithTag("past-day-locked").assertIsDisplayed()
        rule.onNodeWithTag("unlock-past-day").performClick()
        rule.onNodeWithTag("confirm-unlock-past-day").performClick()
        rule.onNodeWithTag("past-day-edit-mode").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("add-meal"))
        rule.onNodeWithTag("add-meal").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("date-row").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("date-row").assertTextContains(past.hrDate())
        rule.onNodeWithText("Odustani").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("go-today"))
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

    @Test fun timerCanSaveRepeatedSessionsAndIsUnavailableForPastDates() {
        finishOnboardingIfNeeded()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tummy-card"))
        repeat(3) {
            rule.onNodeWithTag("timer-start").assertIsDisplayed().performClick()
            rule.onNodeWithTag("timer-stop").assertIsDisplayed().performClick()
            rule.onNodeWithTag("confirm-timer-save").assertIsDisplayed().performClick()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithTag("timer-start").fetchSemanticsNodes().isNotEmpty()
            }
        }
        rule.onNodeWithText("Pokreni novu sesiju").assertIsDisplayed()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("previous-day"))
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tummy-card"))
        assertTrue(rule.onAllNodesWithTag("timer-start").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("manual-tummy").fetchSemanticsNodes().isEmpty())
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("past-day-locked"))
        rule.onNodeWithTag("unlock-past-day").performClick()
        rule.onNodeWithTag("confirm-unlock-past-day").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tummy-card"))
        rule.onNodeWithTag("manual-tummy").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("timer-start").fetchSemanticsNodes().isEmpty())
    }

    @Test fun accessibilityDescriptionsExistForEditAndDeleteAfterDataEntry() {
        finishOnboardingIfNeeded()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("add-meal"))
        rule.onNodeWithTag("add-meal").performClick()
        rule.onNodeWithText("40 ml").performClick()
        rule.onNodeWithText("Spremi").performClick()
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithContentDescription("Uredi zapis").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithContentDescription("Uredi zapis")[0].performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Izbriši zapis")[0].assertIsDisplayed()
    }

    @Test fun stoolCardSavesZeroAndCanEditPreviousDay() {
        finishOnboardingIfNeeded()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stool-card"))
        rule.onNodeWithTag("edit-stool").performClick()
        rule.onNodeWithTag("stool-0").performClick()
        rule.onNodeWithTag("save-stool").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Trenutačno: 0 stolica").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stool-card"))
        rule.onNodeWithText("Trenutačno: 0 stolica").assertIsDisplayed()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("previous-day"))
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithTag("past-day-locked").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("edit-stool").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("unlock-past-day").performClick()
        rule.onNodeWithTag("confirm-unlock-past-day").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stool-card"))
        rule.onNodeWithTag("edit-stool").performClick()
        rule.onNodeWithTag("stool-3").performClick()
        rule.onNodeWithTag("save-stool").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Trenutačno: 3 stolice").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("stool-card"))
        rule.onNodeWithText("Trenutačno: 3 stolice").assertIsDisplayed()
    }

    @Test fun pastDayUnlockDialogCanBeCancelledAndDateChangeRelocks() {
        finishOnboardingIfNeeded()
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithTag("past-day-locked").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("add-meal").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("manual-tummy").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("unlock-past-day").performClick()
        rule.onNodeWithText("Odustani").performClick()
        rule.onNodeWithTag("past-day-locked").assertIsDisplayed()

        rule.onNodeWithTag("unlock-past-day").performClick()
        rule.onNodeWithTag("confirm-unlock-past-day").performClick()
        rule.onNodeWithTag("past-day-edit-mode").assertIsDisplayed()
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithTag("past-day-locked").assertIsDisplayed()
    }
}
