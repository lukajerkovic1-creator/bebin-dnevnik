package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ComplementaryFoodLogicTest {
    private val today = LocalDate.of(2026, 7, 16)
    private val now = LocalTime.of(14, 0)

    @Test fun `normalization preserves names while removing redundant whitespace and duplicates`() {
        assertEquals(listOf("mrkva", "Maslinovo ulje"), ComplementaryFoodLogic.normalizeIngredients(listOf("  mrkva ", "Maslinovo   ulje", "MRKVA")))
    }

    @Test fun `at least one ingredient and nonnegative integer amount are mandatory`() {
        assertFalse(validate(emptyList(), 20).valid)
        assertFalse(validate(listOf("mrkva"), null).valid)
        assertFalse(validate(listOf("mrkva"), -1).valid)
        assertTrue(validate(listOf("mrkva"), 20).valid)
    }

    @Test fun `zero over five hundred and exact duplicate are warnings not blocking errors`() {
        assertEquals(setOf(ComplementaryFoodWarning.ZERO), validate(listOf("mrkva"), 0).warnings)
        assertEquals(setOf(ComplementaryFoodWarning.OVER_500), validate(listOf("mrkva"), 501).warnings)
        val duplicate = meal(1, "2026-07-16", "12:00", listOf("krumpir", "mrkva"), 20, ComplementaryFoodUnit.G)
        val result =
            ComplementaryFoodLogic.validate(
                listOf("Mrkva", "krumpir"),
                30,
                today,
                LocalTime.NOON,
                today,
                now,
                listOf(duplicate),
            )
        assertTrue(result.valid)
        assertTrue(ComplementaryFoodWarning.POSSIBLE_DUPLICATE in result.warnings)
    }

    @Test fun `future date and future time today are blocked but any past time is allowed`() {
        assertFalse(validate(listOf("mrkva"), 20, today.plusDays(1), LocalTime.NOON).valid)
        assertFalse(validate(listOf("mrkva"), 20, today, now.plusMinutes(1)).valid)
        assertTrue(validate(listOf("mrkva"), 20, today.minusDays(1), LocalTime.of(23, 59)).valid)
    }

    @Test fun `multiple same day meals stay separate and gram and milliliter totals never mix`() {
        val meals =
            listOf(
                meal(1, "2026-07-16", "09:30", listOf("mrkva"), 20, ComplementaryFoodUnit.G),
                meal(2, "2026-07-16", "13:00", listOf("mrkva", "krumpir"), 45, ComplementaryFoodUnit.G),
                meal(3, "2026-07-16", "17:15", listOf("jabuka"), 30, ComplementaryFoodUnit.ML),
            )
        val summary = ComplementaryFoodLogic.daySummary(today, meals)
        assertEquals(3, summary.mealCount)
        assertEquals(65, summary.totalG)
        assertEquals(30, summary.totalMl)
        assertEquals(3L, summary.lastMeal?.id)
    }

    @Test fun `statistics count recorded days ingredients frequency diversity and first introduction`() {
        val meals =
            listOf(
                meal(1, "2026-07-14", "09:00", listOf("mrkva"), 20, ComplementaryFoodUnit.G),
                meal(2, "2026-07-15", "12:00", listOf("mrkva", "krumpir"), 40, ComplementaryFoodUnit.G),
                meal(3, "2026-07-15", "17:00", listOf("jabuka"), 30, ComplementaryFoodUnit.ML),
            )
        val result = ComplementaryFoodLogic.statistics(meals, today.minusDays(2), today)
        assertEquals(3, result.mealCount)
        assertEquals(2, result.recordedDays)
        assertEquals(60, result.totalG)
        assertEquals(30, result.totalMl)
        assertEquals(3, result.differentIngredientCount)
        assertEquals("mrkva", result.ingredientFrequencies.first().name)
        assertEquals(2, result.ingredientFrequencies.first().count)
        assertEquals(LocalDate.of(2026, 7, 14), result.ingredientFrequencies.first().firstRecordedDate)
        assertEquals(
            0,
            result.days
                .single { it.date == today }
                .meals.size,
        )
    }

    @Test fun `previously used ingredient suggestions are unique and ordered by recent use`() {
        val older = meal(1, "2026-07-14", "09:00", listOf("mrkva", "krumpir"), 20, ComplementaryFoodUnit.G, updatedAt = 1)
        val newer = meal(2, "2026-07-15", "09:00", listOf("jabuka", "MRKVA"), 20, ComplementaryFoodUnit.G, updatedAt = 2)
        assertEquals(listOf("jabuka", "MRKVA", "krumpir"), ComplementaryFoodLogic.suggestions(listOf(older, newer)))
    }

    private fun validate(
        ingredients: List<String>,
        amount: Int?,
        date: LocalDate = today,
        time: LocalTime = LocalTime.NOON,
    ) = ComplementaryFoodLogic.validate(ingredients, amount, date, time, today, now, emptyList())

    private fun meal(
        id: Long,
        date: String,
        time: String,
        ingredients: List<String>,
        amount: Int,
        unit: ComplementaryFoodUnit,
        updatedAt: Long = id,
    ) = ComplementaryFoodMealEntity(id, date, time, ingredients, amount, unit, id, updatedAt)
}
