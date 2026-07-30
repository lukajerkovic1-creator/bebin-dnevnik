package hr.bebindnevnik.app.domain

import hr.bebindnevnik.app.data.ComplementaryFoodDaySummary
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

enum class ComplementaryFoodWarning { ZERO, OVER_500, OVER_30_TEASPOONS, POSSIBLE_DUPLICATE }

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
    val totalTeaspoons: Int,
)

data class ComplementaryFoodStatistics(
    val mealCount: Int,
    val recordedDays: Int,
    val totalG: Int,
    val totalMl: Int,
    val totalTeaspoons: Int,
    val averageGPerMeal: Double,
    val averageMlPerMeal: Double,
    val averageTeaspoonsPerMeal: Double,
    val teaspoonMealCount: Int,
    val ingredientFrequencies: List<IngredientFrequency>,
    val differentIngredientCount: Int,
    val recentlyIntroduced: List<IngredientFrequency>,
    val days: List<ComplementaryFoodStatisticsDay>,
)

object ComplementaryFoodLogic {
    private val hrLocale = Locale.forLanguageTag("hr")
    private val repeatedWhitespace = Regex("\\s+")
    private val ingredientSeparator =
        Regex("""(?i)(?<![\p{L}\p{N}_])i(?![\p{L}\p{N}_])|[+,]""")
    private val edgeAsterisks = Regex("""^\*+|\*+$""")

    fun normalizeIngredients(values: List<String>): List<String> =
        values
            .flatMap { value -> ingredientSeparator.split(value) }
            .mapNotNull(::normalizeToken)
            .distinctBy { it.lowercase(hrLocale) }

    fun normalizeMeal(meal: ComplementaryFoodMealEntity): ComplementaryFoodMealEntity = meal.copy(ingredients = normalizeIngredients(meal.ingredients))

    fun unitLabel(unit: ComplementaryFoodUnit): String =
        when (unit) {
            ComplementaryFoodUnit.G -> "g"
            ComplementaryFoodUnit.ML -> "ml"
            ComplementaryFoodUnit.TEASPOON -> "žličica"
        }

    fun formatQuantity(
        amount: Int,
        unit: ComplementaryFoodUnit,
    ): String =
        when (unit) {
            ComplementaryFoodUnit.G -> "$amount g"
            ComplementaryFoodUnit.ML -> "$amount ml"
            ComplementaryFoodUnit.TEASPOON -> "$amount ${teaspoonNoun(amount)}"
        }

    fun validate(
        ingredients: List<String>,
        amount: Int?,
        unit: ComplementaryFoodUnit,
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
        val canonical = normalized.map { it.lowercase(hrLocale) }.sorted()
        val duplicate =
            existing.any {
                it.id != editedId && it.date == date.toString() && LocalTime.parse(it.time) == time.withNano(0) &&
                    normalizeIngredients(it.ingredients).map { name -> name.lowercase(hrLocale) }.sorted() == canonical
            }
        return ComplementaryFoodValidation(
            valid = true,
            warnings =
                buildSet {
                    if (amount == 0) add(ComplementaryFoodWarning.ZERO)
                    if (unit != ComplementaryFoodUnit.TEASPOON && (amount ?: 0) > 500) {
                        add(ComplementaryFoodWarning.OVER_500)
                    }
                    if (unit == ComplementaryFoodUnit.TEASPOON && (amount ?: 0) > 30) {
                        add(ComplementaryFoodWarning.OVER_30_TEASPOONS)
                    }
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
            totalTeaspoons = selected.filter { it.unit == ComplementaryFoodUnit.TEASPOON }.sumOf { it.amount },
            lastMeal = selected.maxWithOrNull(compareBy<ComplementaryFoodMealEntity> { it.time }.thenBy { it.id }),
        )
    }

    fun suggestions(meals: List<ComplementaryFoodMealEntity>): List<String> =
        meals
            .sortedByDescending { it.updatedAt }
            .flatMap { it.ingredients }
            .let { normalizeIngredients(it) }
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
                        dayMeals.filter { it.unit == ComplementaryFoodUnit.TEASPOON }.sumOf { it.amount },
                    )
                }.toList()
        val introductions = linkedMapOf<String, Pair<String, LocalDate>>()
        val counts = linkedMapOf<String, Int>()
        selected.sortedWith(compareBy<ComplementaryFoodMealEntity> { it.date }.thenBy { it.time }).forEach { meal ->
            normalizeIngredients(meal.ingredients).forEach { name ->
                val key = name.lowercase(hrLocale)
                counts[key] = counts.getOrDefault(key, 0) + 1
                introductions.putIfAbsent(key, name to LocalDate.parse(meal.date))
            }
        }
        val frequencies =
            counts
                .map { (key, count) ->
                    val introduction = introductions.getValue(key)
                    IngredientFrequency(introduction.first, count, introduction.second)
                }.sortedWith(compareByDescending<IngredientFrequency> { it.count }.thenBy { it.name.lowercase(hrLocale) })
        val grams = selected.filter { it.unit == ComplementaryFoodUnit.G }
        val milliliters = selected.filter { it.unit == ComplementaryFoodUnit.ML }
        val teaspoons = selected.filter { it.unit == ComplementaryFoodUnit.TEASPOON }
        return ComplementaryFoodStatistics(
            mealCount = selected.size,
            recordedDays = selected.map { it.date }.distinct().size,
            totalG = grams.sumOf { it.amount },
            totalMl = milliliters.sumOf { it.amount },
            totalTeaspoons = teaspoons.sumOf { it.amount },
            averageGPerMeal = if (grams.isEmpty()) 0.0 else grams.sumOf { it.amount }.toDouble() / grams.size,
            averageMlPerMeal = if (milliliters.isEmpty()) 0.0 else milliliters.sumOf { it.amount }.toDouble() / milliliters.size,
            averageTeaspoonsPerMeal =
                if (teaspoons.isEmpty()) {
                    0.0
                } else {
                    teaspoons.sumOf { it.amount }.toDouble() / teaspoons.size
                },
            teaspoonMealCount = teaspoons.size,
            ingredientFrequencies = frequencies,
            differentIngredientCount = frequencies.size,
            recentlyIntroduced = frequencies.sortedByDescending { it.firstRecordedDate },
            days = days,
        )
    }

    private fun normalizeToken(raw: String): String? {
        val collapsed =
            raw
                .trim()
                .replace(edgeAsterisks, "")
                .trim()
                .replace(repeatedWhitespace, " ")
        if (collapsed.isEmpty()) return null
        return collapsed.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(hrLocale) else character.toString()
        }
    }

    private fun teaspoonNoun(amount: Int): String {
        val absolute = kotlin.math.abs(amount)
        val lastTwo = absolute % 100
        val last = absolute % 10
        return when {
            lastTwo in 11..14 -> "žličica"
            last == 1 -> "žličica"
            last in 2..4 -> "žličice"
            else -> "žličica"
        }
    }
}
