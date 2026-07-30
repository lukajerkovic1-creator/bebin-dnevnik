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
    private val today = LocalDate.of(2026, 7, 30)
    private val now = LocalTime.of(14, 0)

    @Test fun `standalone conjunction plus and comma split multiple ingredients`() {
        assertNormalized("jabuka i mrkva", "Jabuka", "Mrkva")
        assertNormalized("jabuka + mrkva", "Jabuka", "Mrkva")
        assertNormalized("jabuka, mrkva", "Jabuka", "Mrkva")
        assertNormalized(
            "mrkva i krumpir + tikvica, jabuka",
            "Mrkva",
            "Krumpir",
            "Tikvica",
            "Jabuka",
        )
        assertNormalized(" batat   i   tikvica", "Batat", "Tikvica")
    }

    @Test fun `edge conjunction separators whitespace and empty tokens are removed`() {
        assertNormalized("i mrkva", "Mrkva")
        assertNormalized("+ jabuka", "Jabuka")
        assertNormalized("krumpir,", "Krumpir")
        assertNormalized("  maslinovo    ulje  ", "Maslinovo ulje")
        assertNormalized(",,, +++ i i , mrkva + , i", "Mrkva")
        listOf("", "   ", "i", "+", ",").forEach {
            assertTrue(ComplementaryFoodLogic.normalizeIngredients(listOf(it)).isEmpty())
        }
    }

    @Test fun `letter i inside an ingredient never splits the word`() {
        assertNormalized("tikvica", "Tikvica")
        assertNormalized("riža", "Riža")
        assertNormalized("piletina", "Piletina")
        assertNormalized("biskvit", "Biskvit")
    }

    @Test fun `duplicates are removed case insensitively while first order is preserved`() {
        assertNormalized("Mrkva i mrkva", "Mrkva")
        assertNormalized("mrkva, Mrkva", "Mrkva")
        assertNormalized("i mrkva + Mrkva", "Mrkva")
        assertEquals(
            listOf("Jabuka", "Mrkva"),
            ComplementaryFoodLogic.normalizeIngredients(listOf("Jabuka", "i mrkva", "mrkva")),
        )
    }

    @Test fun `Croatian teaspoon forms handle one paucal and eleven to fourteen`() {
        mapOf(
            1 to "1 žličica",
            2 to "2 žličice",
            3 to "3 žličice",
            4 to "4 žličice",
            5 to "5 žličica",
            11 to "11 žličica",
            12 to "12 žličica",
            14 to "14 žličica",
            21 to "21 žličica",
            22 to "22 žličice",
        ).forEach { (amount, expected) ->
            assertEquals(
                expected,
                ComplementaryFoodLogic.formatQuantity(amount, ComplementaryFoodUnit.TEASPOON),
            )
        }
    }

    @Test fun `at least one ingredient and nonnegative integer amount are mandatory`() {
        assertFalse(validate(emptyList(), 20).valid)
        assertFalse(validate(listOf("mrkva"), null).valid)
        assertFalse(validate(listOf("mrkva"), -1).valid)
        assertTrue(validate(listOf("mrkva"), 20).valid)
    }

    @Test fun `zero large metric quantity and over thirty teaspoons require confirmation`() {
        assertEquals(setOf(ComplementaryFoodWarning.ZERO), validate(listOf("mrkva"), 0).warnings)
        assertEquals(
            setOf(ComplementaryFoodWarning.OVER_500),
            validate(listOf("mrkva"), 501).warnings,
        )
        assertEquals(
            setOf(ComplementaryFoodWarning.OVER_30_TEASPOONS),
            validate(listOf("mrkva"), 31, ComplementaryFoodUnit.TEASPOON).warnings,
        )
        assertTrue(validate(listOf("mrkva"), 30, ComplementaryFoodUnit.TEASPOON).warnings.isEmpty())
    }

    @Test fun `normalized exact duplicate is a warning`() {
        val duplicate =
            meal(
                1,
                "2026-07-30",
                "12:00",
                listOf("Krumpir", "i mrkva"),
                20,
                ComplementaryFoodUnit.G,
            )
        val result =
            ComplementaryFoodLogic.validate(
                listOf("mrkva + krumpir"),
                30,
                ComplementaryFoodUnit.G,
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
        assertFalse(validate(listOf("mrkva"), 20, date = today.plusDays(1)).valid)
        assertFalse(validate(listOf("mrkva"), 20, time = now.plusMinutes(1)).valid)
        assertTrue(
            validate(
                listOf("mrkva"),
                20,
                date = today.minusDays(1),
                time = LocalTime.of(23, 59),
            ).valid,
        )
    }

    @Test fun `day totals never mix grams milliliters and teaspoons`() {
        val meals =
            listOf(
                meal(1, "2026-07-30", "09:30", listOf("mrkva"), 20, ComplementaryFoodUnit.G),
                meal(2, "2026-07-30", "13:00", listOf("krumpir"), 45, ComplementaryFoodUnit.ML),
                meal(3, "2026-07-30", "17:15", listOf("jabuka"), 6, ComplementaryFoodUnit.TEASPOON),
            )
        val summary = ComplementaryFoodLogic.daySummary(today, meals)
        assertEquals(3, summary.mealCount)
        assertEquals(20, summary.totalG)
        assertEquals(45, summary.totalMl)
        assertEquals(6, summary.totalTeaspoons)
        assertEquals(3L, summary.lastMeal?.id)
    }

    @Test fun `statistics keep teaspoon totals averages meals per day and normalized ingredients`() {
        val meals =
            listOf(
                meal(1, "2026-07-28", "09:00", listOf("i mrkva"), 2, ComplementaryFoodUnit.TEASPOON),
                meal(2, "2026-07-29", "12:00", listOf("mrkva", "Krumpir"), 4, ComplementaryFoodUnit.TEASPOON),
                meal(3, "2026-07-29", "17:00", listOf("Jabuka"), 30, ComplementaryFoodUnit.ML),
            )
        val result = ComplementaryFoodLogic.statistics(meals, today.minusDays(2), today)
        assertEquals(3, result.mealCount)
        assertEquals(6, result.totalTeaspoons)
        assertEquals(2, result.teaspoonMealCount)
        assertEquals(3.0, result.averageTeaspoonsPerMeal, 0.0)
        assertEquals(3, result.differentIngredientCount)
        assertEquals("Mrkva", result.ingredientFrequencies.first().name)
        assertEquals(2, result.ingredientFrequencies.first().count)
        assertEquals(
            4,
            result.days
                .single { it.date == today.minusDays(1) }
                .totalTeaspoons,
        )
    }

    @Test fun `suggestions are normalized unique and ordered by recent use`() {
        val older =
            meal(
                1,
                "2026-07-28",
                "09:00",
                listOf("i mrkva", "krumpir,"),
                20,
                ComplementaryFoodUnit.G,
                updatedAt = 1,
            )
        val newer =
            meal(
                2,
                "2026-07-29",
                "09:00",
                listOf("+ jabuka", "MRKVA"),
                20,
                ComplementaryFoodUnit.G,
                updatedAt = 2,
            )
        assertEquals(
            listOf("Jabuka", "MRKVA", "Krumpir"),
            ComplementaryFoodLogic.suggestions(listOf(older, newer)),
        )
    }

    private fun assertNormalized(
        input: String,
        vararg expected: String,
    ) {
        assertEquals(expected.toList(), ComplementaryFoodLogic.normalizeIngredients(listOf(input)))
    }

    private fun validate(
        ingredients: List<String>,
        amount: Int?,
        unit: ComplementaryFoodUnit = ComplementaryFoodUnit.G,
        date: LocalDate = today,
        time: LocalTime = LocalTime.NOON,
    ) = ComplementaryFoodLogic.validate(
        ingredients,
        amount,
        unit,
        date,
        time,
        today,
        now,
        emptyList(),
    )

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
