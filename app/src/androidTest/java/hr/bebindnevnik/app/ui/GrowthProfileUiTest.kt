package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GrowthProfileUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun newProfileStartsWithCurrentDateAndMaterialDatePickerOpens() {
        var saved: ChildProfileEntity? = null
        val today = LocalDate.of(2026, 7, 16)
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                GrowthProfileEditor(null, today, {}, { saved = it })
            }
        }
        rule.onNodeWithText("16.07.2026.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Odaberi datum rođenja").performClick()
        rule.onNodeWithText("Odaberi").assertIsDisplayed()
        rule.onAllNodesWithText("Odustani")[1].performClick()
        rule.onNodeWithTag("growth-profile-name").performTextInput("Lana")
        rule
            .onNodeWithText("Spremi")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        rule.runOnIdle {
            assertEquals("Lana", saved?.name)
            assertEquals(today.toString(), saved?.birthDate)
        }
    }

    @Test fun editingUsesExistingValuesAndDarkNarrowLargeFontRenders() {
        val profile =
            ChildProfileEntity(
                name = "Lana",
                sex = ChildSex.DJEVOJCICA,
                birthDate = "2026-01-02",
                gestationalWeeks = 35,
                gestationalDays = 4,
                createdAt = 1,
                updatedAt = 2,
            )
        rule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(320.dp)) {
                        GrowthProfileEditor(profile, LocalDate.of(2026, 7, 16), {}, {})
                    }
                }
            }
        }
        rule.onNodeWithText("Uredi profil djeteta").assertIsDisplayed()
        rule.onNodeWithTag("growth-profile-name").assertIsDisplayed()
        rule.onNodeWithText("02.01.2026.").assertIsDisplayed()
        rule.onNodeWithTag("growth-profile-name").captureToImage().also { assertTrue(it.width > 0 && it.height > 0) }
    }
}
