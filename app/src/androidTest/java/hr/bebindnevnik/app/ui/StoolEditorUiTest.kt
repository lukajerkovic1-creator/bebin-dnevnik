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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.domain.EntryWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class StoolEditorUiTest {
    @get:Rule val rule = createComposeRule()

    private val date = LocalDate.now().minusDays(1)

    @Test fun quickValuesZeroThroughFiveAreDistinctFromNotRecordedAndSaveExplicitly() {
        var saved: Int? = 99
        showEditor(onSave = { saved = it })
        listOf(0, 1, 2, 3, 4, 5).forEach { value ->
            rule.onNodeWithTag("stool-$value").assertIsDisplayed().performClick()
            rule.onNodeWithTag("stool-count").assertTextContains(value.toString())
        }
        rule.onNodeWithTag("stool-unset").performClick()
        rule.onNodeWithTag("stool-count").assertTextContains("—")
        rule.onNodeWithTag("save-stool").performClick()
        rule.runOnIdle { assertNull(saved) }
    }

    @Test fun plusAndMinusSupportValuesOverFiveWithoutGoingNegative() {
        showEditor(initial = 5)
        rule.onNodeWithTag("stool-plus").performClick()
        rule.onNodeWithTag("stool-count").assertTextContains("6")
        repeat(6) { rule.onNodeWithTag("stool-minus").performClick() }
        rule.onNodeWithTag("stool-count").assertTextContains("0")
        rule.onNodeWithTag("stool-minus").assertIsNotEnabled()
    }

    @Test fun valueOverFifteenRequiresAdditionalConfirmation() {
        var saved: Int? = null
        showEditor(initial = 15, onSave = { saved = it })
        rule.onNodeWithTag("stool-plus").performClick()
        rule.onNodeWithTag("save-stool").performClick()
        rule.onNodeWithText("Potvrdite visoku vrijednost").assertIsDisplayed()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Ipak spremi").performClick()
        rule.runOnIdle { assertEquals(16, saved) }
    }

    @Test fun previousDateCanBeEditedButFutureDateCannotBeSaved() {
        showEditor(date = date)
        rule.onNodeWithText(date.hrDate()).assertIsDisplayed()
        rule.onNodeWithTag("save-stool").assertIsEnabled()
    }

    @Test fun futureDateDisablesSave() {
        showEditor(date = LocalDate.now().plusDays(1))
        rule.onNodeWithTag("save-stool").assertIsNotEnabled()
    }

    @Test fun illustrationAndControlsFitNarrowDarkScreenWithLargeFont() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(280.dp)) {
                        StoolEditorSheet(null, date, { emptySet() }, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithTag("illustration-stool").assertExists()
        rule.onNodeWithTag("stool-5").assertIsDisplayed()
    }

    private fun showEditor(
        initial: Int? = null,
        date: LocalDate = this.date,
        onSave: (Int?) -> Unit = {},
    ) {
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                StoolEditorSheet(
                    initialCount = initial,
                    date = date,
                    onWarnings = { if (it > 15) setOf(EntryWarning.HIGH_STOOL_COUNT) else emptySet() },
                    onSave = onSave,
                    onClose = {},
                )
            }
        }
    }
}
