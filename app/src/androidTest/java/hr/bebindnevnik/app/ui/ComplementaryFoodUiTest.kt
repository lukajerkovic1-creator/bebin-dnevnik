package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.ComplementaryFoodDaySummary
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import hr.bebindnevnik.app.domain.ComplementaryFoodLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ComplementaryFoodUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyCardAndPopulatedMultipleMealCardRenderSeparately() {
        val meals = listOf(meal(1, "09:30", listOf("mrkva"), 20, ComplementaryFoodUnit.G), meal(2, "13:00", listOf("jabuka"), 30, ComplementaryFoodUnit.ML))
        var shownMeals by mutableStateOf(emptyList<ComplementaryFoodMealEntity>())
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                val summary = ComplementaryFoodLogic.daySummary(LocalDate.of(2026, 7, 15), shownMeals)
                ComplementaryFoodCard(summary, shownMeals, true, {}, {}, {})
            }
        }
        rule.onNodeWithText("Dohrana još nije evidentirana.").assertIsDisplayed()
        rule.onNodeWithTag("add-complementary-food").assertIsEnabled()
        rule.runOnIdle { shownMeals = meals }
        rule.waitForIdle()
        rule.onNodeWithText("2 obroka dohrane").assertIsDisplayed()
        rule.onNodeWithText("20 g · 30 ml").assertIsDisplayed()
        rule.onNodeWithText("mrkva").assertIsDisplayed()
        rule.onAllNodesWithText("jabuka").assertCountEquals(2)
    }

    @Test fun freeIngredientChipsUnitAndSaveWorkAndChipCanBeRemoved() {
        var saved: ComplementaryFoodMealEntity? = null
        editor { saved = it }
        rule.onNodeWithTag("ingredient-input").performTextInput("maslinovo   ulje")
        rule.onNodeWithTag("add-ingredient").performClick()
        rule.onNodeWithTag("ingredient-chip-maslinovo ulje").assertIsDisplayed().performClick()
        rule.onNodeWithTag("ingredient-input").performTextInput("mrkva")
        rule.onNodeWithTag("add-ingredient").performClick()
        rule.onNodeWithTag("ingredient-input").performTextInput("krumpir")
        rule.onNodeWithTag("add-ingredient").performClick()
        rule.onNodeWithTag("complementary-food-amount").performTextInput("45")
        rule.onNodeWithTag("food-unit-ml").performClick()
        rule
            .onNodeWithTag("save-complementary-food")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        rule.runOnIdle {
            assertEquals(listOf("mrkva", "krumpir"), saved?.ingredients)
            assertEquals(45, saved?.amount)
            assertEquals(ComplementaryFoodUnit.ML, saved?.unit)
        }
    }

    @Test fun dateTimePickersAndUnusualValueConfirmationAreReachable() {
        editor {}
        rule.onNodeWithTag("date-row").performClick()
        rule.onNodeWithTag("date-picker").assertIsDisplayed()
        rule.onAllNodesWithText("Odustani")[1].performClick()
        rule.onNodeWithTag("time-row").performClick()
        rule.onNodeWithTag("time-picker").assertIsDisplayed()
        rule.onAllNodesWithText("Odustani")[1].performClick()
        rule.onNodeWithTag("ingredient-input").performTextInput("mrkva")
        rule.onNodeWithTag("add-ingredient").performClick()
        rule.onNodeWithTag("complementary-food-amount").performTextInput("0")
        rule.onNodeWithTag("save-complementary-food").performScrollTo().performClick()
        rule.onNodeWithText("Provjerite unos").assertIsDisplayed()
        rule.onNodeWithText("Količina je 0.", substring = true).assertIsDisplayed()
    }

    @Test fun darkNarrowLargeFontCardAndEditorRemainVisible() {
        rule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(320.dp)) {
                        ComplementaryFoodCard(ComplementaryFoodDaySummary(0, 0, 0, null), emptyList(), true, {}, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithTag("complementary-food-card").assertIsDisplayed()
        rule.onRoot().captureToImage().also { assertTrue(it.width > 0 && it.height > 0) }
    }

    private fun editor(onSave: (ComplementaryFoodMealEntity) -> Unit) {
        val today = LocalDate.of(2026, 7, 16)
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                ComplementaryFoodEditorSheet(
                    item = null,
                    defaultDate = today.minusDays(1),
                    today = today,
                    suggestions = listOf("jabuka"),
                    validate = { ingredients, amount, date, time, id ->
                        ComplementaryFoodLogic.validate(
                            ingredients,
                            amount,
                            date,
                            time,
                            today,
                            LocalTime.NOON,
                            emptyList(),
                            id,
                        )
                    },
                    onSave = onSave,
                    onClose = {},
                )
            }
        }
        rule.onNodeWithTag("complementary-food-editor").assertIsDisplayed()
        rule.onNodeWithText("jabuka").assertIsDisplayed()
    }

    private fun meal(
        id: Long,
        time: String,
        ingredients: List<String>,
        amount: Int,
        unit: ComplementaryFoodUnit,
    ) = ComplementaryFoodMealEntity(id, "2026-07-15", time, ingredients, amount, unit, id, id)
}
