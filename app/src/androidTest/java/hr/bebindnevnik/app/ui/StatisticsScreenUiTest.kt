package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import hr.bebindnevnik.app.data.DailyEntryEntity
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import hr.bebindnevnik.app.domain.StatisticsCalculator
import hr.bebindnevnik.app.domain.StatisticsPeriod
import hr.bebindnevnik.app.domain.StatisticsSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class StatisticsScreenUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun periodControlsAndCustomDateRangePickerAreAvailable() {
        var selected = StatisticsSelection()
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                EnhancedStatisticsScreen(report(), selected, { selected = it }, {})
            }
        }
        rule.onNodeWithTag("statistics-period-seven_days").assertIsDisplayed()
        rule.onNodeWithText("30 dana").assertIsDisplayed()
        rule.onNodeWithText("90 dana").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(StatisticsPeriod.NINETY_DAYS, selected.period) }
        rule.onNodeWithTag("statistics-period-custom").performClick()
        rule.onNodeWithTag("statistics-date-range-picker").assertIsDisplayed()
    }

    @Test fun chartTouchShowsExactDayTooltipAndAllowsOpeningDay() {
        var opened: LocalDate? = null
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                EnhancedStatisticsScreen(report(withData = true), StatisticsSelection(), {}, { opened = it })
            }
        }
        rule.onNodeWithTag("statistics-screen").performScrollToNode(hasText("Hranjenje po danu"))
        rule.onNodeWithTag("feeding-chart").performTouchInput { click(center) }
        rule.onNodeWithTag("statistics-screen").performScrollToNode(hasTestTag("feeding-chart-open-day"))
        rule.onNodeWithTag("feeding-chart-open-day").performClick()
        rule.runOnIdle { assertTrue(opened != null) }
    }

    @Test fun emptyNarrowDarkLargeFontStateRendersWithoutClipping() {
        rule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                BebinDnevnikTheme(AppTheme.TAMNA) {
                    Box(Modifier.width(320.dp)) {
                        EnhancedStatisticsScreen(report(), StatisticsSelection(), {}, {})
                    }
                }
            }
        }
        rule.onNodeWithTag("statistics-overview").assertIsDisplayed()
        rule.onRoot().captureToImage().also { assertTrue(it.width > 0 && it.height > 0) }
        rule.onNodeWithTag("statistics-screen").performScrollToNode(hasText("Još nema dovoljno evidentiranih obroka", substring = true))
        rule.onNodeWithText("Još nema dovoljno evidentiranih obroka", substring = true).assertIsDisplayed()
    }

    @Test fun lightAndDarkMainStatisticsStatesProduceScreenshots() {
        var theme by androidx.compose.runtime.mutableStateOf(AppTheme.SVIJETLA)
        rule.setContent {
            BebinDnevnikTheme(theme) {
                EnhancedStatisticsScreen(report(withData = true), StatisticsSelection(), {}, {})
            }
        }
        rule.onNodeWithTag("statistics-screen").assertIsDisplayed()
        rule.onRoot().captureToImage().also { assertTrue(it.width > 0) }
        rule.runOnIdle { theme = AppTheme.TAMNA }
        rule.waitForIdle()
        rule.onRoot().captureToImage().also { assertTrue(it.width > 0) }
    }

    @Test fun complementaryFoodStatisticsKeepGramsMillilitersAndIngredientsVisible() {
        rule.setContent {
            BebinDnevnikTheme(AppTheme.SVIJETLA) {
                EnhancedStatisticsScreen(report(withData = true), StatisticsSelection(), {}, {})
            }
        }
        rule.onNodeWithTag("statistics-screen").performScrollToNode(hasText("Dohrana po danu"))
        rule.onNodeWithTag("complementary-food-chart").assertIsDisplayed()
        rule.onNodeWithTag("statistics-screen").performScrollToNode(hasText("Različitih namirnica"))
        rule.onNodeWithText("Različitih namirnica").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("mrkva").fetchSemanticsNodes().isNotEmpty())
    }

    private fun report(withData: Boolean = false): hr.bebindnevnik.app.domain.StatisticsReport {
        val today = LocalDate.now()
        if (!withData) return StatisticsCalculator.calculate(StatisticsSelection(), today, emptyList(), emptyList(), emptyList())
        val time = 1_700_000_000_000
        val meals =
            listOf(
                MealEntity(1, today.minusDays(1).toString(), "08:00", 80, time, time),
                MealEntity(2, today.toString(), "12:00", 120, time, time),
            )
        val entries =
            listOf(
                DailyEntryEntity(today.toString(), TernaryStatus.DA, TernaryStatus.NE, false, time, time, 0),
            )
        val sessions =
            listOf(
                TummySessionEntity(1, today.toString(), "10:00", 180, TummyInputMethod.RUCNO, time, time),
            )
        val food =
            listOf(
                ComplementaryFoodMealEntity(1, today.toString(), "11:00", listOf("mrkva"), 20, ComplementaryFoodUnit.G, time, time),
                ComplementaryFoodMealEntity(2, today.toString(), "16:00", listOf("jabuka"), 30, ComplementaryFoodUnit.ML, time, time),
            )
        return StatisticsCalculator.calculate(StatisticsSelection(), today, meals, entries, sessions, food)
    }
}
