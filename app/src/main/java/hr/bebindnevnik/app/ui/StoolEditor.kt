@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.domain.EntryWarning
import java.time.LocalDate

@Composable
internal fun StoolEditorSheet(
    initialCount: Int?,
    date: LocalDate,
    onWarnings: (Int) -> Set<EntryWarning>,
    onSave: (Int?) -> Unit,
    onClose: () -> Unit,
) {
    var count by remember(date, initialCount) { mutableStateOf(initialCount) }
    var confirmHighValue by remember { mutableStateOf(false) }

    fun attemptSave(confirmed: Boolean = false) {
        val value = count
        if (value != null && EntryWarning.HIGH_STOOL_COUNT in onWarnings(value) && !confirmed) {
            confirmHighValue = true
        } else {
            onSave(value)
            onClose()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("stool-editor"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evidentiraj stolicu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(date.hrDate(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BabyIllustration(BabyIllustrationKind.STOOL, Modifier.size(BabyDimensions.IllustrationSmall))
            }
            Text("Brzi odabir", style = MaterialTheme.typography.titleMedium)
            listOf(listOf(0, 1, 2), listOf(3, 4, 5)).forEach { values ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    values.forEach { value ->
                        FilterChip(
                            selected = count == value,
                            onClick = { count = value },
                            label = { Text(value.toString()) },
                            leadingIcon = if (count == value) ({ Icon(Icons.Default.Check, "Odabrano") }) else null,
                            modifier = Modifier.weight(1f).heightIn(min = BabyDimensions.TouchTarget).testTag("stool-$value"),
                        )
                    }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = { count = ((count ?: 0) - 1).coerceAtLeast(0) },
                        enabled = (count ?: 0) > 0,
                        modifier = Modifier.semantics { contentDescription = "Smanji broj stolica" }.testTag("stool-minus"),
                    ) { Icon(Icons.Default.Remove, contentDescription = null) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Broj stolica", style = MaterialTheme.typography.labelMedium)
                        Text(
                            count?.toString() ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("stool-count"),
                        )
                    }
                    IconButton(
                        onClick = { count = ((count ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
                        modifier = Modifier.semantics { contentDescription = "Povećaj broj stolica" }.testTag("stool-plus"),
                    ) { Icon(Icons.Default.Add, contentDescription = null) }
                }
            }
            FilterChip(
                selected = count == null,
                onClick = { count = null },
                label = { Text("Nije evidentirano") },
                leadingIcon = if (count == null) ({ Icon(Icons.Default.Check, "Odabrano") }) else null,
                modifier = Modifier.fillMaxWidth().heightIn(min = BabyDimensions.TouchTarget).testTag("stool-unset"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = BabyDimensions.TouchTarget)) {
                    Text("Odustani")
                }
                Button(
                    onClick = { attemptSave() },
                    enabled = !date.isAfter(LocalDate.now()),
                    modifier = Modifier.weight(1f).heightIn(min = BabyDimensions.TouchTarget).testTag("save-stool"),
                ) { Text("Spremi") }
            }
        }
    }
    if (confirmHighValue) {
        AlertDialog(
            onDismissRequest = { confirmHighValue = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Potvrdite visoku vrijednost") },
            text = { Text("Uneseno je ${count ?: 0} stolica za jedan dan. Želite li ipak spremiti taj podatak?") },
            confirmButton = {
                Button(onClick = {
                    confirmHighValue = false
                    attemptSave(true)
                }) { Text("Ipak spremi") }
            },
            dismissButton = { TextButton(onClick = { confirmHighValue = false }) { Text("Ispravi") } },
        )
    }
}

internal fun stoolCountText(count: Int?): String {
    if (count == null) return "Nije evidentirano"
    val noun =
        when {
            count % 100 in 12..14 -> "stolica"
            count % 10 in 2..4 -> "stolice"
            else -> "stolica"
        }
    return "$count $noun"
}
