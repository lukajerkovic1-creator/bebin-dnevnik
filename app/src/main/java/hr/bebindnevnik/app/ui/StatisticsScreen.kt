@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("TooManyFunctions")

package hr.bebindnevnik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.domain.HabitStatistics
import hr.bebindnevnik.app.domain.StatisticsDay
import hr.bebindnevnik.app.domain.StatisticsPeriod
import hr.bebindnevnik.app.domain.StatisticsReport
import hr.bebindnevnik.app.domain.StatisticsSelection
import java.time.LocalDate
import kotlin.math.roundToInt

private data class StatisticsChartPoint(
    val date: LocalDate,
    val value: Float?,
    val detail: String,
)

@Composable
internal fun EnhancedStatisticsScreen(
    report: StatisticsReport,
    selection: StatisticsSelection,
    onSelectionChange: (StatisticsSelection) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("statistics-screen"),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StatisticsHeader(
                selection = selection,
                report = report,
                onSelectionChange = onSelectionChange,
                onCustomRange = { showRangePicker = true },
            )
        }
        item { PeriodOverviewCard(report) }
        item { FeedingSummaryCard(report) }
        item { ComplementaryFoodSummaryCard(report) }
        item { TummySummaryCard(report) }
        item { StoolSummaryCard(report) }
        item { HabitsSummaryCard(report) }
        item {
            StatisticsSectionTitle("Hranjenje", "Količina, broj obroka i vrijeme hranjenja", BabyIllustrationKind.BOTTLE)
        }
        if (report.feeding.mealCount == 0) {
            item {
                StatisticsEmptyState(
                    "Još nema dovoljno evidentiranih obroka za prikaz grafikona.",
                    BabyIllustrationKind.BOTTLE,
                    onOpenDay,
                )
            }
        } else {
            item { FeedingChart(report.days, onOpenDay) }
            item { MealTimeDistribution(report) }
        }
        item {
            StatisticsSectionTitle("Dohrana", "Obroci, količine i evidentirane namirnice", BabyIllustrationKind.FOOD)
        }
        if (report.complementaryFood.mealCount == 0) {
            item {
                StatisticsEmptyState(
                    "Dohrana još nije evidentirana u odabranom razdoblju.",
                    BabyIllustrationKind.FOOD,
                    onOpenDay,
                )
            }
        } else {
            item { ComplementaryFoodChart(report, onOpenDay) }
            item { ComplementaryFoodIngredientsCard(report) }
        }
        item {
            StatisticsSectionTitle("Tummy time", "Trajanje i broj evidentiranih sesija", BabyIllustrationKind.TUMMY)
        }
        if (report.tummy.recordedDays == 0) {
            item {
                StatisticsEmptyState(
                    "Tummy time još nije evidentiran u odabranom razdoblju.",
                    BabyIllustrationKind.TUMMY,
                    onOpenDay,
                )
            }
        } else {
            item { TummyChart(report.days, onOpenDay) }
            item { TummyHeatmap(report.days, onOpenDay) }
            item { MostActiveDayCard(report, onOpenDay) }
        }
        item {
            StatisticsSectionTitle("Stolica", "Nula i neevidentirani dan prikazani su odvojeno", BabyIllustrationKind.STOOL)
        }
        if (report.stool.recordedDays == 0) {
            item {
                StatisticsEmptyState(
                    "Nema evidentiranih podataka o stolici.",
                    BabyIllustrationKind.STOOL,
                    onOpenDay,
                )
            }
        } else {
            item { StoolChart(report.days, onOpenDay) }
        }
        item {
            StatisticsSectionTitle("Dnevne navike", "Waya kapi i vježbanje", BabyIllustrationKind.DROPS)
        }
        item { HabitDetailCard("Waya kapi", report.waya, report.days, true) }
        item { HabitDetailCard("Vježbanje", report.exercise, report.days, false) }
        item { CompletenessCard(report) }
    }
    if (showRangePicker) {
        StatisticsDateRangeDialog(
            selection = selection,
            onDismiss = { showRangePicker = false },
            onConfirm = {
                showRangePicker = false
                onSelectionChange(it)
            },
        )
    }
}

@Composable
private fun StatisticsHeader(
    selection: StatisticsSelection,
    report: StatisticsReport,
    onSelectionChange: (StatisticsSelection) -> Unit,
    onCustomRange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Statistika", style = MaterialTheme.typography.headlineSmall)
                Text("Pregled malih, važnih trenutaka", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BabyIllustration(BabyIllustrationKind.JOURNAL, Modifier.size(BabyDimensions.IllustrationSmall))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(StatisticsPeriod.SEVEN_DAYS, StatisticsPeriod.THIRTY_DAYS, StatisticsPeriod.NINETY_DAYS).forEach { period ->
                    StatisticsPeriodChip(period, selection, onSelectionChange, onCustomRange, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(StatisticsPeriod.ALL, StatisticsPeriod.CUSTOM).forEach { period ->
                    StatisticsPeriodChip(period, selection, onSelectionChange, onCustomRange, Modifier.weight(1f))
                }
            }
        }
        Text(
            "${report.start.hrDate()} – ${report.end.hrDate()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("statistics-date-range"),
        )
    }
}

@Composable
private fun StatisticsPeriodChip(
    period: StatisticsPeriod,
    selection: StatisticsSelection,
    onSelectionChange: (StatisticsSelection) -> Unit,
    onCustomRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selection.period == period,
        onClick = {
            if (period == StatisticsPeriod.CUSTOM) onCustomRange() else onSelectionChange(StatisticsSelection(period))
        },
        label = { Text(period.label(), maxLines = 2) },
        leadingIcon =
            if (period == StatisticsPeriod.CUSTOM) {
                { Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) }
            } else {
                null
            },
        modifier = modifier.heightIn(min = BabyDimensions.TouchTarget).testTag("statistics-period-${period.name.lowercase()}"),
    )
}

@Composable
private fun PeriodOverviewCard(report: StatisticsReport) {
    val feeding = report.feeding
    val tummy = report.tummy
    val stool = report.stool
    Card(
        Modifier.fillMaxWidth().testTag("statistics-overview"),
        shape = RoundedCornerShape(BabyDimensions.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .64f)),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = .45f))))) {
            Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pregled razdoblja", style = MaterialTheme.typography.titleLarge)
                        Text("${report.dayCount} dana", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BabyIllustration(BabyIllustrationKind.JOURNAL, Modifier.size(62.dp))
                }
                OverviewMetric("Ukupno popijeno", "${feeding.totalMl} ml", report.comparison.totalMlPercent)
                OverviewMetric("Prosječno dnevno · svi dani", "${feeding.averagePerAllDays.hrDecimal()} ml", null)
                OverviewMetric("Prosječno dnevno · dani s obrokom", "${feeding.averagePerRecordedDay.hrDecimal()} ml", null)
                OverviewMetric("Prosječno po obroku", "${feeding.averagePerMeal.hrDecimal()} ml", null)
                OverviewMetric("Prosječan broj obroka dnevno", (feeding.mealCount.toDouble() / report.dayCount).hrDecimal(), report.comparison.mealCountPercent)
                OverviewMetric("Ukupni tummy time", tummy.totalSeconds.statisticsDuration(), report.comparison.tummySecondsPercent)
                OverviewMetric("Prosječno stolica · evidentirani dani", stool.averagePerRecordedDay.hrDecimal(), report.comparison.stoolCountPercent)
                OverviewMetric("Potpuno evidentirani dani", "${report.completeness.completePercent}%", report.comparison.completeDaysPercent)
                if (
                    listOf(
                        report.comparison.totalMlPercent,
                        report.comparison.mealCountPercent,
                        report.comparison.tummySecondsPercent,
                        report.comparison.stoolCountPercent,
                        report.comparison.completeDaysPercent,
                    ).all { it == null }
                ) {
                    Text("Prethodno razdoblje nema dovoljno podataka za usporedbu.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Usporedba je s neposredno prethodnim jednako dugim razdobljem.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    comparison: Int?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        comparison?.let {
            Text(it.trendLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FeedingSummaryCard(report: StatisticsReport) =
    StatisticsSummaryCard("Hranjenje", BabyIllustrationKind.BOTTLE) {
        val feeding = report.feeding
        SummaryLine("Ukupno", "${feeding.totalMl} ml")
        SummaryLine("Broj obroka", feeding.mealCount.toString())
        SummaryLine("Prosjek po obroku", "${feeding.averagePerMeal.hrDecimal()} ml")
        SummaryLine("Najveći obrok", feeding.largestMealMl?.let { "$it ml" } ?: "Nije evidentirano")
        SummaryLine(
            "Dan s najvećim unosom",
            feeding.highestDay?.let { "${it.hrDate()} · ${feeding.highestDayMl} ml" } ?: "Nije evidentirano",
        )
    }

@Composable
private fun ComplementaryFoodSummaryCard(report: StatisticsReport) =
    StatisticsSummaryCard("Dohrana", BabyIllustrationKind.FOOD) {
        val food = report.complementaryFood
        SummaryLine("Broj obroka", food.mealCount.toString())
        SummaryLine("Dani s barem jednim obrokom", food.recordedDays.toString())
        SummaryLine("Ukupno u gramima", "${food.totalG} g")
        SummaryLine("Ukupno u mililitrima", "${food.totalMl} ml")
        SummaryLine("Prosjek obroka u gramima", "${food.averageGPerMeal.hrDecimal()} g")
        SummaryLine("Prosjek obroka u mililitrima", "${food.averageMlPerMeal.hrDecimal()} ml")
    }

@Composable
private fun TummySummaryCard(report: StatisticsReport) =
    StatisticsSummaryCard("Tummy time", BabyIllustrationKind.TUMMY) {
        val tummy = report.tummy
        SummaryLine("Ukupno vrijeme", tummy.totalSeconds.statisticsDuration())
        SummaryLine(
            "Prosjek po evidentiranom danu",
            tummy.averagePerRecordedDaySeconds
                .roundToInt()
                .toLong()
                .statisticsDuration(),
        )
        SummaryLine("Broj sesija", tummy.sessionCount.toString())
        SummaryLine(
            "Prosječno trajanje sesije",
            tummy.averageSessionSeconds
                .roundToInt()
                .toLong()
                .statisticsDuration(),
        )
        SummaryLine("Najdulja sesija", tummy.longestSessionSeconds?.statisticsDuration() ?: "Nije evidentirano")
    }

@Composable
private fun StoolSummaryCard(report: StatisticsReport) =
    StatisticsSummaryCard("Stolica", BabyIllustrationKind.STOOL) {
        val stool = report.stool
        SummaryLine("Ukupno", stool.total.toString())
        SummaryLine("Prosjek po evidentiranom danu", stool.averagePerRecordedDay.hrDecimal())
        SummaryLine("Dani s 0", stool.zeroDays.toString())
        SummaryLine("Dani s barem jednom", stool.positiveDays.toString())
        SummaryLine("Neevidentirani dani", stool.missingDays.toString())
    }

@Composable
private fun HabitsSummaryCard(report: StatisticsReport) =
    StatisticsSummaryCard("Dnevne navike", BabyIllustrationKind.EXERCISE) {
        SummaryLine("Waya kapi · Da / Ne / ?", "${report.waya.yes} / ${report.waya.no} / ${report.waya.missing}")
        SummaryLine("Vježbanje · Da / Ne / ?", "${report.exercise.yes} / ${report.exercise.no} / ${report.exercise.missing}")
        SummaryLine("Potpuni dani", report.completeness.completeDays.toString())
        SummaryLine("Djelomični dani", report.completeness.partialDays.toString())
        SummaryLine("Dani bez podataka", report.completeness.emptyDays.toString())
    }

@Composable
private fun StatisticsSummaryCard(
    title: String,
    illustration: BabyIllustrationKind,
    content: @Composable () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                BabyIllustration(illustration, Modifier.size(54.dp))
            }
            content()
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatisticsSectionTitle(
    title: String,
    subtitle: String,
    illustration: BabyIllustrationKind,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        BabyIllustration(illustration, Modifier.size(58.dp))
    }
}

@Composable
private fun FeedingChart(
    days: List<StatisticsDay>,
    onOpenDay: (LocalDate) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(3) { index ->
                val labels = listOf("Ukupno ml", "Broj obroka", "Prosjek po obroku")
                FilterChip(mode == index, { mode = index }, { Text(labels[index], maxLines = 1) })
            }
        }
        val points =
            days.map { day ->
                val value =
                    if (!day.feedingRecorded) {
                        null
                    } else {
                        when (mode) {
                            0 -> day.summary.totalMl.toFloat()
                            1 -> day.summary.mealCount.toFloat()
                            else -> day.summary.averageMl.toFloat()
                        }
                    }
                StatisticsChartPoint(
                    day.date,
                    value,
                    "${day.date.hrDate()} · ${day.summary.totalMl} ml · ${day.summary.mealCount} obroka · " +
                        "prosjek ${day.summary.averageMl.hrDecimal()} ml · najmanji ${day.smallestMealMl ?: 0} ml · najveći ${day.largestMealMl ?: 0} ml",
                )
            }
        InteractiveBarChart("Hranjenje po danu", points, "Siva crta znači da obrok nije evidentiran.", onOpenDay, "feeding-chart")
    }
}

@Composable
private fun ComplementaryFoodChart(
    report: StatisticsReport,
    onOpenDay: (LocalDate) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(3) { index ->
                val labels = listOf("Broj obroka", "Ukupno g", "Ukupno ml")
                FilterChip(mode == index, { mode = index }, { Text(labels[index], maxLines = 1) })
            }
        }
        val points =
            report.complementaryFood.days.map { day ->
                val value =
                    if (day.meals.isEmpty()) {
                        null
                    } else {
                        when (mode) {
                            0 -> day.meals.size.toFloat()
                            1 -> day.totalG.toFloat()
                            else -> day.totalMl.toFloat()
                        }
                    }
                val mealDetails =
                    day.meals.joinToString("; ") {
                        "${it.time.hrStoredTime()} ${it.ingredients.joinToString(" + ")} ${it.amount} ${it.unit.name.lowercase()}"
                    }
                StatisticsChartPoint(
                    day.date,
                    value,
                    "${day.date.hrDate()} · ${day.meals.size} obroka · ${day.totalG} g · ${day.totalMl} ml · $mealDetails",
                )
            }
        InteractiveBarChart(
            "Dohrana po danu",
            points,
            "Neevidentirani dan nije obrok od 0 g.",
            onOpenDay,
            "complementary-food-chart",
        )
    }
}

@Composable
private fun ComplementaryFoodIngredientsCard(report: StatisticsReport) {
    val food = report.complementaryFood
    Card(Modifier.fillMaxWidth().testTag("complementary-food-ingredients")) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Namirnice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SummaryLine("Različitih namirnica", food.differentIngredientCount.toString())
            Text("Najčešće evidentirane", style = MaterialTheme.typography.labelLarge)
            food.ingredientFrequencies.take(8).forEach { item -> SummaryLine(item.name, "${item.count} puta") }
            Text("Posljednje uvedene", style = MaterialTheme.typography.labelLarge)
            food.recentlyIntroduced.take(8).forEach { item -> SummaryLine(item.name, item.firstRecordedDate.hrDate()) }
            Text(
                "Prikaz je evidencijski i ne procjenjuje kvalitetu prehrane ni dovoljnost unosa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TummyChart(
    days: List<StatisticsDay>,
    onOpenDay: (LocalDate) -> Unit,
) {
    val points =
        days.map { day ->
            StatisticsChartPoint(
                date = day.date,
                value = if (day.tummyRecorded) day.summary.tummySeconds / 60f else null,
                detail =
                    "${day.date.hrDate()} · ${day.summary.tummySeconds.statisticsDuration()} · ${day.summary.tummyCount} sesija · " +
                        "prosjek ${if (day.summary.tummyCount == 0) "0 min" else (day.summary.tummySeconds / day.summary.tummyCount).statisticsDuration()} · " +
                        "najkraća ${day.shortestTummySeconds?.statisticsDuration() ?: "nema sesije"} · " +
                        "najdulja ${day.longestTummySeconds?.statisticsDuration() ?: "nema sesije"}",
            )
        }
    InteractiveBarChart("Tummy time po danu · minute", points, "Broj sesija prikazan je u detalju odabranog dana.", onOpenDay, "tummy-chart")
}

@Composable
private fun StoolChart(
    days: List<StatisticsDay>,
    onOpenDay: (LocalDate) -> Unit,
) {
    val points =
        days.map { day ->
            StatisticsChartPoint(
                day.date,
                day.summary.stoolCount?.toFloat(),
                "${day.date.hrDate()} · ${day.summary.stoolCount?.let { "$it stolica" } ?: "nije evidentirano"}",
            )
        }
    InteractiveBarChart("Broj stolica po danu", points, "Točka označava evidentiranu nulu; siva crta znači bez podatka.", onOpenDay, "stool-chart")
}

@Composable
private fun InteractiveBarChart(
    title: String,
    points: List<StatisticsChartPoint>,
    summary: String,
    onOpenDay: (LocalDate) -> Unit,
    tag: String,
) {
    var selected by rememberSaveable(title, points.size) { mutableIntStateOf(-1) }
    val primary = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val missingColor = MaterialTheme.colorScheme.outline
    val chartWidth = (points.size * 28).coerceAtLeast(320).dp
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (selected >= 0) {
                    IconButton({ selected = -1 }) { Icon(Icons.Default.Close, contentDescription = "Poništi odabir") }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(
                    Modifier
                        .width(chartWidth)
                        .height(170.dp)
                        .testTag(tag)
                        .semantics {
                            contentDescription = "$title. $summary " + points.joinToString("; ") { it.detail }
                            role = Role.Image
                        }.pointerInput(points) {
                            detectTapGestures { offset ->
                                selected = ((offset.x / size.width) * points.size).toInt().coerceIn(points.indices)
                            }
                        },
                ) {
                    val max = points.mapNotNull { it.value }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                    val step = size.width / points.size.coerceAtLeast(1)
                    val baseline = size.height - 22.dp.toPx()
                    drawLine(missingColor.copy(alpha = .45f), Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                    points.forEachIndexed { index, point ->
                        val center = index * step + step / 2
                        val color = if (selected == index) selectedColor else primary
                        when {
                            point.value == null -> {
                                drawLine(missingColor, Offset(center - 5.dp.toPx(), baseline), Offset(center + 5.dp.toPx(), baseline), 3.dp.toPx())
                            }

                            point.value == 0f -> {
                                drawCircle(color, 3.dp.toPx(), Offset(center, baseline - 2.dp.toPx()))
                            }

                            else -> {
                                val height = (baseline - 8.dp.toPx()) * point.value / max
                                drawRoundRect(
                                    color,
                                    Offset(index * step + 3.dp.toPx(), baseline - height),
                                    Size((step - 6.dp.toPx()).coerceAtLeast(3.dp.toPx()), height),
                                    CornerRadius(6.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
            Text(summary, style = MaterialTheme.typography.bodySmall)
            if (selected in points.indices) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(points[selected].detail, Modifier.weight(1f))
                        IconButton(
                            onClick = { onOpenDay(points[selected].date) },
                            modifier = Modifier.testTag("$tag-open-day"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Otvori ${points[selected].date.hrDate()}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealTimeDistribution(report: StatisticsReport) {
    val labels = listOf("00–06 h", "06–12 h", "12–18 h", "18–24 h")
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Raspodjela obroka tijekom dana", style = MaterialTheme.typography.titleMedium)
            labels.forEachIndexed { index, label ->
                val count = report.feeding.timeBuckets[index]
                val fraction = if (report.feeding.mealCount == 0) 0f else count.toFloat() / report.feeding.mealCount
                Text("$label · $count obroka")
                Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(fraction).height(12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@Composable
private fun TummyHeatmap(
    days: List<StatisticsDay>,
    onOpenDay: (LocalDate) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Niz evidentiranih dana", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(days.size) { index ->
                    val day = days[index]
                    val color =
                        when {
                            !day.tummyRecorded -> MaterialTheme.colorScheme.surfaceVariant
                            day.summary.tummySeconds == 0L -> MaterialTheme.colorScheme.outlineVariant
                            day.summary.tummySeconds < 600 -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.primary
                        }
                    TextButton(
                        onClick = { onOpenDay(day.date) },
                        modifier = Modifier.size(BabyDimensions.TouchTarget).background(color, RoundedCornerShape(14.dp)),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(day.date.dayOfMonth.toString(), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            Text("Legenda: prazno = bez podatka · obrub = evidentirano 0 min · svijetlo = kraće · fuksija = dulje trajanje", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MostActiveDayCard(
    report: StatisticsReport,
    onOpenDay: (LocalDate) -> Unit,
) {
    report.tummy.mostActiveDay?.let { date ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(BabyDimensions.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Najaktivniji dan", style = MaterialTheme.typography.titleMedium)
                    Text("${date.hrDate()} · ${report.tummy.mostActiveDaySeconds.statisticsDuration()} · ${report.tummy.mostActiveDaySessions} sesija")
                }
                IconButton({ onOpenDay(date) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Otvori najaktivniji dan") }
            }
        }
    }
}

@Composable
private fun HabitDetailCard(
    title: String,
    statistics: HabitStatistics,
    days: List<StatisticsDay>,
    waya: Boolean,
) {
    val total = (statistics.yes + statistics.no + statistics.missing).coerceAtLeast(1)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = "$title: Da ${statistics.yes}, Ne ${statistics.no}, nije evidentirano ${statistics.missing}" }) {
                HabitSegment(statistics.yes.toFloat() / total, MaterialTheme.colorScheme.primary)
                HabitSegment(statistics.no.toFloat() / total, MaterialTheme.colorScheme.tertiary)
                HabitSegment(statistics.missing.toFloat() / total, MaterialTheme.colorScheme.surfaceVariant)
            }
            Text("✓ Da: ${statistics.yes} dana")
            Text("— Ne: ${statistics.no} dana")
            Text("? Nije evidentirano: ${statistics.missing} dana")
            if (days.size <= 31) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(days.size) { index ->
                        val status = if (waya) days[index].summary.waya else days[index].summary.exercise
                        val symbol =
                            when (status) {
                                TernaryStatus.DA -> "✓"
                                TernaryStatus.NE -> "—"
                                TernaryStatus.NIJE_EVIDENTIRANO -> "?"
                            }
                        Text(
                            symbol,
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                                    .semantics { contentDescription = "${days[index].date.hrDate()}: ${status.label()}" },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HabitSegment(
    fraction: Float,
    color: Color,
) {
    if (fraction > 0f) Spacer(Modifier.weight(fraction).height(18.dp).background(color))
}

@Composable
private fun CompletenessCard(report: StatisticsReport) {
    val completeness = report.completeness
    OutlinedCard(Modifier.fillMaxWidth().testTag("statistics-completeness")) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Potpunost dnevnika", style = MaterialTheme.typography.titleLarge)
                    Text("${completeness.completePercent}% potpunih dana", color = MaterialTheme.colorScheme.primary)
                }
                BabyIllustration(BabyIllustrationKind.JOURNAL, Modifier.size(58.dp))
            }
            Text("✓ Potpuno evidentirani: ${completeness.completeDays}")
            Text("◐ Djelomično evidentirani: ${completeness.partialDays}")
            Text("— Bez podataka: ${completeness.emptyDays}")
            Text("Stavke koje nedostaju", fontWeight = FontWeight.SemiBold)
            completeness.missingCounts.forEach { (label, count) -> SummaryLine(label.replaceFirstChar(Char::uppercase), "$count dana") }
            completeness.mostOftenMissing?.let { (label, count) ->
                Text("Najčešće nedostaje: $label — $count dana.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatisticsEmptyState(
    message: String,
    illustration: BabyIllustrationKind,
    onOpenDay: (LocalDate) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(BabyDimensions.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BabyIllustration(illustration, Modifier.size(BabyDimensions.IllustrationMedium))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button({ onOpenDay(LocalDate.now()) }, Modifier.heightIn(min = BabyDimensions.TouchTarget)) { Text("Otvori današnji dan") }
        }
    }
}

@Composable
private fun StatisticsDateRangeDialog(
    selection: StatisticsSelection,
    onDismiss: () -> Unit,
    onConfirm: (StatisticsSelection) -> Unit,
) {
    val today = LocalDate.now()
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = selection.customStart?.toUtcMillis(),
            initialSelectedEndDateMillis = selection.customEnd?.toUtcMillis(),
            selectableDates = PastOrTodaySelectableDates(today),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis?.toUtcDate() ?: return@TextButton
                    val end = state.selectedEndDateMillis?.toUtcDate() ?: start
                    onConfirm(StatisticsSelection(StatisticsPeriod.CUSTOM, start, end))
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text("Prikaži") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Odustani") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Prilagođeno razdoblje", Modifier.padding(24.dp, 16.dp)) },
            headline = {
                Text(
                    listOfNotNull(state.selectedStartDateMillis?.toUtcDate()?.hrDate(), state.selectedEndDateMillis?.toUtcDate()?.hrDate()).joinToString(" – "),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            showModeToggle = false,
            modifier = Modifier.heightIn(max = 620.dp).testTag("statistics-date-range-picker"),
        )
    }
}

private fun StatisticsPeriod.label(): String =
    when (this) {
        StatisticsPeriod.SEVEN_DAYS -> "7 dana"
        StatisticsPeriod.THIRTY_DAYS -> "30 dana"
        StatisticsPeriod.NINETY_DAYS -> "90 dana"
        StatisticsPeriod.ALL -> "Cijelo razdoblje"
        StatisticsPeriod.CUSTOM -> "Prilagođeno"
    }

private fun Int.trendLabel(): String =
    when {
        this > 0 -> "+$this %"
        this < 0 -> "−${-this} %"
        else -> "Bez promjene"
    }

private fun Double.hrDecimal(): String = String.format(java.util.Locale.forLanguageTag("hr-HR"), "%.1f", if (isFinite()) coerceAtLeast(0.0) else 0.0)

private fun Long.statisticsDuration(): String {
    val safe = coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val seconds = safe % 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}
