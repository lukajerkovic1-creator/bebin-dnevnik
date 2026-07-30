@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.ComplementaryFoodDaySummary
import hr.bebindnevnik.app.data.ComplementaryFoodMealEntity
import hr.bebindnevnik.app.data.ComplementaryFoodUnit
import hr.bebindnevnik.app.domain.ComplementaryFoodLogic
import hr.bebindnevnik.app.domain.ComplementaryFoodValidation
import hr.bebindnevnik.app.domain.ComplementaryFoodWarning
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun ComplementaryFoodCard(
    summary: ComplementaryFoodDaySummary,
    meals: List<ComplementaryFoodMealEntity>,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onEdit: (ComplementaryFoodMealEntity) -> Unit,
    onDelete: (ComplementaryFoodMealEntity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("complementary-food-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BabyIllustration(BabyIllustrationKind.FOOD, Modifier.size(72.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dohrana", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (summary.mealCount == 0) {
                        Text("Dohrana još nije evidentirana.")
                    } else {
                        Text("${summary.mealCount} ${foodMealCountLabel(summary.mealCount)}")
                        Text(
                            foodTotalsText(summary.totalG, summary.totalMl, summary.totalTeaspoons),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            summary.lastMeal?.let { last ->
                Column(
                    Modifier.fillMaxWidth().semantics {
                        contentDescription =
                            "Posljednji obrok dohrane u ${last.time.hrStoredTime()}, " +
                            "${last.ingredients.joinToString(", ")}, ${ComplementaryFoodLogic.formatQuantity(last.amount, last.unit)}"
                    },
                ) {
                    Text("Posljednji obrok", style = MaterialTheme.typography.labelLarge)
                    Text(last.ingredients.joinToString(" · "), fontWeight = FontWeight.SemiBold)
                    Text("${last.time.hrStoredTime()} · ${ComplementaryFoodLogic.formatQuantity(last.amount, last.unit)}")
                }
            }
            if (canEdit) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("add-complementary-food"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dodaj obrok dohrane")
                }
            }
            meals.sortedWith(compareBy<ComplementaryFoodMealEntity> { it.time }.thenBy { it.id }).forEach { meal ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("complementary-food-${meal.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${meal.time.hrStoredTime()} · ${ComplementaryFoodLogic.formatQuantity(meal.amount, meal.unit)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(meal.ingredients.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    if (canEdit) {
                        IconButton(onClick = { onEdit(meal) }, modifier = Modifier.testTag("edit-complementary-food-${meal.id}")) {
                            Icon(Icons.Default.Edit, contentDescription = "Uredi obrok dohrane")
                        }
                        IconButton(onClick = { onDelete(meal) }, modifier = Modifier.testTag("delete-complementary-food-${meal.id}")) {
                            Icon(Icons.Default.Delete, contentDescription = "Izbriši obrok dohrane")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ComplementaryFoodEditorSheet(
    item: ComplementaryFoodMealEntity?,
    defaultDate: LocalDate,
    today: LocalDate,
    suggestions: List<String>,
    validate: (List<String>, Int?, ComplementaryFoodUnit, LocalDate, LocalTime, Long) -> ComplementaryFoodValidation,
    onSave: (ComplementaryFoodMealEntity) -> Unit,
    onClose: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ingredients by remember(item?.id) {
        mutableStateOf(ComplementaryFoodLogic.normalizeIngredients(item?.ingredients.orEmpty()))
    }
    var ingredientInput by remember(item?.id) { mutableStateOf("") }
    var amountText by remember(item?.id) { mutableStateOf(item?.amount?.toString().orEmpty()) }
    var unit by remember(item?.id) { mutableStateOf(item?.unit ?: ComplementaryFoodUnit.G) }
    var date by remember(item?.id, defaultDate) { mutableStateOf(item?.date?.let(LocalDate::parse) ?: defaultDate) }
    var time by remember(item?.id) {
        mutableStateOf(item?.time?.let(LocalTime::parse) ?: LocalTime.now().withSecond(0).withNano(0))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var warningConfirmation by remember { mutableStateOf<Set<ComplementaryFoodWarning>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun addIngredient() {
        ingredients = ComplementaryFoodLogic.normalizeIngredients(ingredients + ingredientInput)
        ingredientInput = ""
    }

    fun ingredientsIncludingInput(): List<String> = ComplementaryFoodLogic.normalizeIngredients(ingredients + ingredientInput)

    fun trySave(confirmWarnings: Boolean = false) {
        val amount = amountText.toIntOrNull()
        val effectiveIngredients = ingredientsIncludingInput()
        val validation = validate(effectiveIngredients, amount, unit, date, time, item?.id ?: 0)
        if (!validation.valid) return
        if (!confirmWarnings && validation.warnings.isNotEmpty()) {
            warningConfirmation = validation.warnings
            return
        }
        onSave(
            ComplementaryFoodMealEntity(
                id = item?.id ?: 0,
                date = date.toString(),
                time = time.withSecond(0).withNano(0).toString(),
                ingredients = effectiveIngredients,
                amount = amount ?: return,
                unit = unit,
                createdAt = item?.createdAt ?: 0,
                updatedAt = item?.updatedAt ?: 0,
            ),
        )
        onClose()
    }

    val amount = amountText.toIntOrNull()
    val validation = validate(ingredientsIncludingInput(), amount, unit, date, time, item?.id ?: 0)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, modifier = Modifier.testTag("complementary-food-editor")) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (item == null) "Novi obrok dohrane" else "Uredi obrok dohrane", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = ingredientInput,
                onValueChange = { ingredientInput = it },
                label = { Text("Namirnica") },
                placeholder = { Text("npr. mrkva") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addIngredient() }),
                trailingIcon = {
                    IconButton(onClick = { addIngredient() }, modifier = Modifier.testTag("add-ingredient")) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj namirnicu")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ingredient-input"),
            )
            if (ingredients.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ingredients.forEach { ingredient ->
                        InputChip(
                            selected = true,
                            onClick = { ingredients = ingredients - ingredient },
                            label = { Text(ingredient) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Ukloni $ingredient", Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("ingredient-chip-${ingredient.lowercase()}"),
                        )
                    }
                }
            }
            val normalizedSuggestions = ComplementaryFoodLogic.normalizeIngredients(suggestions)
            val unusedSuggestions =
                normalizedSuggestions.filter { suggestion ->
                    ingredients.none { it.equals(suggestion, true) }
                }
            if (unusedSuggestions.isNotEmpty()) {
                Text("Prethodno korišteno", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    unusedSuggestions.take(8).forEach { suggestion ->
                        AssistChip(
                            onClick = {
                                ingredients = ComplementaryFoodLogic.normalizeIngredients(ingredients + suggestion)
                            },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
            ComplementaryFoodUnitSelector(
                selected = unit,
                onSelected = { selected ->
                    if (selected != unit) {
                        unit = selected
                        amountText = ""
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("Količina je izbrisana zbog promjene jedinice.")
                        }
                    }
                },
            )
            SnackbarHost(snackbarHostState, Modifier.fillMaxWidth().testTag("complementary-food-snackbar"))
            if (unit == ComplementaryFoodUnit.TEASPOON) {
                Text("Brzi odabir", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (value in 1..5) {
                        FilterChip(
                            selected = amountText == value.toString(),
                            onClick = { amountText = value.toString() },
                            label = { Text(value.toString()) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("teaspoon-quick-$value"),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { value -> if (value.all(Char::isDigit)) amountText = value },
                label = { Text("Ukupna količina") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    if (!validation.valid && validation.error != null && validation.error.contains("količin", ignoreCase = true)) {
                        Text(validation.error)
                    }
                },
                isError = !validation.valid && validation.error?.contains("količin", ignoreCase = true) == true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("complementary-food-amount"),
            )
            DateTimeSelectionRows(date, time, { showDatePicker = true }, { showTimePicker = true })
            if (!validation.valid && validation.error != null && !validation.error.contains("količin", ignoreCase = true)) {
                Text(validation.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("complementary-food-error"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Odustani") }
                Button(onClick = { trySave() }, enabled = validation.valid, modifier = Modifier.heightIn(min = 48.dp).testTag("save-complementary-food")) {
                    Text("Spremi")
                }
            }
        }
    }
    if (showDatePicker) {
        AppDatePickerDialog(date, today, onConfirm = {
            date = it
            showDatePicker = false
        }, onDismiss = { showDatePicker = false })
    }
    if (showTimePicker) {
        EntryTimePickerDialog(time, onConfirm = {
            time = it
            showTimePicker = false
        }, onDismiss = { showTimePicker = false })
    }
    if (warningConfirmation.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { warningConfirmation = emptySet() },
            title = { Text("Provjerite unos") },
            text = { Text(warningText(warningConfirmation)) },
            confirmButton = {
                Button(onClick = {
                    warningConfirmation = emptySet()
                    trySave(confirmWarnings = true)
                }) { Text("Ipak spremi") }
            },
            dismissButton = { TextButton(onClick = { warningConfirmation = emptySet() }) { Text("Vrati se") } },
        )
    }
}

private fun foodMealCountLabel(count: Int): String = if (count == 1) "obrok dohrane" else "obroka dohrane"

private fun foodTotalsText(
    totalG: Int,
    totalMl: Int,
    totalTeaspoons: Int,
): String =
    listOfNotNull(
        totalG.takeIf { it > 0 }?.let { ComplementaryFoodLogic.formatQuantity(it, ComplementaryFoodUnit.G) },
        totalMl.takeIf { it > 0 }?.let { ComplementaryFoodLogic.formatQuantity(it, ComplementaryFoodUnit.ML) },
        totalTeaspoons
            .takeIf { it > 0 }
            ?.let { ComplementaryFoodLogic.formatQuantity(it, ComplementaryFoodUnit.TEASPOON) },
    ).joinToString(" · ")
        .ifEmpty { "0" }

private fun warningText(warnings: Set<ComplementaryFoodWarning>): String =
    buildList {
        if (ComplementaryFoodWarning.ZERO in warnings) add("Količina je 0.")
        if (ComplementaryFoodWarning.OVER_500 in warnings) add("Količina je veća od 500.")
        if (ComplementaryFoodWarning.OVER_30_TEASPOONS in warnings) {
            add("Količina je veća od 30 žličica.")
        }
        if (ComplementaryFoodWarning.POSSIBLE_DUPLICATE in warnings) add("Mogući duplikat: isti datum, vrijeme i namirnice već postoje.")
    }.joinToString("\n") + "\nŽelite li ipak spremiti ovaj unos?"

@Composable
private fun ComplementaryFoodUnitSelector(
    selected: ComplementaryFoodUnit,
    onSelected: (ComplementaryFoodUnit) -> Unit,
) {
    val options = ComplementaryFoodUnit.entries
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val useWrappedLayout = maxWidth < 310.dp || LocalDensity.current.fontScale > 1.35f
        if (useWrappedLayout) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        label = { Text(ComplementaryFoodLogic.unitLabel(option), maxLines = 1) },
                        modifier =
                            Modifier
                                .widthIn(min = 88.dp)
                                .heightIn(min = 48.dp)
                                .testTag("food-unit-${option.name.lowercase()}"),
                    )
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        icon = {},
                        label = { Text(ComplementaryFoodLogic.unitLabel(option), maxLines = 1) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("food-unit-${option.name.lowercase()}"),
                    )
                }
            }
        }
    }
}
