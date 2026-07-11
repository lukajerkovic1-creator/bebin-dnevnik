package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.MealEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class EntryEditorsUiTest {
    @get:Rule val rule = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneId.of("Europe/Zagreb"))

    @Test fun allQuickAmountsPopulateTheFieldAndManualInputRemainsNumeric() {
        showMealEditor()
        rule.onNodeWithTag("illustration-bottle").assertExists()
        listOf(40, 80, 120, 160).forEach { amount ->
            rule.onNodeWithTag("quick-$amount").assertIsDisplayed().performClick()
            rule.onNodeWithTag("meal-amount").assertTextContains(amount.toString())
        }
        rule.onNodeWithTag("meal-amount").performTextReplacement("abc95-ml")
        rule.onNodeWithTag("meal-amount").assertTextContains("95")
        rule.onNodeWithTag("save-entry").assertIsEnabled()
    }

    @Test fun dateAndTimeRowsOpenMaterialPickers() {
        showMealEditor()
        rule.onNodeWithTag("date-row").performClick()
        rule.onNodeWithTag("date-picker-title").assertIsDisplayed()
    }

    @Test fun timeRowOpens24HourPicker() {
        showMealEditor()
        rule.onNodeWithTag("time-row").performClick()
        rule.onNodeWithTag("time-picker-title").assertIsDisplayed()
        rule.onNodeWithTag("time-picker").assertIsDisplayed()
    }

    @Test fun newEntryStartsWithLocalNowAndEditStartsWithStoredValues() {
        showMealEditor()
        rule.onNodeWithText("11.07.2026.").assertIsDisplayed()
        rule.onNodeWithText("12:00").assertIsDisplayed()
    }

    @Test fun editedEntryKeepsStoredDateTimeAndCancelDoesNotSave() {
        var savedAmount: Int? = null
        var closed = 0
        val existing = MealEntity(9, "2026-02-03", "21:45", 120, 1, 1)
        showMealEditor(
            item = existing,
            onSave = { _, _, _, amount -> savedAmount = amount },
            onClose = { closed++ },
        )
        rule.onNodeWithText("03.02.2026.").assertIsDisplayed()
        rule.onNodeWithText("21:45").assertIsDisplayed()
        rule.onNodeWithText("Odustani").performClick()
        rule.runOnIdle {
            assertNull(savedAmount)
            assertEquals(1, closed)
        }
    }

    @Test fun saveUsesSelectedAmountOnlyAfterExplicitSave() {
        var savedAmount: Int? = null
        showMealEditor(onSave = { _, _, _, amount -> savedAmount = amount })
        rule.onNodeWithTag("quick-80").performClick()
        rule.runOnIdle { assertNull(savedAmount) }
        rule.onNodeWithTag("save-entry").performClick()
        rule.runOnIdle { assertEquals(80, savedAmount) }
    }

    @Test fun futureTimeTodayShowsErrorAndDisablesSave() {
        val future = MealEntity(3, "2026-07-11", "12:01", 80, 1, 1)
        showMealEditor(item = future)
        rule.onNodeWithTag("date-time-error").assertIsDisplayed()
        rule.onNodeWithTag("save-entry").assertIsNotEnabled()
    }

    @Test fun quickGridFitsNarrowScreenWithLargeFontInDarkTheme() {
        rule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale = 2f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(280.dp)) {
                        QuantityQuickSelect(amount = "160", onAmountSelected = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("quick-40").assertIsDisplayed()
        rule.onNodeWithTag("quick-80").assertIsDisplayed()
        rule.onNodeWithTag("quick-120").assertIsDisplayed()
        rule.onNodeWithTag("quick-160").assertIsDisplayed()
        rule.onNodeWithText("160 ml").assertIsDisplayed()
    }

    @Test fun newEntryUsesCurrentlySelectedPastDate() {
        showMealEditor(defaultDate = LocalDate.of(2026, 7, 9))
        rule.onNodeWithText("09.07.2026.").assertIsDisplayed()
        rule.onNodeWithText("12:00").assertIsDisplayed()
    }

    @Test fun completeEditorRemainsScrollableWithLargeSystemFont() {
        rule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density * 1.2f, fontScale = 2f)) {
                BebinDnevnikTheme(AppTheme.SVIJETLA) {
                    MealEditorSheet(
                        item = null,
                        onWarnings = { _, _, _, _ -> emptySet() },
                        onSave = { _, _, _, _ -> },
                        onClose = {},
                        clock = clock,
                    )
                }
            }
        }
        rule.onNodeWithTag("quick-160").assertIsDisplayed()
        rule.onNodeWithTag("illustration-bottle").assertExists()
        rule.onNodeWithTag("save-entry").performScrollTo().assertIsDisplayed()
    }

    @Test fun futureDateIsNotSelectable() {
        val today = LocalDate.of(2026, 7, 11)
        val selectable = PastOrTodaySelectableDates(today)
        val tomorrowUtc =
            today
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val todayUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertFalse(selectable.isSelectableDate(tomorrowUtc))
        assertEquals(true, selectable.isSelectableDate(todayUtc))
    }

    private fun showMealEditor(
        item: MealEntity? = null,
        defaultDate: LocalDate? = null,
        onSave: (Long, LocalDate, LocalTime, Int) -> Unit = { _, _, _, _ -> },
        onClose: () -> Unit = {},
    ) {
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                MealEditorSheet(
                    item = item,
                    defaultDate = defaultDate,
                    onWarnings = { _, _, _, _ -> emptySet() },
                    onSave = onSave,
                    onClose = onClose,
                    clock = clock,
                )
            }
        }
    }
}
