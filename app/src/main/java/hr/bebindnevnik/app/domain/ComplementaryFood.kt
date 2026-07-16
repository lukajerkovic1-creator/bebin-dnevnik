package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.ComplementaryFoodDaySummary
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import java.time.LocalDate
import java.time.LocalTime

enum class ComplementaryFoodWarning { ZERO, OVER_500, POSSIBLE_DUPLICATE }

data class ComplementaryFoodValidation(
    val valid: Boolean,
    val error: String? = null,
    val warnings: Set<ComplementaryFoodWarning> = emptySet(),
)

data class IngredientFrequency(
    val name: String,
    val count: Int,
    val firstRecordedDate: LocalDate,
)

data class ComplementaryFoodStatisticsDay(
    val date: LocalDate,
    val meals: List<ComplementaryFoodMealEntity>,
    val totalG: Int,
    val totalMl: Int,
)

data class ComplementaryFoodStatistics(
    val mealCount: Int,
    val recordedDays: Int,
    val totalG: Int,
    val totalMl: Int,
    val averageGPerMeal: Double,
    val averageMlPerMeal: Double,
    val ingredientFrequencies: List<IngredientFrequency>,
    val differentIngredientCount: Int,
    val recentlyIntroduced: List<IngredientFrequency>,
    val days: List<ComplementaryFoodStatisticsDay>,
)

object ComplementaryFoodLogic {
    private val repeatedWhitespace = Regex("\\s+")

    fun normalizeIngredient(value: String): String = value.trim().replace(repeatedWhitespace, " ")

    fun normalizeIngredients(values: List<String>): List<String> = values.map(::normalizeIngredient).filter(String::isNotEmpty).distinctBy { it.lowercase() }

    fun validate(
        ingredients: List<String>,
        amount: Int?,
        date: LocalDate,
        time: LocalTime,
        today: LocalDate,
        now: LocalTime,
        existing: List<ComplementaryFoodMealEntity>,
        editedId: Long = 0,
    ): ComplementaryFoodValidation {
        val normalized = normalizeIngredients(ingredients)
        val error =
            when {
                normalized.isEmpty() -> "Dodajte barem jednu namirnicu."
                amount == null -> "Unesite cijelu nenegativnu količinu."
                amount < 0 -> "Količina ne smije biti negativna."
                date.isAfter(today) -> "Budući datum nije dopušten."
                date == today && time.isAfter(now) -> "Buduće vrijeme za današnji dan nije dopušteno."
                else -> null
            }
        if (error != null) return ComplementaryFoodValidation(false, error)
        val canonical = normalized.map { it.lowercase() }.sorted()
        val duplicate =
            existing.any {
                it.id != editedId && it.date == date.toString() && LocalTime.parse(it.time) == time.withNano(0) &&
                    normalizeIngredients(it.ingredients).map(String::lowercase).sorted() == canonical
            }
        return ComplementaryFoodValidation(
            valid = true,
            warnings =
                buildSet {
                    if (amount == 0) add(ComplementaryFoodWarning.ZERO)
                    if ((amount ?: 0) > 500) add(ComplementaryFoodWarning.OVER_500)
                    if (duplicate) add(ComplementaryFoodWarning.POSSIBLE_DUPLICATE)
                },
        )
    }

    fun daySummary(
        date: LocalDate,
        meals: List<ComplementaryFoodMealEntity>,
    ): ComplementaryFoodDaySummary {
        val selected = meals.filter { it.date == date.toString() }
        return ComplementaryFoodDaySummary(
            mealCount = selected.size,
            totalG = selected.filter { it.unit == ComplementaryFoodUnit.G }.sumOf { it.amount },
            totalMl = selected.filter { it.unit == ComplementaryFoodUnit.ML }.sumOf { it.amount },
            lastMeal = selected.maxWithOrNull(compareBy<ComplementaryFoodMealEntity> { it.time }.thenBy { it.id }),
        )
    }

    fun suggestions(meals: List<ComplementaryFoodMealEntity>): List<String> =
        meals
            .sortedByDescending { it.updatedAt }
            .flatMap { it.ingredients }
            .map(::normalizeIngredient)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .take(20)

    fun statistics(
        meals: List<ComplementaryFoodMealEntity>,
        start: LocalDate,
        end: LocalDate,
    ): ComplementaryFoodStatistics {
        val selected = meals.filter { LocalDate.parse(it.date) in start..end }
        val days =
            generateSequence(start) { if (it < end) it.plusDays(1) else null }
                .map { date ->
                    val dayMeals = selected.filter { it.date == date.toString() }.sortedBy { it.time }
                    ComplementaryFoodStatisticsDay(
                        date,
                        dayMeals,
                        dayMeals.filter { it.unit == ComplementaryFoodUnit.G }.sumOf { it.amount },
                        dayMeals.filter { it.unit == ComplementaryFoodUnit.ML }.sumOf { it.amount },
                    )
                }.toList()
        val introductions = linkedMapOf<String, Pair<String, LocalDate>>()
        val counts = linkedMapOf<String, Int>()
        selected.sortedWith(compareBy<ComplementaryFoodMealEntity> { it.date }.thenBy { it.time }).forEach { meal ->
            meal.ingredients.forEach { raw ->
                val name = normalizeIngredient(raw)
                val key = name.lowercase()
                if (name.isNotEmpty()) {
                    counts[key] = counts.getOrDefault(key, 0) + 1
                    introductions.putIfAbsent(key, name to LocalDate.parse(meal.date))
                }
            }
        }
        val frequencies =
            counts
                .map { (key, count) ->
                    val introduction = introductions.getValue(key)
                    IngredientFrequency(introduction.first, count, introduction.second)
                }.sortedWith(compareByDescending<IngredientFrequency> { it.count }.thenBy { it.name.lowercase() })
        val grams = selected.filter { it.unit == ComplementaryFoodUnit.G }
        val milliliters = selected.filter { it.unit == ComplementaryFoodUnit.ML }
        return ComplementaryFoodStatistics(
            mealCount = selected.size,
            recordedDays = selected.map { it.date }.distinct().size,
            totalG = grams.sumOf { it.amount },
            totalMl = milliliters.sumOf { it.amount },
            averageGPerMeal = if (grams.isEmpty()) 0.0 else grams.sumOf { it.amount }.toDouble() / grams.size,
            averageMlPerMeal = if (milliliters.isEmpty()) 0.0 else milliliters.sumOf { it.amount }.toDouble() / milliliters.size,
            ingredientFrequencies = frequencies,
            differentIngredientCount = frequencies.size,
            recentlyIntroduced = frequencies.sortedByDescending { it.firstRecordedDate },
            days = days,
        )
    }
}
