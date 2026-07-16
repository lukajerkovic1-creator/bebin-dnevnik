package hr.bebindnevnik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.domain.growth.GrowthAssessment
import hr.bebindnevnik.app.domain.growth.GrowthIndicator
import hr.bebindnevnik.app.domain.growth.GrowthMetricResult
import hr.bebindnevnik.app.domain.growth.GrowthReferenceLine
import hr.bebindnevnik.app.domain.growth.GrowthReferenceSystem
import hr.bebindnevnik.app.domain.growth.ageDaysFor
import hr.bebindnevnik.app.domain.growth.birthMeasurement
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

private data class ChartRange(
    val label: String,
    val days: Int,
)

private val chartRanges =
    listOf(
        ChartRange("0–6 mj", 183),
        ChartRange("0–12 mj", 365),
        ChartRange("0–24 mj", 730),
        ChartRange("0–5 god", 1826),
    )

private data class ChildChartPoint(
    val x: Double,
    val y: Double,
    val measurement: GrowthMeasurementEntity,
    val assessment: GrowthAssessment,
    val metric: GrowthMetricResult,
)

@Composable
internal fun GrowthChartsSection(
    profile: ChildProfileEntity,
    measurements: List<GrowthMeasurementEntity>,
    viewModel: MainViewModel,
) {
    var indicator by rememberSaveable { mutableStateOf(GrowthIndicator.WEIGHT_FOR_AGE) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(2) }
    var correctedWho by rememberSaveable { mutableStateOf(true) }
    var useLengthTable by rememberSaveable { mutableStateOf(true) }
    val range = chartRanges[rangeIndex]
    val preterm = profile.gestationalWeeks < 37
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Grafikoni rasta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GrowthIndicator.entries.forEach { option ->
                    FilterChip(selected = indicator == option, onClick = { indicator = option }, label = { Text(option.label()) })
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chartRanges.forEachIndexed { index, option ->
                    FilterChip(selected = rangeIndex == index, onClick = { rangeIndex = index }, label = { Text(option.label) })
                }
            }
        }
        if (preterm) {
            item {
                Column {
                    Text("WHO dio grafikona", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = correctedWho, onClick = { correctedWho = true }, label = { Text("Korigirana dob") })
                        FilterChip(selected = !correctedWho, onClick = { correctedWho = false }, label = { Text("Kronološka dob") })
                    }
                }
            }
        }
        if (indicator == GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = useLengthTable, onClick = { useLengthTable = true }, label = { Text("Težina prema duljini") })
                    FilterChip(selected = !useLengthTable, onClick = { useLengthTable = false }, label = { Text("Težina prema visini") })
                }
            }
        }
        item {
            val all = listOfNotNull(profile.birthMeasurement()) + measurements
            val points = childPoints(profile, all, indicator, range.days, correctedWho, useLengthTable, viewModel)
            val referenceLines =
                if (indicator == GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT) {
                    viewModel.growthMeasureReferenceLines(profile, useLengthTable)
                } else {
                    viewModel.growthAgeReferenceLines(profile, indicator, range.days, correctedWho).let { lines ->
                        if (preterm && correctedWho) {
                            lines.map { line -> line.copy(points = line.points.map { it.copy(x = it.x + (280 - profile.gestationalWeeks * 7 - profile.gestationalDays)) }) }
                        } else {
                            lines
                        }
                    }
                }
            val transition =
                if (preterm && indicator != GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT) {
                    (350 - profile.gestationalWeeks * 7 - profile.gestationalDays).toDouble().takeIf { it in 0.0..range.days.toDouble() }
                } else {
                    null
                }
            GrowthChart(
                indicator = indicator,
                lines = referenceLines,
                points = points,
                xMaxForAge = range.days.toDouble().takeIf { indicator != GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT },
                transitionX = transition,
            )
        }
        item {
            Text(
                "Referentne linije: 3., 15., 50., 85. i 97. percentil. Točke su zasebna mjerenja povezana kronološkim redom. Zumirajte s dva prsta i povucite graf vodoravno.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (preterm) {
            item {
                Text("Fenton i WHO percentili nisu prikazani kao zamjenjivi. Okomita oznaka pokazuje prijelaz na WHO; Fenton krivulje nisu ugrađene bez licence.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun childPoints(
    profile: ChildProfileEntity,
    measurements: List<GrowthMeasurementEntity>,
    indicator: GrowthIndicator,
    maxDays: Int,
    correctedWho: Boolean,
    useLengthTable: Boolean,
    viewModel: MainViewModel,
): List<ChildChartPoint> =
    measurements
        .mapNotNull { item ->
            val assessment = viewModel.assessGrowth(profile, item, correctedWho)
            val metric = assessment.metric(indicator) ?: return@mapNotNull null
            if (assessment.ages.chronologicalDays !in 0..maxDays.toLong()) return@mapNotNull null
            if (indicator == GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT && assessment.referenceSystem != GrowthReferenceSystem.WHO_2006) return@mapNotNull null
            val x =
                if (indicator == GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT) {
                    val isLength = assessment.ageBasis.let { ageDaysFor(assessment.ages, it) < 731 }
                    if (isLength != useLengthTable) return@mapNotNull null
                    metric.calculationValue.let { item.lengthHeightCm ?: return@mapNotNull null } + metric.lengthCorrectionCm
                } else {
                    assessment.ages.chronologicalDays.toDouble()
                }
            ChildChartPoint(x, metric.calculationValue, item, assessment, metric)
        }.sortedWith(compareBy({ it.measurement.date }, { it.measurement.time }, { it.measurement.id }))

@Composable
@Suppress("CyclomaticComplexMethod")
private fun GrowthChart(
    indicator: GrowthIndicator,
    lines: List<GrowthReferenceLine>,
    points: List<ChildChartPoint>,
    xMaxForAge: Double?,
    transitionX: Double?,
) {
    var zoom by remember(indicator, xMaxForAge) { mutableFloatStateOf(1f) }
    var panX by remember(indicator, xMaxForAge) { mutableFloatStateOf(0f) }
    var selected by remember(indicator, xMaxForAge) { mutableStateOf<ChildChartPoint?>(null) }
    val allX = lines.flatMap { it.points }.map { it.x } + points.map { it.x }
    val allY = lines.flatMap { it.points }.map { it.value } + points.map { it.y }
    val baseXMin = if (xMaxForAge != null) 0.0 else allX.minOrNull() ?: 0.0
    val baseXMax = xMaxForAge ?: allX.maxOrNull() ?: 1.0
    val yMinValue = allY.minOrNull() ?: 0.0
    val yMaxValue = allY.maxOrNull() ?: 1.0
    val yPadding = ((yMaxValue - yMinValue) * .08).coerceAtLeast(1.0)
    val yMin = yMinValue - yPadding
    val yMax = yMaxValue + yPadding
    val scheme = MaterialTheme.colorScheme
    val summary = "${indicator.label()}. ${points.size} mjerenja. Raspon x ${"%.1f".format(Locale.US, baseXMin)} do ${"%.1f".format(Locale.US, baseXMax)}; raspon vrijednosti ${"%.1f".format(Locale.US, yMinValue)} do ${"%.1f".format(Locale.US, yMaxValue)}."

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(indicator.label(), fontWeight = FontWeight.Bold)
                    Text(if (indicator == GrowthIndicator.WEIGHT_FOR_AGE || indicator == GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT) "kg" else "cm", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    zoom = 1f
                    panX = 0f
                    selected = null
                }) {
                    Icon(Icons.Default.Refresh, null)
                    Text("Početni prikaz", Modifier.padding(start = 6.dp))
                }
            }
            val chartModifier =
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .testTag("growth-chart-${indicator.name.lowercase()}")
                    .semantics { contentDescription = summary }
                    .pointerInput(indicator, baseXMin, baseXMax, yMin, yMax, zoom, panX, points) {
                        detectTapGestures { tap ->
                            val plotLeft = size.width * .11f
                            val plotRight = size.width * .97f
                            val plotTop = size.height * .07f
                            val plotBottom = size.height * .88f
                            val visibleWidth = (baseXMax - baseXMin) / zoom
                            val maxPan = ((baseXMax - baseXMin) - visibleWidth).coerceAtLeast(0.0)
                            val startX = (baseXMin + panX * maxPan).coerceIn(baseXMin, baseXMax - visibleWidth)

                            fun map(point: ChildChartPoint): Offset =
                                Offset(
                                    (plotLeft + ((point.x - startX) / visibleWidth).toFloat() * (plotRight - plotLeft)),
                                    (plotBottom - ((point.y - yMin) / (yMax - yMin)).toFloat() * (plotBottom - plotTop)),
                                )
                            selected = points.minByOrNull { hypot((map(it).x - tap.x).toDouble(), (map(it).y - tap.y).toDouble()) }?.takeIf { hypot((map(it).x - tap.x).toDouble(), (map(it).y - tap.y).toDouble()) < 42 }
                        }
                    }.pointerInput(indicator) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                            panX = (panX - pan.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            Canvas(chartModifier) {
                val left = size.width * .11f
                val right = size.width * .97f
                val top = size.height * .07f
                val bottom = size.height * .88f
                val visibleWidth = (baseXMax - baseXMin) / zoom
                val maxPan = ((baseXMax - baseXMin) - visibleWidth).coerceAtLeast(0.0)
                val startX = (baseXMin + panX * maxPan).coerceIn(baseXMin, baseXMax - visibleWidth)
                val endX = startX + visibleWidth

                fun mapX(x: Double) = left + ((x - startX) / visibleWidth).toFloat() * (right - left)

                fun mapY(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * (bottom - top)
                drawLine(scheme.onSurfaceVariant, Offset(left, top), Offset(left, bottom), 2f)
                drawLine(scheme.onSurfaceVariant, Offset(left, bottom), Offset(right, bottom), 2f)
                repeat(5) { index ->
                    val fraction = index / 4f
                    val x = left + (right - left) * fraction
                    val y = bottom - (bottom - top) * fraction
                    drawLine(scheme.outlineVariant, Offset(x, top), Offset(x, bottom), 1f)
                    drawLine(scheme.outlineVariant, Offset(left, y), Offset(right, y), 1f)
                }
                val referenceColors = listOf(scheme.tertiary, scheme.secondary, scheme.primary.copy(alpha = .65f), scheme.secondary, scheme.tertiary)
                lines.forEachIndexed { index, line ->
                    val visible = line.points.filter { it.x in (startX - visibleWidth * .05)..(endX + visibleWidth * .05) }
                    if (visible.size > 1) {
                        val path =
                            Path().apply {
                                moveTo(mapX(visible.first().x), mapY(visible.first().value))
                                visible.drop(1).forEach { lineTo(mapX(it.x), mapY(it.value)) }
                            }
                        drawPath(path, referenceColors[index % referenceColors.size], style = Stroke(width = if (line.percentile == 50) 3.5f else 2f, cap = StrokeCap.Round))
                    }
                }
                transitionX?.takeIf { it in startX..endX }?.let { x ->
                    drawLine(
                        scheme.error,
                        Offset(mapX(x), top),
                        Offset(mapX(x), bottom),
                        3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
                if (points.size > 1) {
                    val childPath =
                        Path().apply {
                            val first = points.first()
                            moveTo(mapX(first.x), mapY(first.y))
                            points.drop(1).forEach { lineTo(mapX(it.x), mapY(it.y)) }
                        }
                    drawPath(childPath, scheme.onSurface, style = Stroke(4f, cap = StrokeCap.Round))
                }
                points.filter { it.x in startX..endX }.forEach { point ->
                    drawCircle(if (selected == point) scheme.error else scheme.primary, if (selected == point) 11f else 8f, Offset(mapX(point.x), mapY(point.y)))
                    drawCircle(scheme.surface, if (selected == point) 4f else 3f, Offset(mapX(point.x), mapY(point.y)))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(3, 15, 50, 85, 97).forEach { Text("$it. p", style = MaterialTheme.typography.labelSmall) }
                Text("● dijete", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                if (transitionX != null) Text("┆ Prijelaz Fenton → WHO", style = MaterialTheme.typography.labelSmall, color = scheme.error)
            }
            selected?.let { point -> GrowthPointTooltip(point) }
        }
    }
}

@Composable
private fun GrowthPointTooltip(point: ChildChartPoint) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${LocalDate.parse(point.measurement.date).growthDate()} u ${LocalTime.parse(point.measurement.time).growthTime()}", fontWeight = FontWeight.Bold)
            Text("Kronološka dob: ${point.assessment.ages.chronologicalDays} dana")
            point.assessment.ages.correctedDays
                ?.let { Text("Korigirana dob: $it dana") }
            Text("Postmenstrualna dob: ${point.assessment.ages.postmenstrualDays / 7}+${point.assessment.ages.postmenstrualDays % 7}")
            Text("Izmjerena vrijednost: ${point.metric.rawValue}")
            if (point.metric.lengthCorrectionCm != 0.0) Text("Vrijednost za izračun: ${point.metric.calculationValue} cm (korekcija ${point.metric.lengthCorrectionCm} cm)")
            Text("${point.metric.percentileText()} · ${point.metric.zText()}")
            Text("${point.assessment.referenceSystem.label()} · ${point.assessment.ageBasis.label()}")
        }
    }
}
