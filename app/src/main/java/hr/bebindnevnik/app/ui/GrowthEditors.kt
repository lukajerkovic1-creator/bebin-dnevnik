package hr.bebindnevnik.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.ChildSex
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.LengthMeasurementType
import hr.bebindnevnik.app.domain.growth.GrowthIndicator
import hr.bebindnevnik.app.domain.growth.GrowthValidation
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GrowthProfileEditor(
    existing: ChildProfileEntity?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ChildProfileEntity) -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    var name by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.name.orEmpty()) }
    var sex by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.sex ?: ChildSex.DJEVOJCICA) }
    var birthDate by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.birthDate?.let(LocalDate::parse) ?: today) }
    var weeks by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.gestationalWeeks ?: 40) }
    var days by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.gestationalDays ?: 0) }
    var birthWeight by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.birthWeightG?.toString().orEmpty()) }
    var birthLength by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.birthLengthCm?.decimalInput().orEmpty()) }
    var birthHead by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.birthHeadCircumferenceCm?.decimalInput().orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmWarnings by rememberSaveable { mutableStateOf(false) }
    val draft =
        ChildProfileEntity(
            name = name,
            sex = sex,
            birthDate = birthDate.toString(),
            gestationalWeeks = weeks,
            gestationalDays = days,
            birthWeightG = birthWeight.toIntOrNull(),
            birthLengthCm = birthLength.toDecimalOrNull(),
            birthHeadCircumferenceCm = birthHead.toDecimalOrNull(),
            independentMobilityDate = existing?.independentMobilityDate,
            createdAt = existing?.createdAt ?: now,
            updatedAt = existing?.updatedAt ?: now,
        )
    val validation = GrowthValidation.profile(draft, today)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (existing == null) "Izradi profil djeteta" else "Uredi profil djeteta", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth().testTag("growth-profile-name"),
                label = { Text("Ime djeteta") },
                singleLine = true,
                supportingText = { if (name.isBlank()) Text("Ime je obvezno i prikazuje se samo unutar modula Rast.") },
            )
            Text("Spol se koristi samo za odabir odgovarajućih WHO/Fenton referentnih podataka.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChildSex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(if (option == ChildSex.DJEVOJCICA) "Djevojčica" else "Dječak") },
                        leadingIcon = if (sex == option) ({ Icon(Icons.Default.Check, "Odabrano") }) else null,
                    )
                }
            }
            DateTimeRow(Icons.Default.CalendarMonth, "Datum rođenja", birthDate.growthDate(), "Odaberi datum rođenja") { showDatePicker = true }
            Text("Gestacijska dob pri rođenju", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberDropdown("Puni tjedni", weeks, (GrowthValidation.MIN_GESTATIONAL_WEEKS..GrowthValidation.MAX_GESTATIONAL_WEEKS).toList(), { weeks = it }, Modifier.weight(1f))
                NumberDropdown("Dodatni dani", days, (0..6).toList(), { days = it }, Modifier.weight(1f))
            }
            Text("Prikaz: $weeks+$days", color = MaterialTheme.colorScheme.primary)
            GrowthNumberField("Porođajna težina", "g", birthWeight, { birthWeight = it.filter(Char::isDigit).take(5) }, KeyboardType.Number)
            GrowthNumberField("Porođajna duljina", "cm", birthLength, { birthLength = decimalInput(it) }, KeyboardType.Decimal)
            GrowthNumberField("Porođajni opseg glave", "cm", birthHead, { birthHead = decimalInput(it) }, KeyboardType.Decimal)
            validation.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            validation.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Odustani") }
                Button(
                    onClick = {
                        if (validation.warnings.isNotEmpty()) confirmWarnings = true else onSave(draft)
                    },
                    enabled = validation.valid,
                ) { Text("Spremi") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (showDatePicker) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = birthDate.toUtcMillis(),
                selectableDates = GrowthSelectableDates(LocalDate.MIN, today),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { birthDate = it.toUtcDate() }
                    showDatePicker = false
                }) { Text("Odaberi") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Odustani") } },
        ) { DatePicker(state = state) }
    }
    if (confirmWarnings) {
        WarningConfirmation(validation.warnings, onDismiss = { confirmWarnings = false }) {
            confirmWarnings = false
            onSave(draft)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun GrowthMeasurementEditor(
    profile: ChildProfileEntity,
    existing: GrowthMeasurementEntity?,
    existingMeasurements: List<GrowthMeasurementEntity>,
    today: LocalDate,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (GrowthMeasurementEntity) -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val initialTime = existing?.time?.let(LocalTime::parse) ?: LocalTime.now().withSecond(0).withNano(0)
    var date by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.date?.let(LocalDate::parse) ?: today) }
    var time by rememberSaveable(existing?.updatedAt) { mutableStateOf(initialTime) }
    var weight by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.weightG?.toString().orEmpty()) }
    var length by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.lengthHeightCm?.decimalInput().orEmpty()) }
    var type by rememberSaveable(existing?.updatedAt) {
        mutableStateOf(
            existing?.lengthMeasurementType
                ?: if (date.isBefore(LocalDate.parse(profile.birthDate).plusYears(2))) LengthMeasurementType.LEZECA_DULJINA else LengthMeasurementType.STOJECA_VISINA,
        )
    }
    var head by rememberSaveable(existing?.updatedAt) { mutableStateOf(existing?.headCircumferenceCm?.decimalInput().orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val draft =
        GrowthMeasurementEntity(
            id = existing?.id ?: 0,
            date = date.toString(),
            time = time.withSecond(0).withNano(0).toString(),
            weightG = weight.toIntOrNull(),
            lengthHeightCm = length.toDecimalOrNull(),
            lengthMeasurementType = type,
            headCircumferenceCm = head.toDecimalOrNull(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = existing?.updatedAt ?: now,
        )
    val validation = GrowthValidation.measurement(profile, draft, today, LocalTime.now())
    val duplicates = existingMeasurements.filter { it.id != draft.id && it.date == draft.date }
    val preview = if (validation.valid) viewModel.assessGrowth(profile, draft) else null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (existing == null) "Dodaj mjerenje" else "Uredi mjerenje", style = MaterialTheme.typography.headlineSmall)
            DateTimeRow(Icons.Default.CalendarMonth, "Datum", date.growthDate(), "Odaberi datum mjerenja") { showDatePicker = true }
            DateTimeRow(Icons.Default.Schedule, "Vrijeme", time.growthTime(), "Odaberi vrijeme mjerenja") {
                TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute) }, time.hour, time.minute, true).show()
            }
            GrowthNumberField("Težina", "g", weight, { weight = it.filter(Char::isDigit).take(5) }, KeyboardType.Number)
            GrowthNumberField("Duljina/visina", "cm", length, { length = decimalInput(it) }, KeyboardType.Decimal)
            Text("Vrsta mjerenja", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LengthMeasurementType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(if (option == LengthMeasurementType.LEZECA_DULJINA) "Ležeća duljina" else "Stojeća visina") },
                        leadingIcon = if (type == option) ({ Icon(Icons.Default.Check, "Odabrano") }) else null,
                    )
                }
            }
            GrowthNumberField("Opseg glave", "cm", head, { head = decimalInput(it) }, KeyboardType.Decimal)
            validation.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            validation.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
            preview?.let { GrowthPreview(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Odustani") }
                Button(onClick = { if (duplicates.isNotEmpty() || validation.warnings.isNotEmpty()) confirmSave = true else onSave(draft) }, enabled = validation.valid) {
                    Text("Spremi")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (showDatePicker) {
        val maxDate = minOf(today, LocalDate.parse(profile.birthDate).plusYears(5).minusDays(1))
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis(), selectableDates = GrowthSelectableDates(LocalDate.parse(profile.birthDate), maxDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        date = it.toUtcDate()
                        type = if (date.isBefore(LocalDate.parse(profile.birthDate).plusYears(2))) LengthMeasurementType.LEZECA_DULJINA else LengthMeasurementType.STOJECA_VISINA
                    }
                    showDatePicker = false
                }) { Text("Odaberi") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Odustani") } },
        ) { DatePicker(picker) }
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text(if (duplicates.isNotEmpty()) "Mogući duplikat" else "Provjerite mjerenje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    validation.warnings.forEach { Text(it) }
                    if (duplicates.isNotEmpty()) {
                        Text("Na isti datum već postoje mjerenja:")
                        duplicates.forEach { existingItem ->
                            Text("• ${LocalTime.parse(existingItem.time).growthTime()} · ${existingItem.shortValues()}")
                        }
                    }
                    Text("Želite li ipak spremiti zaseban zapis?")
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmSave = false
                    onSave(draft)
                }) { Text("Spremi") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun GrowthPreview(assessment: hr.bebindnevnik.app.domain.growth.GrowthAssessment) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pregled rezultata", style = MaterialTheme.typography.titleMedium)
        Text("${assessment.referenceSystem.label()} · ${assessment.ageBasis.label()}", color = MaterialTheme.colorScheme.primary)
        assessment.metrics.forEach { metric ->
            Text("${metric.indicator.label()}: ${metric.percentileText()} · ${metric.zText()}")
            if (metric.lengthCorrectionCm != 0.0) {
                Text("Korekcija samo za izračun: ${if (metric.lengthCorrectionCm > 0) "+" else ""}${metric.lengthCorrectionCm} cm", style = MaterialTheme.typography.bodySmall)
            }
            if (metric.isLow || metric.isHigh) Text("Ovo nije dijagnoza. Rezultat procijenite s pedijatrom.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GrowthNumberField(
    label: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text(suffix) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}

@Composable
private fun DateTimeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Default.KeyboardArrowDown, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberDropdown(
    label: String,
    selected: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WarningConfirmation(
    warnings: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provjerite neuobičajene vrijednosti") },
        text = { Column { warnings.forEach { Text("• $it") } } },
        confirmButton = { Button(onClick = onConfirm) { Text("Svejedno spremi") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vrati se") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private class GrowthSelectableDates(
    private val min: LocalDate,
    private val max: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcDate() in min..max

    override fun isSelectableYear(year: Int): Boolean = year in min.year..max.year
}

private fun Double.decimalInput(): String = "%.1f".format(Locale.US, this).replace('.', ',')

private fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

private fun decimalInput(value: String): String {
    val normalized = value.replace('.', ',').filter { it.isDigit() || it == ',' }
    val firstComma = normalized.indexOf(',')
    return if (firstComma < 0) normalized.take(3) else normalized.take(firstComma + 2).take(5)
}

private fun GrowthMeasurementEntity.shortValues(): String =
    listOfNotNull(
        weightG?.let { "$it g" },
        lengthHeightCm?.let { "${it.decimalInput()} cm" },
        headCircumferenceCm?.let { "glava ${it.decimalInput()} cm" },
    ).joinToString(" · ")
