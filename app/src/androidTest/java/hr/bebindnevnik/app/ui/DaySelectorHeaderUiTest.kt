package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.DayStatus
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DaySelectorHeaderUiTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 7, 11)

    @Test fun tappingTodayOpensPickerWithCurrentDateSelectedAndPastSelectionClosesIt() {
        showHeader(today)
        rule.onNodeWithText("Danas").performClick()
        rule.onNodeWithTag("date-picker-title").assertIsDisplayed()
        rule.onNodeWithTag("date-picker").assertContentDescriptionEquals("Odabrani datum 11.07.2026.")
        rule.onNodeWithText("July 10, 2026", substring = true).performClick()
        rule.onNodeWithText("10.07.2026.").assertIsDisplayed()
        rule.onNodeWithTag("date-picker-title").assertDoesNotExist()
    }

    @Test fun tappingDisplayedPastDateOpensPicker() {
        showHeader(today.minusDays(1))
        rule.onNodeWithText("10.07.2026.").performClick()
        rule.onNodeWithTag("date-picker-title").assertIsDisplayed()
    }

    @Test fun previousNextAndGoTodayObeyTodayBoundary() {
        showHeader(today)
        rule.onNodeWithTag("next-day").assertIsNotEnabled()
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithText("10.07.2026.").assertIsDisplayed()
        rule.onNodeWithTag("next-day").assertIsEnabled().performClick()
        rule.onNodeWithText("Danas").assertIsDisplayed()
        rule.onNodeWithTag("previous-day").performClick()
        rule.onNodeWithTag("go-today").assertIsDisplayed().performClick()
        rule.onNodeWithText("Danas").assertIsDisplayed()
        rule.onNodeWithTag("next-day").assertIsNotEnabled()
    }

    @Test fun headerFitsNarrowWidthLargeFontAndDarkTheme() {
        var selected by mutableStateOf(today)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * 1.2f, fontScale = 2f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(300.dp)) {
                        DaySelectorHeader(selected, DayStatus.BEZ_PODATAKA, { selected = it }, today)
                    }
                }
            }
        }
        rule.onNodeWithTag("day-selector").assertIsDisplayed().assertTextContains("Danas")
        rule.onNodeWithTag("previous-day").assertIsDisplayed()
        rule.onNodeWithTag("next-day").assertIsDisplayed()
    }

    private fun showHeader(initialDate: LocalDate) {
        var selected by mutableStateOf(initialDate)
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                DaySelectorHeader(selected, DayStatus.BEZ_PODATAKA, { selected = it }, today)
            }
        }
    }
}
