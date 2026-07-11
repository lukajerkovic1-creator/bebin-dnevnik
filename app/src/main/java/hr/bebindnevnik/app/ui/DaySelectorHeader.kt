@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.data.DayStatus
import java.time.LocalDate

@Composable
internal fun DaySelectorHeader(
    selectedDate: LocalDate,
    status: DayStatus,
    onDateSelected: (LocalDate) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var showPicker by remember { mutableStateOf(false) }
    val isToday = selectedDate == today
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onDateSelected(selectedDate.minusDays(1)) },
                modifier = Modifier.size(48.dp).testTag("previous-day"),
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Prethodni dan")
            }
            OutlinedCard(
                onClick = { showPicker = true },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .testTag("day-selector")
                        .semantics {
                            contentDescription =
                                if (isToday) {
                                    "Odaberi datum. Danas, ${selectedDate.hrDate()}"
                                } else {
                                    "Odaberi datum. ${selectedDate.hrDate()}"
                                }
                        },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isToday) "Danas" else selectedDate.hrDate(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isToday) {
                            Text(
                                selectedDate.hrDate(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Otvori odabir datuma")
                }
            }
            IconButton(
                onClick = { onDateSelected(selectedDate.plusDays(1)) },
                enabled = selectedDate.isBefore(today),
                modifier = Modifier.size(48.dp).testTag("next-day"),
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Sljedeći dan")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!isToday) {
                TextButton(
                    onClick = { onDateSelected(today) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("go-today"),
                ) { Text("Idi na danas") }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!isToday) Spacer(Modifier.weight(1f))
            StatusBadge(status)
        }
    }
    if (showPicker) {
        AppDatePickerDialog(
            selectedDate = selectedDate,
            today = today,
            autoConfirmSelection = true,
            onConfirm = { date ->
                showPicker = false
                onDateSelected(date)
            },
            onDismiss = { showPicker = false },
        )
    }
}
