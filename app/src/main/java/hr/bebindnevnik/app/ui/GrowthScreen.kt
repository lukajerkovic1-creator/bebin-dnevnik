package hr.bebindnevnik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.bebindnevnik.app.data.ChildProfileEntity
import hr.bebindnevnik.app.data.GrowthMeasurementEntity
import hr.bebindnevnik.app.data.LengthMeasurementType
import hr.bebindnevnik.app.domain.growth.GrowthAssessment
import hr.bebindnevnik.app.domain.growth.GrowthCalculator
import hr.bebindnevnik.app.domain.growth.GrowthIndicator
import hr.bebindnevnik.app.domain.growth.GrowthReferenceSystem
import hr.bebindnevnik.app.domain.growth.birthMeasurement
import hr.bebindnevnik.app.domain.growth.calculateGrowthAges
import hr.bebindnevnik.app.domain.growth.crossedTwoMajorChannels
import hr.bebindnevnik.app.domain.growth.formatGrowthAge
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private enum class GrowthSection(
    val label: String,
) {
    OVERVIEW("Pregled"),
    CHARTS("Grafikoni"),
    HISTORY("Povijest"),
    PROFILE("Profil"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
internal fun GrowthScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val unlockedId by viewModel.growthUnlockedId.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(GrowthSection.OVERVIEW) }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }
    var showProfileWarning by rememberSaveable { mutableStateOf(false) }
    var showMeasurementEditor by rememberSaveable { mutableStateOf(false) }
    var editingMeasurement by remember { mutableStateOf<GrowthMeasurementEntity?>(null) }
    var pendingUnlock by remember { mutableStateOf<GrowthMeasurementEntity?>(null) }
    var pendingUnlockForDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GrowthMeasurementEntity?>(null) }
    var firstDeleteProfileWarning by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteProfile by rememberSaveable { mutableStateOf(false) }
    var showSources by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose(viewModel::lockGrowthMeasurement)
    }

    val profile = state.childProfile
    if (profile == null) {
        GrowthIntroduction(onCreateProfile = { showProfileEditor = true })
        if (showProfileEditor) {
            GrowthProfileEditor(
                existing = null,
                today = state.currentLocalDate,
                onDismiss = { showProfileEditor = false },
                onSave = {
                    viewModel.saveChildProfile(it)
                    showProfileEditor = false
                },
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        GrowthModuleHeader(profile, state.currentLocalDate)
        PrimaryScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 8.dp) {
            GrowthSection.entries.forEach { item ->
                Tab(
                    selected = section == item,
                    onClick = {
                        viewModel.lockGrowthMeasurement()
                        section = item
                    },
                    modifier = Modifier.testTag("growth-tab-${item.name.lowercase()}"),
                    text = { Text(item.label) },
                )
            }
        }
        if (showSources) {
            GrowthSourcesScreen(onBack = { showSources = false })
        } else {
            when (section) {
                GrowthSection.OVERVIEW -> {
                    GrowthOverview(
                        profile,
                        state.growthMeasurements,
                        state.currentLocalDate,
                        viewModel,
                        onAdd = {
                            editingMeasurement = null
                            showMeasurementEditor = true
                        },
                        onSources = { showSources = true },
                    )
                }

                GrowthSection.CHARTS -> {
                    GrowthChartsSection(profile, state.growthMeasurements, viewModel)
                }

                GrowthSection.HISTORY -> {
                    GrowthHistory(
                        profile,
                        state.growthMeasurements,
                        viewModel,
                        unlockedId,
                        onAdd = {
                            editingMeasurement = null
                            showMeasurementEditor = true
                        },
                        onEdit = { item ->
                            if (LocalDate.parse(item.date).isBefore(state.currentLocalDate) && unlockedId != item.id) {
                                pendingUnlock = item
                                pendingUnlockForDelete = false
                            } else {
                                editingMeasurement = item
                                showMeasurementEditor = true
                            }
                        },
                        onDelete = { item ->
                            if (LocalDate.parse(item.date).isBefore(state.currentLocalDate) && unlockedId != item.id) {
                                pendingUnlock = item
                                pendingUnlockForDelete = true
                            } else {
                                pendingDelete = item
                            }
                        },
                    )
                }

                GrowthSection.PROFILE -> {
                    GrowthProfile(
                        profile = profile,
                        state = state,
                        viewModel = viewModel,
                        onEdit = { showProfileWarning = true },
                        onDelete = { firstDeleteProfileWarning = true },
                        onSources = { showSources = true },
                    )
                }
            }
        }
    }

    if (showMeasurementEditor) {
        GrowthMeasurementEditor(
            profile = profile,
            existing = editingMeasurement,
            existingMeasurements = state.growthMeasurements,
            today = state.currentLocalDate,
            viewModel = viewModel,
            onDismiss = {
                showMeasurementEditor = false
                editingMeasurement = null
                viewModel.lockGrowthMeasurement()
            },
            onSave = {
                viewModel.saveGrowthMeasurement(it)
                showMeasurementEditor = false
                editingMeasurement = null
            },
        )
    }
    if (showProfileWarning) {
        AlertDialog(
            onDismissRequest = { showProfileWarning = false },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Uredi profil?") },
            text = {
                Text("Promjena spola, datuma rođenja ili gestacijske dobi ponovno će izračunati dob, percentile i z-vrijednosti svih mjerenja. Mjerenja se neće izbrisati.")
            },
            confirmButton = {
                Button(onClick = {
                    showProfileWarning = false
                    showProfileEditor = true
                }) { Text("Nastavi") }
            },
            dismissButton = { TextButton(onClick = { showProfileWarning = false }) { Text("Odustani") } },
        )
    }
    if (showProfileEditor) {
        GrowthProfileEditor(
            existing = profile,
            today = state.currentLocalDate,
            onDismiss = { showProfileEditor = false },
            onSave = {
                viewModel.saveChildProfile(it)
                showProfileEditor = false
            },
        )
    }
    pendingUnlock?.let { item ->
        AlertDialog(
            onDismissRequest = {
                pendingUnlock = null
                pendingUnlockForDelete = false
            },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Otključaj prošlo mjerenje?") },
            text = { Text("Prošla mjerenja zadano su samo za čitanje. Nakon izlaska iz zapisa ponovno će se zaključati.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.unlockGrowthMeasurement(item.id)
                    if (pendingUnlockForDelete) {
                        pendingDelete = item
                    } else {
                        editingMeasurement = item
                        showMeasurementEditor = true
                    }
                    pendingUnlock = null
                    pendingUnlockForDelete = false
                }) { Text("Otključaj") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingUnlock = null
                    pendingUnlockForDelete = false
                }) { Text("Odustani") }
            },
        )
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Izbriši mjerenje?") },
            text = { Text("Mjerenje će se izbrisati. Nakon brisanja možete kratko odabrati Poništi.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteGrowthMeasurement(item)
                    pendingDelete = null
                }) { Text("Izbriši") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Odustani") } },
        )
    }
    if (firstDeleteProfileWarning) {
        AlertDialog(
            onDismissRequest = { firstDeleteProfileWarning = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Izbriši profil i mjerenja rasta?") },
            text = { Text("Izbrisat će se samo profil, porođajne mjere i sva mjerenja rasta. Obroci i ostali podaci ostat će netaknuti.") },
            confirmButton = {
                Button(onClick = {
                    firstDeleteProfileWarning = false
                    confirmDeleteProfile = true
                }) { Text("Nastavi") }
            },
            dismissButton = { TextButton(onClick = { firstDeleteProfileWarning = false }) { Text("Odustani") } },
        )
    }
    if (confirmDeleteProfile) {
        var confirmation by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmDeleteProfile = false },
            title = { Text("Trajna potvrda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Za trajno brisanje upišite IZBRIŠI.")
                    OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Potvrda") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGrowthProfileAndMeasurements()
                        confirmDeleteProfile = false
                    },
                    enabled = confirmation == "IZBRIŠI",
                ) { Text("Izbriši") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteProfile = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun GrowthIntroduction(onCreateProfile: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GrowthIllustration(GrowthIllustrationKind.BABY_GROWTH, "Beba uz oznake rasta", Modifier.size(180.dp))
        Text("Praćenje rasta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Pratite težinu, duljinu ili visinu i opseg glave te usporedite mjerenja sa službenim referentnim podacima.")
        PrivacyLine("Profil i mjerenja čuvaju se u postojećoj šifriranoj lokalnoj bazi.")
        PrivacyLine("Ime se prikazuje samo unutar modula Rast.")
        PrivacyLine("Percentili nisu dijagnoza. Rezultate procijenite s pedijatrom.")
        Button(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("create-growth-profile")) {
            Text("Izradi profil djeteta")
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GrowthModuleHeader(
    profile: ChildProfileEntity,
    today: LocalDate,
) {
    val birth = LocalDate.parse(profile.birthDate)
    val ages = calculateGrowthAges(profile, today)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Kronološka dob: ${formatGrowthAge(birth, today)}", style = MaterialTheme.typography.bodyMedium)
        ages?.correctedDays?.let { Text("Korigirana dob: ${formatGrowthAge(birth, today, it)}", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun GrowthOverview(
    profile: ChildProfileEntity,
    measurements: List<GrowthMeasurementEntity>,
    today: LocalDate,
    viewModel: MainViewModel,
    onAdd: () -> Unit,
    onSources: () -> Unit,
) {
    val all = listOfNotNull(profile.birthMeasurement()) + measurements
    val assessed = all.map { it to viewModel.assessGrowth(profile, it) }.sortedWith(compareBy({ it.first.date }, { it.first.time }, { it.first.id }))
    val currentAges = calculateGrowthAges(profile, today)
    val reference =
        currentAges?.let {
            hr.bebindnevnik.app.domain.growth
                .referenceFor(profile, it)
                .first
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GrowthIllustration(GrowthIllustrationKind.BABY_GROWTH, "Beba uz oznake rasta", Modifier.size(74.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Aktivni sustav", style = MaterialTheme.typography.labelLarge)
                        Text(reference?.label() ?: "Nije dostupno", fontWeight = FontWeight.Bold)
                        if (reference == GrowthReferenceSystem.FENTON_2013_UNAVAILABLE) {
                            Text("Mjerenja se čuvaju, ali Fenton percentili nisu uključeni bez licence.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("add-growth-measurement")) {
                Icon(Icons.Default.Add, null)
                Text("Dodaj mjerenje", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { GrowthLatestCard("Težina", GrowthIllustrationKind.SCALE, GrowthIndicator.WEIGHT_FOR_AGE, assessed) }
        item { GrowthLatestCard("Duljina/visina", GrowthIllustrationKind.RULER, GrowthIndicator.LENGTH_HEIGHT_FOR_AGE, assessed) }
        item { GrowthLatestCard("Opseg glave", GrowthIllustrationKind.HEAD_TAPE, GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE, assessed) }
        item { TextButton(onClick = onSources, modifier = Modifier.fillMaxWidth()) { Text("Izvori podataka o rastu") } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun GrowthLatestCard(
    title: String,
    illustration: GrowthIllustrationKind,
    indicator: GrowthIndicator,
    assessed: List<Pair<GrowthMeasurementEntity, GrowthAssessment>>,
) {
    val comparable = assessed.mapNotNull { (measurement, assessment) -> assessment.metric(indicator)?.let { Triple(measurement, assessment, it) } }
    val latest = comparable.lastOrNull()
    val previous = comparable.dropLast(1).lastOrNull()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GrowthIllustration(illustration, title, Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (latest == null) {
                    Text("Još nema mjerenja.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val (measurement, assessment, metric) = latest
                    val value =
                        when (indicator) {
                            GrowthIndicator.WEIGHT_FOR_AGE -> "${measurement.weightG} g"
                            GrowthIndicator.LENGTH_HEIGHT_FOR_AGE -> "${"%.1f".format(Locale.forLanguageTag("hr-HR"), measurement.lengthHeightCm)} cm · ${if (measurement.lengthMeasurementType == LengthMeasurementType.LEZECA_DULJINA) "duljina" else "visina"}"
                            GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE -> "${"%.1f".format(Locale.forLanguageTag("hr-HR"), measurement.headCircumferenceCm)} cm"
                            GrowthIndicator.WEIGHT_FOR_LENGTH_HEIGHT -> ""
                        }
                    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${LocalDate.parse(measurement.date).growthDate()} · ${metric.percentileText()} · ${metric.zText()}", style = MaterialTheme.typography.bodySmall)
                    previous?.let { (oldMeasurement, _, oldMetric) ->
                        val delta = metric.rawValue - oldMetric.rawValue
                        val deltaText =
                            if (indicator == GrowthIndicator.WEIGHT_FOR_AGE) {
                                "Promjena: ${if (delta >= 0) "+" else ""}${delta.toInt()} g"
                            } else {
                                "Promjena: ${if (delta >= 0) "+" else ""}${"%.1f".format(Locale.forLanguageTag("hr-HR"), delta)} cm"
                            }
                        Text(deltaText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (
                            crossedTwoMajorChannels(
                                oldMetric,
                                metric,
                                LocalDate.parse(oldMeasurement.date),
                                LocalDate.parse(measurement.date),
                            )
                        ) {
                            Text(
                                "Promjena prelazi dvije glavne percentilne krivulje. Rezultat procijenite s pedijatrom.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (metric.isLow || metric.isHigh) GrowthDisclaimer()
                    if (metric.lengthCorrectionCm != 0.0) {
                        Text("Za izračun primijenjena korekcija ${if (metric.lengthCorrectionCm > 0) "+" else ""}${metric.lengthCorrectionCm} cm; izvorna mjera nije promijenjena.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!assessment.temporallyValid) Text(GrowthCalculator.OUTSIDE_RANGE, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GrowthHistory(
    profile: ChildProfileEntity,
    measurements: List<GrowthMeasurementEntity>,
    viewModel: MainViewModel,
    unlockedId: Long?,
    onAdd: () -> Unit,
    onEdit: (GrowthMeasurementEntity) -> Unit,
    onDelete: (GrowthMeasurementEntity) -> Unit,
) {
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val birth = profile.birthMeasurement()
    val sorted = measurements.sortedWith(compareBy<GrowthMeasurementEntity> { it.date }.thenBy { it.time }.thenBy { it.id }).let { if (newestFirst) it.reversed() else it }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Povijest mjerenja", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Najnovije prvo" else "Najstarije prvo") }
            }
        }
        item {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("Dodaj mjerenje", Modifier.padding(start = 8.dp))
            }
        }
        if (!newestFirst && birth != null) item { GrowthHistoryCard(profile, birth, viewModel.assessGrowth(profile, birth), true, false, {}, {}) }
        items(sorted, key = { it.id }) { item ->
            GrowthHistoryCard(profile, item, viewModel.assessGrowth(profile, item), false, unlockedId == item.id, { onEdit(item) }, { onDelete(item) })
        }
        if (newestFirst && birth != null) item { GrowthHistoryCard(profile, birth, viewModel.assessGrowth(profile, birth), true, false, {}, {}) }
        if (sorted.isEmpty() && birth == null) item { Text("Još nema mjerenja.", modifier = Modifier.padding(20.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GrowthHistoryCard(
    profile: ChildProfileEntity,
    item: GrowthMeasurementEntity,
    assessment: GrowthAssessment,
    atBirth: Boolean,
    unlocked: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = LocalDate.parse(item.date)
    val birthDate = LocalDate.parse(profile.birthDate)
    Card(Modifier.fillMaxWidth().testTag("growth-history-${item.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (atBirth) "Pri rođenju" else date.growthDate(), fontWeight = FontWeight.Bold)
                    Text(if (atBirth) "Porođajne mjere" else "${LocalTime.parse(item.time).growthTime()} · ${formatGrowthAge(birthDate, date)}", style = MaterialTheme.typography.bodySmall)
                }
                if (!atBirth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock, if (unlocked) "Način uređivanja" else "Zaključano")
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Uredi mjerenje") }
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Izbriši mjerenje") }
                    }
                }
            }
            if (unlocked) Text("Način uređivanja", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            item.weightG?.let { GrowthHistoryMetric("Težina", "$it g", assessment.metric(GrowthIndicator.WEIGHT_FOR_AGE)) }
            item.lengthHeightCm?.let {
                GrowthHistoryMetric(
                    if (item.lengthMeasurementType == LengthMeasurementType.LEZECA_DULJINA) "Ležeća duljina" else "Stojeća visina",
                    "${"%.1f".format(Locale.forLanguageTag("hr-HR"), it)} cm",
                    assessment.metric(GrowthIndicator.LENGTH_HEIGHT_FOR_AGE),
                )
            }
            item.headCircumferenceCm?.let { GrowthHistoryMetric("Opseg glave", "${"%.1f".format(Locale.forLanguageTag("hr-HR"), it)} cm", assessment.metric(GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE)) }
            Text("${assessment.referenceSystem.label()} · ${assessment.ageBasis.label()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GrowthHistoryMetric(
    label: String,
    value: String,
    metric: hr.bebindnevnik.app.domain.growth.GrowthMetricResult?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$label: $value")
        Text(metric?.let { "${it.percentileText()} · ${it.zText()}" } ?: "Nema izračuna", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GrowthProfile(
    profile: ChildProfileEntity,
    state: UiState,
    viewModel: MainViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSources: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, "Profil je zaključan")
                Text("Profil je zaključan", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null)
                Text("Uredi profil", Modifier.padding(start = 6.dp))
            }
        }
        ProfileValue("Ime", profile.name)
        ProfileValue("Spol", if (profile.sex.name == "DJEVOJCICA") "Djevojčica" else "Dječak")
        ProfileValue("Datum rođenja", LocalDate.parse(profile.birthDate).growthDate())
        ProfileValue("Gestacijska dob", "${profile.gestationalWeeks}+${profile.gestationalDays}")
        ProfileValue("Porođajna težina", profile.birthWeightG?.let { "$it g" } ?: "Nije uneseno")
        ProfileValue("Porođajna duljina", profile.birthLengthCm?.let { "${"%.1f".format(Locale.forLanguageTag("hr-HR"), it)} cm" } ?: "Nije uneseno")
        ProfileValue("Porođajni opseg glave", profile.birthHeadCircumferenceCm?.let { "${"%.1f".format(Locale.forLanguageTag("hr-HR"), it)} cm" } ?: "Nije uneseno")
        Text("Spol se koristi samo za odabir odgovarajućih WHO/Fenton referentnih podataka.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onSources) { Text("Izvori podataka o rastu") }
        GuidelineProfileSettings(state, viewModel)
        HorizontalDivider()
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Delete, null)
            Text("Izbriši profil i mjerenja rasta", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProfileValue(
    label: String,
    value: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun GrowthDisclaimer() {
    Text("Ovo nije dijagnoza. Rezultat procijenite s pedijatrom.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun GrowthSourcesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Natrag") }
        Text("Izvori podataka o rastu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SourceCard(
            "WHO Child Growth Standards",
            "WHO 2006 / službeni WHO paket anthro 1.1.0.9000",
            "Svjetska zdravstvena organizacija (WHO)",
            "Rođenje do 5 godina: težina/dob, duljina-visina/dob, opseg glave/dob i težina/duljina-visina.",
            "Numeričke LMS tablice uključene su lokalno iz WHO-ova repozitorija. Izračun koristi službenu LMS metodu i pravilo ±0,7 cm.",
            "GNU GPL v3 · uključeno 16.07.2026.",
        )
        SourceCard(
            "Fenton preterm growth charts",
            "Fenton 2013 – podaci nisu ugrađeni",
            "Tanis R. Fenton i Jae H. Kim",
            "22–50 postmenstrualnih tjedana za nedonoščad.",
            "LMS parametri nisu javno dopušteni za slobodnu redistribuciju. Aplikacija zato ne procjenjuje ni ekstrapolira Fenton percentile; mjerenja ostaju sigurno spremljena.",
            "Članak CC BY 2.0; numerički LMS podaci nisu licencirani za redistribuciju · provjereno 16.07.2026.",
        )
        Text("Podaci o izvorima, licencama i SHA-256 hash vrijednostima ugrađenih tablica nalaze se u dokumentaciji repozitorija.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SourceCard(
    title: String,
    version: String,
    author: String,
    range: String,
    method: String,
    license: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(version)
            Text(author, style = MaterialTheme.typography.bodySmall)
            Text(range, style = MaterialTheme.typography.bodySmall)
            Text(method, style = MaterialTheme.typography.bodySmall)
            Text(license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
