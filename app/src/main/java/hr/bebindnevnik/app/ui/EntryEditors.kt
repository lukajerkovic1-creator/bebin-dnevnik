@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TummySessionEntity
import hr.bebindnevnik.app.domain.EntryDateTimeRules
import hr.bebindnevnik.app.domain.EntryWarning
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun MealEditorSheet(
    item: MealEntity?,
    defaultDate: LocalDate? = null,
    onWarnings: (Int, LocalDate, LocalTime, Long) -> Set<EntryWarning>,
    onSave: (Long, LocalDate, LocalTime, Int) -> Unit,
    onClose: () -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val initialDate = remember(item?.id, defaultDate, clock) { initialEntryDate(item?.date, defaultDate, clock) }
    val initialTime = remember(item?.id, clock) { item?.time?.let(LocalTime::parse) ?: LocalTime.now(clock).withSecond(0).withNano(0) }
    var amount by remember(item?.id) { mutableStateOf(item?.amountMl?.toString().orEmpty()) }
    var amountInteracted by remember(item?.id) { mutableStateOf(false) }
    var date by remember(item?.id) { mutableStateOf(initialDate) }
    var time by remember(item?.id) { mutableStateOf(initialTime) }
    var warnings by remember { mutableStateOf<Set<EntryWarning>>(emptySet()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val amountError = amountValidationMessage(amount)
    val displayedAmountError = amountError?.takeIf { amountInteracted }
    val dateTimeError = EntryDateTimeRules.validate(date, time, clock)?.let(EntryDateTimeRules::message)
    val canSave = amountError == null && dateTimeError == null

    fun attemptSave(confirmed: Boolean = false) {
        val parsedAmount = amount.toIntOrNull()
        val currentAmountError = amountValidationMessage(amount)
        val currentDateTimeError = EntryDateTimeRules.validate(date, time, clock)?.let(EntryDateTimeRules::message)
        if (parsedAmount == null || currentAmountError != null || currentDateTimeError != null) {
            saveError = currentAmountError ?: currentDateTimeError
            return
        }
        try {
            EntryDateTimeRules.requireValid(date, time, clock)
            val found = onWarnings(parsedAmount, date, time, item?.id ?: 0)
            if (found.isNotEmpty() && !confirmed) {
                warnings = found
            } else {
                onSave(item?.id ?: 0, date, time, parsedAmount)
                onClose()
            }
        } catch (error: IllegalArgumentException) {
            saveError = error.message ?: "Provjerite unesene vrijednosti."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("meal-editor"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (item == null) "Novi obrok" else "Uredi obrok",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                BabyIllustration(BabyIllustrationKind.BOTTLE, Modifier.size(BabyDimensions.IllustrationSmall))
            }
            Text("Brzi odabir količine", style = MaterialTheme.typography.titleMedium)
            QuantityQuickSelect(
                amount = amount,
                onAmountSelected = {
                    amount = it.toString()
                    amountInteracted = true
                },
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { value ->
                    amount = value.filter(Char::isDigit)
                    amountInteracted = true
                },
                modifier = Modifier.fillMaxWidth().testTag("meal-amount"),
                label = { Text("Količina") },
                suffix = { Text("ml") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = displayedAmountError != null,
                supportingText = displayedAmountError?.let { message -> ({ Text(message) }) },
            )
            DateTimeSelectionRows(date, time, { showDatePicker = true }, { showTimePicker = true })
            dateTimeError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("date-time-error")) }
            saveError?.takeIf { it != dateTimeError && it != amountError }?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            EditorActions(onClose = onClose, onSave = { attemptSave() }, saveEnabled = canSave)
        }
    }
    if (showDatePicker) {
        AppDatePickerDialog(
            selectedDate = date,
            today = LocalDate.now(clock),
            onConfirm = { selected ->
                date = selected
                showDatePicker = false
                saveError = null
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        EntryTimePickerDialog(time, { selected ->
            time = selected
            showTimePicker = false
            saveError = null
        }, { showTimePicker = false })
    }
    if (warnings.isNotEmpty()) {
        EntryWarningDialog(warnings.joinToString("\n") { warningLabelForEditor(it) }, { attemptSave(true) }, { warnings = emptySet() })
    }
}

@Composable
internal fun TummyEditorSheet(
    item: TummySessionEntity?,
    defaultDate: LocalDate? = null,
    onWarnings: (Long, LocalDate, LocalTime) -> Set<EntryWarning>,
    onSave: (Long, LocalDate, LocalTime, Long) -> Unit,
    onClose: () -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val initialDate = remember(item?.id, defaultDate, clock) { initialEntryDate(item?.date, defaultDate, clock) }
    val initialTime = remember(item?.id, clock) { item?.time?.let(LocalTime::parse) ?: LocalTime.now(clock).withSecond(0).withNano(0) }
    var minutes by remember(item?.id) { mutableStateOf(((item?.durationSeconds ?: 0) / 60).toString()) }
    var seconds by remember(item?.id) { mutableStateOf(((item?.durationSeconds ?: 0) % 60).toString()) }
    var date by remember(item?.id) { mutableStateOf(initialDate) }
    var time by remember(item?.id) { mutableStateOf(initialTime) }
    var warnings by remember { mutableStateOf<Set<EntryWarning>>(emptySet()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val duration = durationOrNull(minutes, seconds)
    val dateTimeError = EntryDateTimeRules.validate(date, time, clock)?.let(EntryDateTimeRules::message)
    val durationError = if (duration == null) "Unesite cijelo nenegativno trajanje." else null

    fun attemptSave(confirmed: Boolean = false) {
        val parsedDuration = durationOrNull(minutes, seconds)
        val currentDateTimeError = EntryDateTimeRules.validate(date, time, clock)?.let(EntryDateTimeRules::message)
        if (parsedDuration == null || currentDateTimeError != null) {
            saveError = durationError ?: currentDateTimeError
            return
        }
        try {
            EntryDateTimeRules.requireValid(date, time, clock)
            val found = onWarnings(parsedDuration, date, time)
            if (found.isNotEmpty() && !confirmed) {
                warnings = found
            } else {
                onSave(item?.id ?: 0, date, time, parsedDuration)
                onClose()
            }
        } catch (error: IllegalArgumentException) {
            saveError = error.message ?: "Provjerite unesene vrijednosti."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("tummy-editor"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (item == null) "Ručni unos tummy timea" else "Uredi tummy time",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                BabyIllustration(BabyIllustrationKind.TUMMY, Modifier.size(BabyDimensions.IllustrationSmall))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DurationField("Minute", minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), "tummy-minutes")
                DurationField("Sekunde", seconds, { seconds = it.filter(Char::isDigit) }, Modifier.weight(1f), "tummy-seconds")
            }
            durationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            DateTimeSelectionRows(date, time, { showDatePicker = true }, { showTimePicker = true })
            dateTimeError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("date-time-error")) }
            saveError?.takeIf { it != dateTimeError && it != durationError }?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            EditorActions(onClose = onClose, onSave = { attemptSave() }, saveEnabled = duration != null && dateTimeError == null)
        }
    }
    if (showDatePicker) {
        AppDatePickerDialog(
            selectedDate = date,
            today = LocalDate.now(clock),
            onConfirm = { selected ->
                date = selected
                showDatePicker = false
                saveError = null
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        EntryTimePickerDialog(time, { selected ->
            time = selected
            showTimePicker = false
            saveError = null
        }, { showTimePicker = false })
    }
    if (warnings.isNotEmpty()) {
        EntryWarningDialog(warnings.joinToString("\n") { warningLabelForEditor(it) }, { attemptSave(true) }, { warnings = emptySet() })
    }
}

@Composable
internal fun QuantityQuickSelect(
    amount: String,
    onAmountSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(40, 80), listOf(120, 160)).forEach { rowAmounts ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowAmounts.forEach { ml ->
                    val selected = amount == ml.toString()
                    FilterChip(
                        selected = selected,
                        onClick = { onAmountSelected(ml) },
                        label = {
                            Text(
                                "$ml ml",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        },
                        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, contentDescription = "Odabrano") }) else null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("quick-$ml"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DateTimeSelectionRows(
    date: LocalDate,
    time: LocalTime,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    SelectionRow(
        label = "Datum",
        value = date.hrDate(),
        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
        contentDescription = "Odaberi datum, trenutačno ${date.hrDate()}",
        testTag = "date-row",
        onClick = onDateClick,
    )
    SelectionRow(
        label = "Vrijeme",
        value = time.hrTime(),
        icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
        contentDescription = "Odaberi vrijeme, trenutačno ${time.hrTime()}",
        testTag = "time-row",
        onClick = onTimeClick,
    )
}

@Composable
internal fun TimeSelectionRow(
    time: LocalTime,
    label: String = "Vrijeme",
    testTag: String = "time-row",
    onClick: () -> Unit,
) {
    SelectionRow(
        label = label,
        value = time.hrTime(),
        icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
        contentDescription = "Odaberi vrijeme, trenutačno ${time.hrTime()}",
        testTag = testTag,
        onClick = onClick,
    )
}

@Composable
internal fun EntryTimePickerDialog(
    selectedTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(selectedTime.hour, selectedTime.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Odaberite vrijeme", modifier = Modifier.testTag("time-picker-title")) },
        text = { TimePicker(state = state, modifier = Modifier.testTag("time-picker")) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("U redu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } },
    )
}

@Composable
internal fun AppDatePickerDialog(
    selectedDate: LocalDate,
    today: LocalDate,
    autoConfirmSelection: Boolean = false,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectableDates = remember(today) { PastOrTodaySelectableDates(today) }
    val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate.toUtcMillis(), selectableDates = selectableDates)
    LaunchedEffect(state.selectedDateMillis, autoConfirmSelection) {
        val changedDate = state.selectedDateMillis?.toUtcDate()
        if (autoConfirmSelection && changedDate != null && changedDate != selectedDate) onConfirm(changedDate)
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onConfirm(it.toUtcDate()) } },
            ) { Text("U redu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } },
    ) {
        DatePicker(
            state = state,
            title = { Text("Odaberite datum", Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp).testTag("date-picker-title")) },
            showModeToggle = false,
            modifier =
                Modifier
                    .testTag("date-picker")
                    .semantics { contentDescription = "Odabrani datum ${selectedDate.hrDate()}" },
        )
    }
}

@Composable
private fun SelectionRow(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    testTag: String,
) = OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
    modifier = modifier.testTag(testTag),
)

@Composable
private fun EditorActions(
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Odustani") }
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save-entry"),
        ) { Text("Spremi") }
    }
}

@Composable
private fun EntryWarningDialog(
    text: String,
    confirm: () -> Unit,
    dismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = dismiss,
    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
    title = { Text("Potrebna je potvrda") },
    text = { Text(text) },
    confirmButton = { Button(onClick = confirm) { Text("Ipak spremi") } },
    dismissButton = { TextButton(onClick = dismiss) { Text("Ispravi unos") } },
)

private fun amountValidationMessage(amount: String): String? =
    when {
        amount.isBlank() -> "Unesite količinu."
        amount.toIntOrNull() == null -> "Unesite cijeli nenegativan broj."
        else -> null
    }

private fun initialEntryDate(
    storedDate: String?,
    defaultDate: LocalDate?,
    clock: Clock,
): LocalDate = storedDate?.let(LocalDate::parse) ?: defaultDate ?: LocalDate.now(clock)

private fun durationOrNull(
    minutes: String,
    seconds: String,
): Long? {
    val parsedMinutes = minutes.toLongOrNull() ?: return null
    val parsedSeconds = seconds.toLongOrNull() ?: return null
    return runCatching { Math.addExact(Math.multiplyExact(parsedMinutes, 60), parsedSeconds) }.getOrNull()
}

private fun warningLabelForEditor(warning: EntryWarning): String =
    when (warning) {
        EntryWarning.ZERO_ML -> "Količina je 0 ml."
        EntryWarning.OVER_500_ML -> "Količina je veća od 500 ml."
        EntryWarning.DUPLICATE_TIME -> "Već postoji obrok s potpuno jednakim datumom i vremenom."
        EntryWarning.UNDER_5_SECONDS -> "Sesija je kraća od 5 sekundi."
        EntryWarning.OVER_60_MINUTES -> "Sesija je dulja od 60 minuta."
    }
