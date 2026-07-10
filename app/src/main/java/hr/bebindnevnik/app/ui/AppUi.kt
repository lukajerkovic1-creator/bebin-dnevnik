@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import hr.bebindnevnik.app.BuildConfig
import hr.bebindnevnik.app.backup.BackupManager
import hr.bebindnevnik.app.backup.BackupPreview
import hr.bebindnevnik.app.backup.CsvExporter
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.DaySummary
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummySessionEntity
import hr.bebindnevnik.app.domain.AppLogic
import hr.bebindnevnik.app.domain.EntryWarning
import hr.bebindnevnik.app.domain.StatisticsRange
import hr.bebindnevnik.app.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems =
    listOf(
        NavItem("today", "Danas", Icons.Default.Home),
        NavItem("calendar", "Kalendar", Icons.Default.CalendarMonth),
        NavItem("statistics", "Statistika", Icons.Default.BarChart),
        NavItem("settings", "Postavke", Icons.Default.Settings),
    )

@Composable
fun BebinDnevnikApp(
    viewModel: MainViewModel,
    notifications: NotificationHelper,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    var undoMessage by remember { mutableStateOf<UiMessage?>(null) }
    var notificationExplanation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val action =
                when (message) {
                    is UiMessage.MealDeleted, is UiMessage.TummyDeleted -> "Poništi"
                    is UiMessage.Text -> null
                }
            undoMessage = message
            val text =
                when (message) {
                    is UiMessage.Text -> message.value
                    else -> "Zapis je izbrisan."
                }
            val result = snackbar.showSnackbar(text, action)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undo(message)
            undoMessage = null
        }
    }

    if (!state.settings.onboardingShown) {
        Onboarding(
            onFinish = {
                viewModel.finishOnboarding()
                if (Build.VERSION.SDK_INT >= 33) notificationExplanation = true
            },
        )
        return
    }
    if (notificationExplanation) {
        AlertDialog(
            onDismissRequest = { notificationExplanation = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Dopusti podsjetnike?") },
            text = {
                Text(
                    "Obavijest jednom dnevno podsjeća samo na stavke koje još nisu evidentirane. Aplikacija radi i bez ove dozvole.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    notificationExplanation = false
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Nastavi") }
            },
            dismissButton = { TextButton(onClick = { notificationExplanation = false }) { Text("Ne sada") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { CenterAlignedTopAppBar(title = { Text("Bebin dnevnik") }) },
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = backStack?.destination?.route == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, "today", Modifier.padding(padding)) {
            composable("today") {
                TodayScreen(state, timer, highlight, viewModel, notifications) { navController.navigate("settings") }
            }
            composable("calendar") {
                CalendarScreen(state, viewModel) { date ->
                    viewModel.selectDate(date)
                    navController.navigate("today")
                }
            }
            composable("statistics") { StatisticsScreen(state) }
            composable("settings") {
                SettingsScreen(state, viewModel, notifications, onExplainPermission = { notificationExplanation = true })
            }
        }
    }
}

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("Dobro došli u Bebin dnevnik", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        listOf(
            "Evidentirajte obroke, Waya kapi, vježbanje i tummy time.",
            "Nema profila djeteta ni identifikacijskih podataka.",
            "Svi podaci ostaju samo na ovom uređaju; aplikacija nema pristup internetu.",
            "Deinstalacijom ili gubitkom uređaja podaci se gube ako prije toga ne izvezete sigurnosnu kopiju.",
            "Dnevni podsjetnik je neobvezan i može se isključiti u Postavkama.",
        ).forEach { Text("• $it", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Započni") }
    }
}

@Composable
private fun TodayScreen(
    state: UiState,
    timer: hr.bebindnevnik.app.notifications.TimerState,
    highlight: Set<String>,
    viewModel: MainViewModel,
    notifications: NotificationHelper,
    openSettings: () -> Unit,
) {
    var mealDialog by remember { mutableStateOf<MealEntity?>(null) }
    var newMeal by remember { mutableStateOf(false) }
    var tummyDialog by remember { mutableStateOf<TummySessionEntity?>(null) }
    var newTummy by remember { mutableStateOf(false) }
    var deleteMeal by remember { mutableStateOf<MealEntity?>(null) }
    var deleteTummy by remember { mutableStateOf<TummySessionEntity?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    val isToday = state.selectedDate == LocalDate.now()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        if (isToday) "Danas" else state.selectedDate.hrDate(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isToday) TextButton(onClick = { viewModel.selectDate(LocalDate.now()) }) { Text("Vrati se na danas") }
                }
                StatusBadge(state.summary.status)
            }
        }
        if (!notifications.notificationsAllowed()) item { NotificationWarning(openSettings) }
        item {
            HighlightCard("obrok" in highlight) {
                SectionTitle("Posljednji obrok")
                val last = state.selectedMeals.maxByOrNull { it.time }
                if (last == null) {
                    Text("Nije evidentirano")
                } else {
                    Text("${last.time.hrStoredTime()} · ${last.amountMl} ml", style = MaterialTheme.typography.headlineSmall)
                    Text(AppLogic.elapsedText(LocalDate.parse(last.date), LocalTime.parse(last.time)))
                }
            }
        }
        item {
            HighlightCard("obrok" in highlight) {
                SectionTitle("Dodavanje obroka")
                Button(onClick = { newMeal = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dodaj obrok")
                }
                state.selectedMeals.sortedByDescending { it.time }.forEach { meal ->
                    EntryRow("${meal.time.hrStoredTime()} · ${meal.amountMl} ml", { mealDialog = meal }, { deleteMeal = meal })
                }
            }
        }
        item { StatusCard("Waya kapi", "Waya kapi" in highlight, state.summary.waya, viewModel::setWaya) }
        item { StatusCard("Vježbanje", "vježbanje" in highlight, state.summary.exercise, viewModel::setExercise) }
        item {
            HighlightCard("tummy time" in highlight, Modifier.testTag("tummy-card")) {
                SectionTitle("Tummy time")
                Text("Ukupno danas: ${state.summary.tummySeconds.durationText()} · ${state.summary.tummyCount} sesija")
                if (isToday) {
                    Text(timer.elapsedSeconds.durationText(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    if (timer.running) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = viewModel::stopTimer, modifier = Modifier.weight(1f).height(56.dp).testTag("timer-stop")) {
                                Icon(Icons.Default.Stop, null)
                                Text(" Zaustavi")
                            }
                            OutlinedButton(
                                onClick = viewModel::cancelTimer,
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Poništi") }
                        }
                    } else {
                        Button(onClick = viewModel::startTimer, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("timer-start")) {
                            Icon(Icons.Default.PlayArrow, null)
                            Text(" Pokreni")
                        }
                    }
                }
                OutlinedButton(onClick = { newTummy = true }, modifier = Modifier.fillMaxWidth()) { Text("Ručni unos sesije") }
                if (state.selectedSessions.isEmpty() && !state.summary.noTummyTime) {
                    Text("Nije evidentirano", modifier = Modifier.padding(vertical = 6.dp))
                    TextButton(
                        onClick = viewModel::markNoTummy,
                    ) { Text(if (isToday) "Danas nije bilo tummy timea" else "Nije bilo tummy timea") }
                } else if (state.summary.noTummyTime) {
                    Text("Izričito evidentirano: nije bilo tummy timea")
                }
                state.selectedSessions.sortedByDescending { it.time }.forEach { session ->
                    EntryRow("${session.time.hrStoredTime()} · ${session.durationSeconds.durationText()}", { tummyDialog = session }, {
                        deleteTummy =
                            session
                    })
                }
            }
        }
        item {
            SummaryCard(state.summary)
            TextButton(onClick = { resetConfirm = true }) { Text("Resetiraj dnevne statuse") }
        }
    }
    if (newMeal ||
        mealDialog != null
    ) {
        MealEditor(mealDialog, state.selectedDate, viewModel, onClose = {
            newMeal = false
            mealDialog = null
        })
    }
    if (newTummy ||
        tummyDialog != null
    ) {
        TummyEditor(tummyDialog, state.selectedDate, viewModel, onClose = {
            newTummy = false
            tummyDialog = null
        })
    }
    deleteMeal?.let { item ->
        ConfirmDelete("Obrisati ovaj obrok?", {
            viewModel.deleteMeal(item)
            deleteMeal = null
        }, { deleteMeal = null })
    }
    deleteTummy?.let { item ->
        ConfirmDelete("Obrisati ovu tummy-time sesiju?", {
            viewModel.deleteTummy(item)
            deleteTummy = null
        }, {
            deleteTummy =
                null
        })
    }
    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("Resetirati dnevne statuse?") },
            text = { Text("Waya kapi i vježbanje vratit će se na „Nije evidentirano”. Obroci i tummy-time sesije neće se izbrisati.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetStatuses()
                    resetConfirm = false
                }) { Text("Resetiraj") }
            },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun HighlightCard(
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val color by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "isticanje",
    )
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun StatusCard(
    title: String,
    highlighted: Boolean,
    status: TernaryStatus,
    set: (TernaryStatus) -> Unit,
) = HighlightCard(highlighted) {
    SectionTitle(title)
    Text("Trenutačno: ${status.label()}")
    val unset = TernaryStatus.NIJE_EVIDENTIRANO
    FilterChip(
        selected = status == unset,
        onClick = { set(unset) },
        label = { Text(unset.label()) },
        leadingIcon = if (status == unset) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(TernaryStatus.DA, TernaryStatus.NE).forEach { value ->
            FilterChip(
                selected = status == value,
                onClick = { set(value) },
                label = { Text(value.label()) },
                leadingIcon = if (status == value) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EntryRow(
    text: String,
    edit: () -> Unit,
    delete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        IconButton(onClick = edit, modifier = Modifier.semantics { contentDescription = "Uredi zapis" }) { Icon(Icons.Default.Edit, null) }
        IconButton(
            onClick = delete,
            modifier = Modifier.semantics { contentDescription = "Izbriši zapis" },
        ) { Icon(Icons.Default.Delete, null) }
    }
}

@Composable
private fun SummaryCard(summary: DaySummary) =
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Dnevni sažetak")
            Text("Ukupno: ${summary.totalMl} ml")
            Text("Broj obroka: ${summary.mealCount}")
            Text("Prosječno: ${"%.1f".format(summary.averageMl)} ml")
            Text("Posljednji obrok: ${summary.lastMealTime?.hrStoredTime() ?: "Nije evidentirano"}")
            Text("Waya kapi: ${summary.waya.label()}")
            Text("Vježbanje: ${summary.exercise.label()}")
            Text("Tummy time: ${summary.tummySeconds.durationText()} (${summary.tummyCount} sesija)")
            Text("Status dana: ${dayStatusLabel(summary.status)}", fontWeight = FontWeight.Bold)
        }
    }

@Composable
private fun StatusBadge(status: DayStatus) {
    val color =
        when (status) {
            DayStatus.POTPUNO -> Color(0xFF2E7D32)
            DayStatus.DJELOMICNO -> Color(0xFFE07900)
            DayStatus.BEZ_PODATAKA -> MaterialTheme.colorScheme.outline
        }
    AssistChip(onClick = {}, label = { Text(dayStatusLabel(status)) }, leadingIcon = {
        Icon(
            if (status ==
                DayStatus.POTPUNO
            ) {
                Icons.Default.Check
            } else {
                Icons.Default.Warning
            },
            null,
            tint = color,
        )
    })
}

private fun dayStatusLabel(status: DayStatus) =
    when (status) {
        DayStatus.POTPUNO -> "Potpuno"
        DayStatus.DJELOMICNO -> "Djelomično"
        DayStatus.BEZ_PODATAKA -> "Bez podataka"
    }

@Composable
private fun NotificationWarning(openSettingsScreen: () -> Unit) =
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Podsjetnici nisu omogućeni", fontWeight = FontWeight.Bold)
                Text("Aplikacija i dalje normalno radi.")
            }
            TextButton(onClick = openSettingsScreen) { Text("Postavke") }
        }
    }

@Composable
private fun MealEditor(
    item: MealEntity?,
    selectedDate: LocalDate,
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    var amount by remember { mutableStateOf(item?.amountMl?.toString().orEmpty()) }
    var dateText by remember { mutableStateOf((item?.date?.let(LocalDate::parse) ?: selectedDate).hrDate()) }
    var timeText by remember { mutableStateOf((item?.time?.let(LocalTime::parse) ?: LocalTime.now()).hrTime()) }
    var error by remember { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf<Set<EntryWarning>>(emptySet()) }

    fun attemptSave(confirmed: Boolean = false) {
        try {
            val parsedAmount = amount.toInt()
            val date = LocalDate.parse(dateText, CroatianDateFormatter)
            val time = LocalTime.parse(timeText, CroatianTimeFormatter)
            val found = viewModel.mealWarnings(parsedAmount, date, time, item?.id ?: 0)
            if (found.isNotEmpty() &&
                !confirmed
            ) {
                warnings = found
            } else {
                viewModel.saveMeal(item?.id ?: 0, date, time, parsedAmount)
                onClose()
            }
        } catch (e: Exception) {
            error = e.message ?: "Provjerite datum, vrijeme i količinu."
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (item == null) "Novi obrok" else "Uredi obrok") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(40, 80, 120, 160).forEach { ml ->
                        AssistChip(onClick = {
                            amount =
                                ml.toString()
                        }, label = { Text("$ml ml") })
                    }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Količina (ml)") }, singleLine = true)
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Datum (dd.MM.yyyy.)") }, singleLine = true)
                OutlinedTextField(timeText, { timeText = it }, label = { Text("Vrijeme (HH:mm)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { attemptSave() }) { Text("Spremi") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Odustani") } },
    )
    if (warnings.isNotEmpty()) {
        WarningDialog(warnings.joinToString("\n") { warningLabel(it) }, { attemptSave(true) }, {
            warnings =
                emptySet()
        })
    }
}

@Composable
private fun TummyEditor(
    item: TummySessionEntity?,
    selectedDate: LocalDate,
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    var minutes by remember { mutableStateOf(((item?.durationSeconds ?: 0) / 60).toString()) }
    var seconds by remember { mutableStateOf(((item?.durationSeconds ?: 0) % 60).toString()) }
    var dateText by remember { mutableStateOf((item?.date?.let(LocalDate::parse) ?: selectedDate).hrDate()) }
    var timeText by remember { mutableStateOf((item?.time?.let(LocalTime::parse) ?: LocalTime.now()).hrTime()) }
    var error by remember { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf<Set<EntryWarning>>(emptySet()) }

    fun attemptSave(confirmed: Boolean = false) {
        try {
            val duration = minutes.toLong() * 60 + seconds.toLong()
            val date = LocalDate.parse(dateText, CroatianDateFormatter)
            val time = LocalTime.parse(timeText, CroatianTimeFormatter)
            val found = viewModel.tummyWarnings(duration, date, time)
            if (found.isNotEmpty() &&
                !confirmed
            ) {
                warnings = found
            } else {
                viewModel.saveTummy(item?.id ?: 0, date, time, duration)
                onClose()
            }
        } catch (e: Exception) {
            error = e.message ?: "Provjerite unesene vrijednosti."
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (item == null) "Ručni unos tummy timea" else "Uredi tummy time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minutes, {
                        minutes = it.filter(Char::isDigit)
                    }, label = { Text("Minute") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(seconds, {
                        seconds = it.filter(Char::isDigit)
                    }, label = { Text("Sekunde") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Datum (dd.MM.yyyy.)") }, singleLine = true)
                OutlinedTextField(timeText, { timeText = it }, label = { Text("Vrijeme (HH:mm)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { attemptSave() }) { Text("Spremi") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Odustani") } },
    )
    if (warnings.isNotEmpty()) {
        WarningDialog(warnings.joinToString("\n") { warningLabel(it) }, { attemptSave(true) }, {
            warnings =
                emptySet()
        })
    }
}

@Composable private fun WarningDialog(
    text: String,
    confirm: () -> Unit,
    dismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = dismiss,
    icon = { Icon(Icons.Default.Warning, null) },
    title = { Text("Potrebna je potvrda") },
    text = { Text(text) },
    confirmButton = {
        Button(onClick = confirm) { Text("Ipak spremi") }
    },
    dismissButton = { TextButton(onClick = dismiss) { Text("Ispravi unos") } },
)

private fun warningLabel(warning: EntryWarning) =
    when (warning) {
        EntryWarning.ZERO_ML -> "Količina je 0 ml."
        EntryWarning.OVER_500_ML -> "Količina je veća od 500 ml."
        EntryWarning.DUPLICATE_TIME -> "Već postoji obrok s potpuno jednakim datumom i vremenom."
        EntryWarning.UNDER_5_SECONDS -> "Sesija je kraća od 5 sekundi."
        EntryWarning.OVER_60_MINUTES -> "Sesija je dulja od 60 minuta."
    }

@Composable private fun ConfirmDelete(
    title: String,
    confirm: () -> Unit,
    dismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text(title) },
    text = { Text("Brisanje možete kratko poništiti nakon potvrde.") },
    confirmButton = { Button(onClick = confirm) { Text("Izbriši") } },
    dismissButton = { TextButton(onClick = dismiss) { Text("Odustani") } },
)

@Composable
private fun CalendarScreen(
    state: UiState,
    viewModel: MainViewModel,
    select: (LocalDate) -> Unit,
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val shown = YearMonth.parse(month)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = shown.minusMonths(1).toString() }) { Text("‹ Prethodni") }
            Text(
                "${shown.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("hr"))} ${shown.year}",
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = {
                if (shown <
                    YearMonth.now()
                ) {
                    month = shown.plusMonths(1).toString()
                }
            }, enabled = shown < YearMonth.now()) { Text("Sljedeći ›") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned").forEach {
                Text(it, Modifier.weight(1f).padding(4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        val offset = shown.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = List(offset) { null } + (1..shown.lengthOfMonth()).map(shown::atDay)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { null }).forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(64.dp))
                    } else {
                        val disabled = date.isAfter(LocalDate.now())
                        val summary = AppLogic.summary(date, state.meals, state.entries, state.sessions)
                        val symbol =
                            when (summary.status) {
                                DayStatus.POTPUNO -> "✓"
                                DayStatus.DJELOMICNO -> "◐"
                                DayStatus.BEZ_PODATAKA -> "—"
                            }
                        OutlinedCard(
                            Modifier.weight(1f).height(64.dp).padding(2.dp).clickable(enabled = !disabled) {
                                viewModel.selectDate(date)
                                select(date)
                            },
                            colors =
                                CardDefaults.outlinedCardColors(
                                    containerColor =
                                        if (disabled) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = .35f,
                                            )
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                ),
                        ) {
                            Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfMonth.toString())
                                Text(symbol, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("✓ potpuno · ◐ djelomično · — bez podataka")
    }
}

private enum class StatsPeriod { SEVEN, THIRTY, ALL }

@Composable
private fun StatisticsScreen(state: UiState) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.SEVEN) }
    val today = LocalDate.now()
    val firstData =
        (
            state.meals.map { LocalDate.parse(it.date) } + state.sessions.map { LocalDate.parse(it.date) } +
                state.entries.map { LocalDate.parse(it.date) }
        ).minOrNull()
            ?: today
    val summaries =
        AppLogic.statistics(
            when (period) {
                StatsPeriod.SEVEN -> StatisticsRange.SEVEN_DAYS
                StatsPeriod.THIRTY -> StatisticsRange.THIRTY_DAYS
                StatsPeriod.ALL -> StatisticsRange.ALL
            },
            today,
            firstData,
            state.meals,
            state.entries,
            state.sessions,
        )
    val start = LocalDate.parse(summaries.first().date)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("Statistika")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsPeriod.entries.forEach { value ->
                    FilterChip(period == value, { period = value }, {
                        Text(
                            when (value) {
                                StatsPeriod.SEVEN -> "7 dana"
                                StatsPeriod.THIRTY -> "30 dana"
                                StatsPeriod.ALL -> "Sve"
                            },
                        )
                    })
                }
            }
        }
        item {
            val totalMl = summaries.sumOf { it.totalMl }
            val mealCount = summaries.sumOf { it.mealCount }
            val tummy = summaries.sumOf { it.tummySeconds }
            val complete = summaries.count { it.status == DayStatus.POTPUNO }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Razdoblje: ${start.hrDate()} – ${today.hrDate()}")
                    Text("Ukupno ml: $totalMl ml")
                    Text("Broj obroka: $mealCount")
                    Text("Prosječno po obroku: ${if (mealCount == 0) "0,0" else "%.1f".format(totalMl.toDouble() / mealCount)} ml")
                    Text("Tummy time: ${(tummy / 60)} min · ${summaries.sumOf { it.tummyCount }} sesija")
                    Text("Potpuni dani: $complete/${summaries.size} (${if (summaries.isEmpty()) 0 else complete * 100 / summaries.size}%)")
                }
            }
        }
        item {
            BarChart(
                "Ukupno ml po danu",
                summaries.map { it.totalMl.toFloat() },
                "Najviše ${summaries.maxOfOrNull { it.totalMl } ?: 0} ml",
            )
        }
        item {
            BarChart(
                "Broj obroka po danu",
                summaries.map { it.mealCount.toFloat() },
                "Ukupno ${summaries.sumOf { it.mealCount }} obroka",
            )
        }
        item { BarChart("Prosječna količina po obroku", summaries.map { it.averageMl.toFloat() }, "Prosjek razdoblja prikazan je iznad") }
        item { BarChart("Tummy time po danu", summaries.map { (it.tummySeconds / 60).toFloat() }, "Vrijednosti su cijele minute") }
        item {
            BarChart(
                "Broj tummy-time sesija",
                summaries.map { it.tummyCount.toFloat() },
                "Ukupno ${summaries.sumOf { it.tummyCount }} sesija",
            )
        }
        item {
            val waya = TernaryStatus.entries.associateWith { status -> summaries.count { it.waya == status } }
            val exercise = TernaryStatus.entries.associateWith { status -> summaries.count { it.exercise == status } }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Waya kapi", fontWeight = FontWeight.Bold)
                    TernaryStatus.entries.forEach { Text("${it.label()}: ${waya[it]}") }
                    Spacer(Modifier.height(8.dp))
                    Text("Vježbanje", fontWeight = FontWeight.Bold)
                    TernaryStatus.entries.forEach { Text("${it.label()}: ${exercise[it]}") }
                }
            }
        }
    }
}

@Composable
private fun BarChart(
    title: String,
    values: List<Float>,
    summary: String,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (values.all { it == 0f }) {
                Text("Nema podataka za odabrano razdoblje.")
            } else {
                val barColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = "$title. $summary" }) {
                    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                    val visible =
                        if (values.size >
                            90
                        ) {
                            values.chunked((values.size / 90) + 1).map { chunk -> chunk.average().toFloat() }
                        } else {
                            values
                        }
                    val step = size.width / visible.size.coerceAtLeast(1)
                    visible.forEachIndexed { index, value ->
                        drawRect(
                            barColor,
                            topLeft =
                                androidx.compose.ui.geometry.Offset(
                                    index * step,
                                    size.height * (1 - value / max),
                                ),
                            size =
                                androidx.compose.ui.geometry.Size(
                                    (step - 2).coerceAtLeast(1f),
                                    size.height * value / max,
                                ),
                        )
                    }
                }
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    viewModel: MainViewModel,
    notifications: NotificationHelper,
    onExplainPermission: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPassword by remember { mutableStateOf(false) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var csvWarning by remember { mutableStateOf(false) }
    var deleteAll by remember { mutableStateOf(false) }
    var reminderTime by remember(state.settings.reminderTime) { mutableStateOf(LocalTime.parse(state.settings.reminderTime)) }
    val backupCreate =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val bytes = exportBytes
            if (uri != null &&
                bytes != null
            ) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            exportBytes = null
        }
    val csvCreate =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri !=
                null
            ) {
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { CsvExporter.createZip(viewModel.snapshot()) }
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
            }
        }
    val backupOpen =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri !=
                null
            ) {
                importUri = uri
                importPassword = true
            }
        }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Postavke") }
        item {
            SettingsCard("Podsjetnik") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dnevni podsjetnik", Modifier.weight(1f))
                    Switch(state.settings.reminderEnabled, viewModel::setReminder)
                }
                TextButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute ->
                        reminderTime = LocalTime.of(hour, minute)
                        viewModel.setReminderTime(reminderTime)
                    }, reminderTime.hour, reminderTime.minute, true).show()
                }) { Text("Vrijeme: ${reminderTime.hrTime()}") }
                if (!notifications.notificationsAllowed()) {
                    Text("Podsjetnici nisu omogućeni", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = onExplainPermission) { Text("Zatraži dozvolu") }
                        TextButton(onClick = { openNotificationSettings(context) }) { Text("Android postavke") }
                    }
                } else {
                    Text("Dozvola za obavijesti je uključena.")
                }
            }
        }
        item {
            SettingsCard("Tema") {
                AppTheme.entries.forEach { theme ->
                    Row(Modifier.fillMaxWidth().clickable { viewModel.setTheme(theme) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.settings.theme == theme, { viewModel.setTheme(theme) })
                        Text(
                            when (theme) {
                                AppTheme.SUSTAV -> "Sustav"
                                AppTheme.SVIJETLA -> "Svijetla"
                                AppTheme.TAMNA -> "Tamna"
                            },
                        )
                    }
                }
            }
        }
        item {
            SettingsCard("Podaci i sigurnosne kopije") {
                Button(onClick = { exportPassword = true }, Modifier.fillMaxWidth()) { Text("Izvezi sigurnosnu kopiju") }
                OutlinedButton(onClick = {
                    backupOpen.launch(arrayOf("application/octet-stream", "application/*"))
                }, Modifier.fillMaxWidth()) { Text("Uvezi sigurnosnu kopiju") }
                OutlinedButton(onClick = { csvWarning = true }, Modifier.fillMaxWidth()) { Text("Izvezi CSV ZIP") }
                TextButton(
                    onClick = { deleteAll = true },
                    Modifier.fillMaxWidth(),
                ) { Text("Izbriši sve podatke", color = MaterialTheme.colorScheme.error) }
            }
        }
        item { Text("Verzija aplikacije: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall) }
    }
    if (exportPassword) {
        PasswordDialog("Nova lozinka sigurnosne kopije", repeat = true, onDismiss = { exportPassword = false }) { password ->
            scope.launch {
                exportBytes = withContext(Dispatchers.Default) { BackupManager.encrypt(viewModel.snapshot(), password.toCharArray()) }
                exportPassword = false
                backupCreate.launch("BebinDnevnik-${LocalDate.now()}.bdk")
            }
        }
    }
    if (importPassword) {
        PasswordDialog("Lozinka sigurnosne kopije", repeat = false, onDismiss = {
            importPassword = false
            importUri = null
        }) { password ->
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(importUri ?: return@launch)?.use { it.readBytes() } ?: return@launch
                    preview = withContext(Dispatchers.Default) { BackupManager.decrypt(bytes, password.toCharArray()) }
                    importPassword = false
                } catch (e: Exception) {
                    android.widget.Toast
                        .makeText(context, e.message ?: "Uvoz nije uspio.", android.widget.Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Zamijeniti sve podatke?") },
            text = {
                Text(
                    "Uvest će se ${data.mealCount} obroka, ${data.tummyCount} tummy-time sesija i ${data.dailyCount} dnevnih evidencija. Svi postojeći podaci bit će potpuno zamijenjeni. Radnja se ne može poništiti.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.replaceAll(data.snapshot)
                    preview = null
                }) { Text("Zamijeni podatke") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Odustani") } },
        )
    }
    if (csvWarning) {
        AlertDialog(
            onDismissRequest = { csvWarning = false },
            title = { Text("CSV nije šifriran") },
            text = {
                Text(
                    "ZIP s CSV datotekama služi samo za pregled. Čuvajte ga na sigurnom mjestu; iz njega nije moguće vratiti bazu.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    csvWarning = false
                    csvCreate.launch("BebinDnevnik-CSV-${LocalDate.now()}.zip")
                }) { Text("Nastavi") }
            },
            dismissButton = { TextButton(onClick = { csvWarning = false }) { Text("Odustani") } },
        )
    }
    if (deleteAll) {
        DeleteAllDialog({
            viewModel.deleteAll()
            deleteAll = false
        }, { deleteAll = false })
    }
}

@Composable private fun SettingsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) = OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    repeat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lozinka mora imati najmanje 8 znakova. Zaboravljenu lozinku nije moguće vratiti.")
                OutlinedTextField(password, {
                    password = it
                }, label = { Text("Lozinka") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (repeat) {
                    OutlinedTextField(repeated, {
                        repeated = it
                    }, label = { Text("Ponovite lozinku") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                error =
                    when {
                        password.length < 8 -> "Lozinka je prekratka."
                        repeat && password != repeated -> "Lozinke se ne podudaraju."
                        else -> null
                    }
                if (error == null) onConfirm(password)
            }) { Text("Nastavi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } },
    )
}

@Composable
private fun DeleteAllDialog(
    confirm: () -> Unit,
    dismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Trajno izbrisati sve podatke?") },
        text = {
            Column {
                Text("Ova se radnja ne može poništiti. Upišite IZBRIŠI za potvrdu.")
                OutlinedTextField(text, { text = it }, label = { Text("Potvrda") })
            }
        },
        confirmButton = { Button(onClick = confirm, enabled = text == "IZBRIŠI") { Text("Izbriši sve") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Odustani") } },
    )
}

private fun openNotificationSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}
