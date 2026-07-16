package hr.bebindnevnik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.IndividualFeedingTargetEntity
import hr.bebindnevnik.app.data.IndividualTummyTargetEntity
import hr.bebindnevnik.app.domain.growth.GrowthAgeBasis
import hr.bebindnevnik.app.domain.guidelines.FeedingTargetResult
import hr.bebindnevnik.app.domain.guidelines.GUIDELINE_DISCLAIMER
import hr.bebindnevnik.app.domain.guidelines.RangeStatus
import hr.bebindnevnik.app.domain.guidelines.TargetOrigin
import hr.bebindnevnik.app.domain.guidelines.TummyTargetResult
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun FeedingGuidelineSummary(
    result: FeedingTargetResult,
    onCompleteData: () -> Unit,
    onSetMealCount: (Int) -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var editMealCount by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .clickable { showDetails = true }
            .semantics { contentDescription = "Okvirni cilj hranjenja. Dodirnite za objašnjenje izračuna." }
            .testTag("feeding-guideline-summary"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (result.origin == TargetOrigin.INDIVIDUAL) "Individualni cilj" else "Okvirni cilj hranjenja",
                    fontWeight = FontWeight.Bold,
                )
            }
            if (result.lowerMl != null && result.upperMl != null) {
                Text(
                    "${result.recordedMl} ml / okvirno ${result.lowerMl}–${result.upperMl} ml",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (result.evidenceComplete == false) {
                    Text("Evidentirano ${result.recordedMl} ml · unos nije potpun")
                } else if (result.evidenceComplete == null) {
                    Text("Označite predstavljaju li evidentirani ml sav dnevni unos.")
                } else {
                    result.percentOfLower?.let { percent ->
                        Text("$percent % donje granice")
                        LinearProgressIndicator(
                            progress = { result.visualProgress },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Napredak $percent posto" },
                        )
                    }
                    result.status?.let {
                        Text(
                            when (it) {
                                RangeStatus.BELOW -> "Ispod okvirnog raspona"
                                RangeStatus.WITHIN -> "Unutar okvirnog raspona"
                                RangeStatus.ABOVE -> "Iznad okvirnog raspona"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (result.weight?.olderThanThirtyDays == true) {
                    Text("Težina nije nedavno ažurirana – cilj je okviran.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(result.message ?: "Evidentirano: ${result.recordedMl} ml")
                if (result.message?.contains("dodajte", ignoreCase = true) == true) {
                    TextButton(onClick = onCompleteData, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dovrši podatke") }
                }
            }
            Text(GUIDELINE_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showDetails) {
        GuidelineDetailsSheet(
            title = "Kako se izračunava hranjenje?",
            lines = feedingDetails(result),
            onDismiss = { showDetails = false },
            extra = {
                TextButton(onClick = { editMealCount = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Promijeni očekivani broj obroka")
                }
            },
        )
    }
    if (editMealCount) {
        NumberDialog(
            title = "Očekivani broj obroka",
            initial = result.expectedMealCount?.toString().orEmpty(),
            range = 1..24,
            onDismiss = { editMealCount = false },
            onSave = {
                onSetMealCount(it)
                editMealCount = false
            },
        )
    }
}

@Composable
internal fun TummyGuidelineSummary(
    result: TummyTargetResult,
    onCompleteData: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .clickable { showDetails = true }
            .semantics { contentDescription = "Okvirni cilj tummy timea. Dodirnite za objašnjenje izračuna." }
            .testTag("tummy-guideline-summary"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                if (result.origin == TargetOrigin.INDIVIDUAL) "Individualni cilj tummy timea" else "Okvirni cilj tummy timea",
                fontWeight = FontWeight.Bold,
            )
            if (result.targetMinutes != null && result.percent != null) {
                Text("${result.recordedMinutes}/${result.targetMinutes} min · ${result.percent} %", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { result.visualProgress },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Napredak ${result.percent} posto" },
                )
                Text("Budno i pod nadzorom, raspoređeno kroz dan.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    if (result.independentlyMobile) {
                        "Danas evidentirano: ${result.recordedMinutes} min"
                    } else {
                        result.message ?: "Evidentirano: ${result.recordedMinutes} min"
                    },
                )
                if (result.message?.contains("dodajte", ignoreCase = true) == true) {
                    TextButton(onClick = onCompleteData, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dovrši podatke") }
                }
            }
            Text(GUIDELINE_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showDetails) {
        GuidelineDetailsSheet("Kako se izračunava tummy time?", tummyDetails(result), { showDetails = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuidelineDetailsSheet(
    title: String,
    lines: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            lines.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.42f))
                    Text(value, modifier = Modifier.weight(0.58f))
                }
            }
            extra()
            Text(GUIDELINE_DISCLAIMER, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Zatvori") }
        }
    }
}

private fun feedingDetails(result: FeedingTargetResult): List<Pair<String, String>> =
    buildList {
        result.age?.let {
            add("Dob" to "${it.effectiveDays.coerceAtLeast(0)} dana")
            add("Vrsta dobi" to if (it.basis == GrowthAgeBasis.CORRECTED) "Korigirana" else "Kronološka")
        }
        result.weight?.let { add("Težina" to "${it.grams} g · ${it.measurementDate.hrDate()}") }
        if (result.lowerMl != null && result.upperMl != null) add("Dnevni raspon" to "${result.lowerMl}–${result.upperMl} ml")
        add(
            "Evidencija mlijeka" to
                when (result.evidenceComplete) {
                    true -> "Potpuna"
                    false -> "Nije potpuna"
                    null -> "Nije postavljeno"
                },
        )
        add("Broj obroka" to (result.expectedMealCount?.let { "$it (${if (result.expectedMealCountIsManual) "ručno" else "7-dnevni prosjek"})" } ?: "Nije postavljen"))
        if (result.perMealLowerMl != null && result.perMealUpperMl != null) add("Po obroku" to "${result.perMealLowerMl}–${result.perMealUpperMl} ml")
        add("Vrsta cilja" to if (result.origin == TargetOrigin.INDIVIDUAL) "Individualni cilj" else "Okvirna smjernica")
        result.guideline?.let { add("Izvor" to "${it.sourceName} · ${it.guidelineId} v${it.version}") }
        result.message?.let { add("Napomena" to it) }
    }

private fun tummyDetails(result: TummyTargetResult): List<Pair<String, String>> =
    buildList {
        result.age?.let {
            add("Dob" to "${it.effectiveDays.coerceAtLeast(0)} dana")
            add("Vrsta dobi" to if (it.basis == GrowthAgeBasis.CORRECTED) "Korigirana" else "Kronološka")
        }
        result.targetMinutes?.let { add("Dnevni cilj" to "$it min") }
        add("Vrsta cilja" to if (result.origin == TargetOrigin.INDIVIDUAL) "Individualni cilj" else "Okvirna smjernica")
        add("Samostalno pokretno" to if (result.independentlyMobile) "Da" else "Ne")
        result.guideline?.let { add("Izvor" to "${it.sourceName} · ${it.guidelineId} v${it.version}") }
        result.message?.let { add("Napomena" to it) }
    }

@Composable
private fun NumberDialog(
    title: String,
    initial: String,
    range: IntRange,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val number = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value,
                { value = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.testTag("number-dialog-input"),
                label = { Text(title) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = number !in range,
                supportingText = { if (number !in range) Text("Dopušteno: ${range.first}–${range.last}.") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSave(requireNotNull(number)) }, enabled = number in range) { Text("Spremi") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } },
    )
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod") // Four lightweight steps intentionally share one saved state machine.
internal fun GuidelineWizard(
    state: UiState,
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    val hasWeight = state.childProfile?.birthWeightG != null || state.growthMeasurements.any { it.weightG != null }
    val firstIncomplete =
        when {
            state.childProfile == null -> 0
            !hasWeight -> 1
            state.milkCompletenessHistory.isEmpty() -> 2
            else -> 3
        }
    var step by rememberSaveable { mutableIntStateOf(firstIncomplete) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showMeasurement by rememberSaveable { mutableStateOf(false) }
    var milkComplete by rememberSaveable { mutableStateOf<Boolean?>(state.milkCompletenessHistory.firstOrNull()?.complete) }
    var mobile by rememberSaveable { mutableStateOf(state.childProfile?.independentMobilityDate != null) }
    var mobilityDate by rememberSaveable { mutableStateOf(state.childProfile?.independentMobilityDate?.let(LocalDate::parse) ?: state.currentLocalDate) }
    var showMobilityDate by rememberSaveable { mutableStateOf(false) }
    var feedingLower by rememberSaveable { mutableStateOf("") }
    var feedingUpper by rememberSaveable { mutableStateOf("") }
    var tummyMinutes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Postavite okvirne ciljeve") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Unesite osnovne podatke kako bi aplikacija mogla prikazivati okvirne ciljeve hranjenja i tummy timea.")
                LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
                Text("Korak ${step + 1} od 4", fontWeight = FontWeight.Bold)
                when (step) {
                    0 -> {
                        Text("Profil")
                        Text(if (state.childProfile == null) "Nedostaju datum rođenja, spol i gestacijska dob." else "Profil je dovršen.")
                        OutlinedButton(onClick = { showProfile = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.childProfile == null) "Unesi profil" else "Pregledaj profil")
                        }
                    }

                    1 -> {
                        Text("Tjelesna težina")
                        Text(if (hasWeight) "Tjelesna težina je evidentirana." else "Dodajte stvarno mjerenje težine; aplikacija je neće procjenjivati.")
                        OutlinedButton(
                            onClick = { showMeasurement = true },
                            enabled = state.childProfile != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Dodaj mjerenje") }
                    }

                    2 -> {
                        Text("Predstavljaju li evidentirani mililitri sav dnevni unos mlijeka?")
                        listOf(true to "Da", false to "Ne / dijete dio obroka izravno doji ili se sav unos ne evidentira").forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().clickable { milkComplete = option.first }.heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(milkComplete == option.first, { milkComplete = option.first })
                                Text(option.second)
                            }
                        }
                    }

                    else -> {
                        Text("Dodatne postavke")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Dijete je samostalno pokretno", Modifier.weight(1f))
                            Switch(mobile, { mobile = it })
                        }
                        if (mobile) {
                            OutlinedButton(onClick = { showMobilityDate = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Od ${mobilityDate.hrDate()}")
                            }
                        }
                        OutlinedTextField(
                            feedingLower,
                            { feedingLower = it.filter(Char::isDigit).take(4) },
                            label = { Text("Individualni cilj hranjenja – donja granica (ml)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            feedingUpper,
                            { feedingUpper = it.filter(Char::isDigit).take(4) },
                            label = { Text("Individualni cilj hranjenja – gornja granica (ml)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            tummyMinutes,
                            { tummyMinutes = it.filter(Char::isDigit).take(3) },
                            label = { Text("Individualni cilj tummy timea (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Text("Sva tri polja su neobvezna; ciljeve kasnije možete uređivati u Rast → Profil.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 2) milkComplete?.let { viewModel.setMilkEvidenceComplete(it, state.selectedDate) }
                    if (step == 3) {
                        viewModel.setIndependentMobilityDate(mobilityDate.takeIf { mobile })
                        val lower = feedingLower.toIntOrNull()
                        val upper = feedingUpper.toIntOrNull()
                        val now = System.currentTimeMillis()
                        if (lower != null && upper != null && lower > 0 && upper >= lower) {
                            viewModel.saveIndividualFeedingTarget(
                                IndividualFeedingTargetEntity(
                                    lowerMlPerDay = lower,
                                    upperMlPerDay = upper,
                                    startDate = state.selectedDate.toString(),
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                        }
                        tummyMinutes.toIntOrNull()?.takeIf { it in 1..240 }?.let {
                            viewModel.saveIndividualTummyTarget(
                                IndividualTummyTargetEntity(
                                    minutesPerDay = it,
                                    startDate = state.selectedDate.toString(),
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                        }
                        viewModel.completeGuidelineWizard()
                        onClose()
                    } else {
                        step++
                    }
                },
            ) { Text(if (step == 3) "Završi" else "Dalje") }
        },
        dismissButton = {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Natrag") }
                TextButton(onClick = {
                    if (step < 3) {
                        step++
                    } else {
                        viewModel.completeGuidelineWizard()
                        onClose()
                    }
                }) { Text("Preskoči") }
                TextButton(onClick = {
                    viewModel.dismissGuidelineWizard()
                    onClose()
                }) { Text("Odustani") }
            }
        },
    )
    if (showProfile) {
        GrowthProfileEditor(
            existing = state.childProfile,
            today = state.currentLocalDate,
            onDismiss = { showProfile = false },
            onSave = {
                viewModel.saveChildProfile(it)
                showProfile = false
            },
        )
    }
    if (showMeasurement && state.childProfile != null) {
        GrowthMeasurementEditor(
            profile = state.childProfile,
            existing = null,
            existingMeasurements = state.growthMeasurements,
            today = state.currentLocalDate,
            viewModel = viewModel,
            onDismiss = { showMeasurement = false },
            onSave = {
                viewModel.saveGrowthMeasurement(it)
                showMeasurement = false
            },
        )
    }
    if (showMobilityDate) {
        AppDatePickerDialog(mobilityDate, state.currentLocalDate, onConfirm = {
            mobilityDate = it
            showMobilityDate = false
        }, onDismiss = { showMobilityDate = false })
    }
}

@Composable
internal fun GuidelineProfileSettings(
    state: UiState,
    viewModel: MainViewModel,
) {
    var addFeeding by rememberSaveable { mutableStateOf(false) }
    var addTummy by rememberSaveable { mutableStateOf(false) }
    var showMobilityDate by rememberSaveable { mutableStateOf(false) }
    val mobilityDate = state.childProfile?.independentMobilityDate?.let(LocalDate::parse)
    OutlinedCard(Modifier.fillMaxWidth().testTag("individual-targets-card")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Individualni ciljevi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dijete je samostalno pokretno", Modifier.weight(1f))
                Switch(mobilityDate != null, { enabled -> viewModel.setIndependentMobilityDate(state.currentLocalDate.takeIf { enabled }) })
            }
            if (mobilityDate != null) {
                TextButton(onClick = { showMobilityDate = true }) { Text("Od ${mobilityDate.hrDate()}") }
            }
            HorizontalDivider()
            Text("Hranjenje", fontWeight = FontWeight.Bold)
            state.individualFeedingTargets.forEach { target ->
                TargetRow(
                    "${target.lowerMlPerDay}–${target.upperMlPerDay} ml/dan",
                    target.startDate,
                    target.endDate,
                    { viewModel.deleteIndividualFeedingTarget(target) },
                )
            }
            OutlinedButton(onClick = { addFeeding = true }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj cilj hranjenja") }
            Text("Tummy time", fontWeight = FontWeight.Bold)
            state.individualTummyTargets.forEach { target ->
                TargetRow("${target.minutesPerDay} min/dan", target.startDate, target.endDate, { viewModel.deleteIndividualTummyTarget(target) })
            }
            OutlinedButton(onClick = { addTummy = true }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj tummy-time cilj") }
            Text(GUIDELINE_DISCLAIMER, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (showMobilityDate && mobilityDate != null) {
        AppDatePickerDialog(mobilityDate, state.currentLocalDate, onConfirm = {
            viewModel.setIndependentMobilityDate(it)
            showMobilityDate = false
        }, onDismiss = { showMobilityDate = false })
    }
    if (addFeeding) {
        FeedingTargetDialog(state.currentLocalDate, { addFeeding = false }) {
            viewModel.saveIndividualFeedingTarget(it)
            addFeeding = false
        }
    }
    if (addTummy) {
        TummyTargetDialog(state.currentLocalDate, { addTummy = false }) {
            viewModel.saveIndividualTummyTarget(it)
            addTummy = false
        }
    }
}

@Composable
private fun TargetRow(
    value: String,
    start: String,
    end: String?,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(value)
            Text("${LocalDate.parse(start).hrDate()} – ${end?.let { LocalDate.parse(it).hrDate() } ?: "bez završetka"}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Izbriši") }
    }
}

@Composable
private fun FeedingTargetDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (IndividualFeedingTargetEntity) -> Unit,
) {
    var lower by rememberSaveable { mutableStateOf("") }
    var upper by rememberSaveable { mutableStateOf("") }
    TargetDialog("Individualni cilj hranjenja", today, onDismiss) { start, end ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lower, { lower = it.filter(Char::isDigit).take(4) }, label = { Text("Donja granica (ml/dan)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(upper, { upper = it.filter(Char::isDigit).take(4) }, label = { Text("Gornja granica (ml/dan)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(IndividualFeedingTargetEntity(lowerMlPerDay = lower.toInt(), upperMlPerDay = upper.toInt(), startDate = start.toString(), endDate = end?.toString(), createdAt = now, updatedAt = now))
                },
                enabled = lower.toIntOrNull()?.let { low -> upper.toIntOrNull()?.let { it >= low && low > 0 } } == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spremi") }
        }
    }
}

@Composable
private fun TummyTargetDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (IndividualTummyTargetEntity) -> Unit,
) {
    var minutes by rememberSaveable { mutableStateOf("") }
    TargetDialog("Individualni tummy-time cilj", today, onDismiss) { start, end ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Minute dnevno") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(IndividualTummyTargetEntity(minutesPerDay = minutes.toInt(), startDate = start.toString(), endDate = end?.toString(), createdAt = now, updatedAt = now))
                },
                enabled = minutes.toIntOrNull() in 1..240,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spremi") }
        }
    }
}

@Composable
private fun TargetDialog(
    title: String,
    today: LocalDate,
    onDismiss: () -> Unit,
    content: @Composable (LocalDate, LocalDate?) -> Unit,
) {
    var start by rememberSaveable { mutableStateOf(today) }
    var end by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.fillMaxWidth()) { Text("Početak: ${start.hrDate()}") }
                OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.fillMaxWidth()) { Text("Završetak: ${end?.hrDate() ?: "nije postavljen"}") }
                if (end != null) TextButton(onClick = { end = null }) { Text("Ukloni završetak") }
                content(start, end)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } },
    )
    if (pickStart) {
        AppDatePickerDialog(
            start,
            today,
            onConfirm = {
                start = it
                if (end?.isBefore(it) == true) end = null
                pickStart = false
            },
            onDismiss = { pickStart = false },
        )
    }
    if (pickEnd) {
        AppDatePickerDialog(
            end ?: start,
            today,
            onConfirm = {
                if (!it.isBefore(start)) end = it
                pickEnd = false
            },
            onDismiss = { pickEnd = false },
        )
    }
}
