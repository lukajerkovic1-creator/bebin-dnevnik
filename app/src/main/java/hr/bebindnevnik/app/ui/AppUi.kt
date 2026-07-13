@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import hr.bebindnevnik.app.backup.LocalSafetyBackup
import hr.bebindnevnik.app.cloud.CloudBackupPreferences
import hr.bebindnevnik.app.cloud.CloudBackupWorker
import hr.bebindnevnik.app.data.AppTheme
import hr.bebindnevnik.app.data.DayStatus
import hr.bebindnevnik.app.data.DaySummary
import hr.bebindnevnik.app.data.MealEntity
import hr.bebindnevnik.app.data.TernaryStatus
import hr.bebindnevnik.app.data.TummySessionEntity
import hr.bebindnevnik.app.domain.AppLogic
import hr.bebindnevnik.app.domain.StatisticsRange
import hr.bebindnevnik.app.notifications.NotificationHelper
import hr.bebindnevnik.app.notifications.TimerPhase
import hr.bebindnevnik.app.update.ApkUpdateManager
import hr.bebindnevnik.app.update.AppUpdate
import hr.bebindnevnik.app.update.UpdateCheckResult
import hr.bebindnevnik.app.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean

private data class NavItem(
    val route: String,
    val label: String,
    val icon: BabyNavKind,
)

private val navItems =
    listOf(
        NavItem("today", "Danas", BabyNavKind.TODAY),
        NavItem("calendar", "Kalendar", BabyNavKind.CALENDAR),
        NavItem("statistics", "Statistika", BabyNavKind.STATISTICS),
        NavItem("settings", "Postavke", BabyNavKind.SETTINGS),
    )

@Composable
fun BebinDnevnikApp(
    viewModel: MainViewModel,
    notifications: NotificationHelper,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val statisticsReport by viewModel.statisticsReport.collectAsStateWithLifecycle()
    val statisticsSelection by viewModel.statisticsSelection.collectAsStateWithLifecycle()
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
            viewModel = viewModel,
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BabyIllustration(BabyIllustrationKind.JOURNAL, Modifier.size(38.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bebin dnevnik", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("navigation-${item.route}"),
                        selected = backStack?.destination?.route == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            BabyNavIcon(
                                kind = item.icon,
                                selected = backStack?.destination?.route == item.route,
                                label = item.label,
                            )
                        },
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
            composable("statistics") {
                EnhancedStatisticsScreen(
                    report = statisticsReport,
                    selection = statisticsSelection,
                    onSelectionChange = viewModel::selectStatisticsRange,
                    onOpenDay = { date ->
                        viewModel.selectDate(date)
                        navController.navigate("today")
                    },
                )
            }
            composable("settings") {
                SettingsScreen(state, viewModel, notifications, onExplainPermission = { notificationExplanation = true })
            }
        }
    }
}

@Composable
private fun Onboarding(
    viewModel: MainViewModel,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCloud by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    val openBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importUri = uri
                askPassword = true
            }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BabyIllustration(BabyIllustrationKind.JOURNAL, Modifier.size(104.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text("Dobro došli u Bebin dnevnik", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        listOf(
            "Evidentirajte obroke, Waya kapi, vježbanje i tummy time.",
            "Nema profila djeteta ni identifikacijskih podataka.",
            "Lokalna šifrirana baza glavni je izvor podataka i aplikacija radi bez interneta.",
            "Dobrovoljni šifrirani Google Drive backup štiti od deinstalacije, kvara ili gubitka uređaja.",
            "Dnevni podsjetnik je neobvezan i može se isključiti u Postavkama.",
        ).forEach { Text("• $it", Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Započni bez vraćanja") }
        OutlinedButton(onClick = { showCloud = !showCloud }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Vrati podatke s Google Drivea")
        }
        OutlinedButton(onClick = { openBackup.launch(arrayOf("application/octet-stream", "application/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Uvezi sigurnosnu kopiju iz datoteke")
        }
        if (showCloud) CloudBackupSettingsCard(viewModel)
    }
    if (askPassword) {
        PasswordDialog("Lozinka sigurnosne kopije", repeat = false, onDismiss = { askPassword = false }) { password ->
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(importUri ?: return@launch)?.use { it.readBytes() } ?: return@launch
                    preview = withContext(Dispatchers.Default) { BackupManager.decrypt(bytes, password.toCharArray()) }
                    askPassword = false
                } catch (error: Exception) {
                    android.widget.Toast
                        .makeText(context, error.message ?: "Uvoz nije uspio.", android.widget.Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
    preview?.let { data ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Vratiti podatke?") },
            text = { Text("Vratit će se ${data.mealCount} obroka, ${data.dailyCount} dnevnih evidencija i ${data.tummyCount} tummy-time sesija.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.replaceAll(data.snapshot)
                    preview = null
                }) { Text("Vrati podatke") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Odustani") } },
        )
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
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
    var stoolEditor by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    val isToday = state.selectedDate == LocalDate.now()
    val notificationVisible = !notifications.notificationsAllowed()
    val listState = rememberLazyListState()

    LaunchedEffect(highlight, notificationVisible) {
        val target = listOf("obrok", "Waya kapi", "vježbanje", "stolica", "tummy time").firstOrNull { it in highlight }
        val baseIndex =
            when (target) {
                "obrok" -> 1
                "Waya kapi" -> 3
                "vježbanje" -> 4
                "stolica" -> 5
                "tummy time" -> 6
                else -> null
            }
        if (baseIndex != null) listState.animateScrollToItem(baseIndex + if (notificationVisible) 1 else 0)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DaySelectorHeader(
                selectedDate = state.selectedDate,
                status = state.summary.status,
                onDateSelected = viewModel::selectDate,
            )
        }
        if (notificationVisible) item { NotificationWarning(openSettings) }
        item {
            HighlightCard("obrok" in highlight) {
                IllustratedSectionTitle("Posljednji obrok", BabyIllustrationKind.BOTTLE)
                val last = state.selectedMeals.maxByOrNull { it.time }
                if (last == null) {
                    Text("Još nije evidentiran nijedan obrok.")
                } else {
                    Text("${last.time.hrStoredTime()} · ${last.amountMl} ml", style = MaterialTheme.typography.headlineSmall)
                    Text(AppLogic.elapsedText(LocalDate.parse(last.date), LocalTime.parse(last.time)))
                }
            }
        }
        item {
            HighlightCard("obrok" in highlight) {
                IllustratedSectionTitle("Novi obrok", BabyIllustrationKind.BOTTLE)
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
        item {
            StatusCard(
                "Waya kapi",
                BabyIllustrationKind.DROPS,
                "Waya kapi" in highlight,
                state.summary.waya,
                viewModel::setWaya,
            )
        }
        item {
            StatusCard(
                "Vježbanje",
                BabyIllustrationKind.EXERCISE,
                "vježbanje" in highlight,
                state.summary.exercise,
                viewModel::setExercise,
            )
        }
        item {
            HighlightCard("stolica" in highlight, Modifier.testTag("stool-card")) {
                IllustratedSectionTitle("Stolica", BabyIllustrationKind.STOOL)
                Text("Trenutačno: ${stoolCountText(state.summary.stoolCount)}", style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { stoolEditor = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("edit-stool"),
                ) {
                    Text(if (state.summary.stoolCount == null) "Evidentiraj broj" else "Uredi broj")
                }
            }
        }
        item {
            HighlightCard("tummy time" in highlight, Modifier.testTag("tummy-card")) {
                IllustratedSectionTitle("Tummy time", BabyIllustrationKind.TUMMY)
                Text(
                    "${if (isToday) "Ukupno danas" else "Ukupno za odabrani dan"}: " +
                        "${state.summary.tummySeconds.durationText()} · ${tummySessionCountText(state.summary.tummyCount)}",
                )
                if (isToday) {
                    when (timer.phase) {
                        TimerPhase.RUNNING -> {
                            Text(
                                timer.elapsedSeconds.durationText(),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("timer-elapsed"),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = viewModel::stopTimer, modifier = Modifier.weight(1f).height(56.dp).testTag("timer-stop")) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Text("Zaustavi")
                                }
                                OutlinedButton(
                                    onClick = viewModel::cancelTimer,
                                    modifier = Modifier.weight(1f).height(56.dp).testTag("timer-cancel"),
                                ) { Text("Poništi") }
                            }
                        }

                        TimerPhase.IDLE -> {
                            Button(onClick = viewModel::startTimer, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("timer-start")) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pokreni novu sesiju")
                            }
                        }

                        TimerPhase.CONFIRMING -> {
                            Unit
                        }
                    }
                }
                OutlinedButton(
                    onClick = { newTummy = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual-tummy"),
                ) { Text("Ručni unos") }
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
        MealEditorSheet(
            item = mealDialog,
            defaultDate = state.selectedDate,
            onWarnings = viewModel::mealWarnings,
            onSave = viewModel::saveMeal,
            onClose = {
                newMeal = false
                mealDialog = null
            },
        )
    }
    if (newTummy ||
        tummyDialog != null
    ) {
        TummyEditorSheet(
            item = tummyDialog,
            defaultDate = state.selectedDate,
            onWarnings = viewModel::tummyWarnings,
            onSave = viewModel::saveTummy,
            onClose = {
                newTummy = false
                tummyDialog = null
            },
        )
    }
    if (timer.phase == TimerPhase.CONFIRMING) {
        AlertDialog(
            onDismissRequest = viewModel::cancelTimer,
            title = { Text("Spremiti tummy-time sesiju?") },
            text = {
                Text(
                    if (timer.elapsedSeconds < 5) {
                        "Sesija je kraća od 5 sekundi. Želite li je ipak spremiti?"
                    } else {
                        "Sesija je dulja od 60 minuta. Želite li je ipak spremiti?"
                    },
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmTimer, modifier = Modifier.testTag("confirm-timer-save")) {
                    Text("Spremi")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelTimer) { Text("Odustani") }
            },
        )
    }
    if (stoolEditor) {
        StoolEditorSheet(
            initialCount = state.summary.stoolCount,
            date = state.selectedDate,
            onWarnings = { viewModel.stoolWarnings(it, state.selectedDate) },
            onSave = viewModel::setStoolCount,
            onClose = { stoolEditor = false },
        )
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
            text = {
                Text(
                    "Waya kapi, vježbanje i stolica vratit će se na „Nije evidentirano”. Obroci i tummy-time sesije neće se izbrisati.",
                )
            },
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
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(BabyDimensions.CardCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(BabyDimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun IllustratedSectionTitle(
    text: String,
    illustration: BabyIllustrationKind,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(text)
        Spacer(Modifier.weight(1f))
        BabyIllustration(illustration, Modifier.size(BabyDimensions.IllustrationSmall))
    }
}

@Composable
private fun StatusCard(
    title: String,
    illustration: BabyIllustrationKind,
    highlighted: Boolean,
    status: TernaryStatus,
    set: (TernaryStatus) -> Unit,
) = HighlightCard(highlighted) {
    IllustratedSectionTitle(title, illustration)
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BabyDimensions.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            IllustratedSectionTitle("Dnevni sažetak", BabyIllustrationKind.JOURNAL)
            Text("Ukupno: ${summary.totalMl} ml")
            Text("Broj obroka: ${summary.mealCount}")
            Text("Prosječno: ${"%.1f".format(summary.averageMl)} ml")
            Text("Posljednji obrok: ${summary.lastMealTime?.hrStoredTime() ?: "Nije evidentirano"}")
            Text("Waya kapi: ${summary.waya.label()}")
            Text("Vježbanje: ${summary.exercise.label()}")
            Text("Stolica: ${stoolCountText(summary.stoolCount)}")
            Text("Tummy time: ${summary.tummySeconds.durationText()} (${summary.tummyCount} sesija)")
            Text("Status dana: ${dayStatusLabel(summary.status)}", fontWeight = FontWeight.Bold)
        }
    }

@Composable
internal fun StatusBadge(status: DayStatus) {
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
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Kalendar")
                Text("Nježan pregled bebinih dana", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BabyIllustration(BabyIllustrationKind.JOURNAL, Modifier.size(BabyDimensions.IllustrationSmall))
        }
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
                            Modifier.weight(1f).heightIn(min = 64.dp).padding(2.dp).clickable(enabled = !disabled) {
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
                                Text(
                                    symbol,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        when (summary.status) {
                                            DayStatus.POTPUNO -> Color(0xFF2E7D32)
                                            DayStatus.DJELOMICNO -> MaterialTheme.colorScheme.primary
                                            DayStatus.BEZ_PODATAKA -> MaterialTheme.colorScheme.outline
                                        },
                                )
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
@Suppress("UnusedPrivateMember") // Kept temporarily as a compact fallback while the enhanced screen is exercised by release tests.
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Statistika")
                    Text("Pregled malih, važnih trenutaka", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BabyIllustration(BabyIllustrationKind.BOTTLE, Modifier.size(BabyDimensions.IllustrationSmall))
            }
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
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(BabyDimensions.CardCorner),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    IllustratedSectionTitle("Sažetak razdoblja", BabyIllustrationKind.JOURNAL)
                    Text("Razdoblje: ${start.hrDate()} – ${today.hrDate()}")
                    Text("Ukupno ml: $totalMl ml")
                    Text("Broj obroka: $mealCount")
                    Text("Prosječno po obroku: ${if (mealCount == 0) "0,0" else "%.1f".format(totalMl.toDouble() / mealCount)} ml")
                    Text("Tummy time: ${(tummy / 60)} min · ${summaries.sumOf { it.tummyCount }} sesija")
                    Text("Potpuni dani: $complete/${summaries.size} (${if (summaries.isEmpty()) 0 else complete * 100 / summaries.size}%)")
                }
            }
        }
        item { StoolStatisticsCard(summaries) }
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
private fun StoolStatisticsCard(summaries: List<DaySummary>) {
    val recorded = summaries.mapNotNull { it.stoolCount }
    val statistics = AppLogic.stoolStatistics(summaries)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BabyDimensions.CardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IllustratedSectionTitle("Stolica", BabyIllustrationKind.STOOL)
            if (recorded.isEmpty()) {
                Text("Nema evidentiranih podataka o stolici za odabrano razdoblje.")
            } else {
                Text("Ukupno evidentiranih stolica: ${statistics.total}")
                Text("Prosječno po evidentiranom danu: ${"%.1f".format(statistics.averagePerRecordedDay)}")
                Text("Dani s 0 stolica: ${statistics.zeroDays}")
                Text("Dani s barem jednom stolicom: ${statistics.positiveDays}")
                Text("Bez evidentiranog podatka: ${statistics.missingDays}/${summaries.size} (${statistics.missingPercent}%)")
                val primary = MaterialTheme.colorScheme.primary
                val missingColor = MaterialTheme.colorScheme.outlineVariant
                val description =
                    "Broj stolica po danu. Ukupno ${statistics.total}. ${statistics.missingDays} dana bez evidentiranog podatka."
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .semantics { contentDescription = description }
                        .testTag("stool-chart"),
                ) {
                    val max = recorded.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val step = size.width / summaries.size.coerceAtLeast(1)
                    summaries.forEachIndexed { index, summary ->
                        val value = summary.stoolCount
                        val left = index * step
                        if (value == null) {
                            drawRect(
                                missingColor,
                                topLeft =
                                    androidx.compose.ui.geometry
                                        .Offset(left, size.height - 4.dp.toPx()),
                                size =
                                    androidx.compose.ui.geometry
                                        .Size((step - 2).coerceAtLeast(1f), 4.dp.toPx()),
                            )
                        } else if (value == 0) {
                            drawCircle(
                                primary,
                                radius = 2.dp.toPx(),
                                center =
                                    androidx.compose.ui.geometry
                                        .Offset(left + step / 2, size.height - 3.dp.toPx()),
                            )
                        } else {
                            val barHeight = size.height * value / max
                            drawRect(
                                primary,
                                topLeft =
                                    androidx.compose.ui.geometry
                                        .Offset(left, size.height - barHeight),
                                size =
                                    androidx.compose.ui.geometry
                                        .Size((step - 2).coerceAtLeast(1f), barHeight),
                            )
                        }
                    }
                }
                Text("Graf prikazuje samo evidentirane vrijednosti; siva crta označava dan bez podatka.", style = MaterialTheme.typography.bodySmall)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Nema podataka za odabrano razdoblje.", Modifier.weight(1f))
                    BabyIllustration(
                        if (title.contains("Tummy", ignoreCase = true)) {
                            BabyIllustrationKind.TUMMY
                        } else {
                            BabyIllustrationKind.BOTTLE
                        },
                        Modifier.size(60.dp),
                    )
                }
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
@Suppress("LongMethod") // Declarative settings layout includes its tightly coupled launcher and confirmation state.
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
    var showReminderTimePicker by remember { mutableStateOf(false) }
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
                TimeSelectionRow(
                    time = reminderTime,
                    label = "Vrijeme podsjetnika",
                    testTag = "reminder-time-row",
                    onClick = { showReminderTimePicker = true },
                )
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
        item { AppUpdateSettingsCard(context, viewModel) }
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
        item { CloudBackupSettingsCard(viewModel) }
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
    if (showReminderTimePicker) {
        EntryTimePickerDialog(
            selectedTime = reminderTime,
            onConfirm = { selected ->
                reminderTime = selected
                viewModel.setReminderTime(selected)
                showReminderTimePicker = false
            },
            onDismiss = { showReminderTimePicker = false },
        )
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

@Composable
private fun AppUpdateSettingsCard(
    context: android.content.Context,
    viewModel: MainViewModel,
) {
    val scope = rememberCoroutineScope()
    val manager = remember { ApkUpdateManager(context) }
    val preferences = remember { context.getSharedPreferences("update_status", android.content.Context.MODE_PRIVATE) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var downloading by remember { mutableStateOf(false) }
    var preparedFile by remember { mutableStateOf<File?>(null) }
    var cancellation by remember { mutableStateOf<AtomicBoolean?>(null) }
    var lastCheck by remember { mutableStateOf(preferences.getString("last_check", null)) }
    val installLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            message = "Ako ste odustali od instalacije, postojeća aplikacija i svi podaci ostali su nepromijenjeni."
        }
    val unknownSourceLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val file = preparedFile
            if (file != null && manager.canInstallPackages()) installLauncher.launch(manager.installIntent(file))
        }

    fun startInstaller(file: File) {
        if (manager.canInstallPackages()) {
            installLauncher.launch(manager.installIntent(file))
        } else {
            message = "Dopustite instaliranje iz ovog izvora pa će se postupak nastaviti."
            unknownSourceLauncher.launch(manager.unknownSourcesIntent())
        }
    }

    suspend fun downloadAndInstall(update: AppUpdate) {
        downloading = true
        progress = 0
        message = "Pronađena je verzija ${update.versionName}. Preuzimanje je pokrenuto."
        val token = AtomicBoolean(false)
        cancellation = token
        try {
            val file = manager.downloadAndVerify(update, token) { progress = it }
            withContext(Dispatchers.IO) { LocalSafetyBackup.create(context, viewModel.snapshot()) }
            CloudBackupPreferences(context).let { cloud ->
                if (cloud.status().enabled) {
                    cloud.markDirty()
                    CloudBackupWorker.schedule(context, delaySeconds = 0, replace = true)
                }
            }
            preparedFile = file
            message = "APK je preuzet i sve sigurnosne provjere su prošle."
            startInstaller(file)
        } catch (error: Exception) {
            message = error.message ?: "Preuzimanje nije uspjelo. Pokušajte ponovno."
        } finally {
            downloading = false
            cancellation = null
        }
    }

    SettingsCard("Ažuriranje aplikacije") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Instalirana verzija", style = MaterialTheme.typography.labelMedium)
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
            }
        }
        lastCheck?.let { Text("Posljednja provjera: $it", style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                checking = true
                message = null
                scope.launch {
                    when (val result = UpdateChecker.check()) {
                        is UpdateCheckResult.Available -> {
                            checking = false
                            downloadAndInstall(result.update)
                        }

                        is UpdateCheckResult.Current -> {
                            message =
                                "Na GitHubu još nije objavljena novija verzija od ${result.latestVersionName}."
                        }

                        is UpdateCheckResult.Failed -> {
                            message = result.message
                        }
                    }
                    lastCheck =
                        java.time.ZonedDateTime
                            .now()
                            .format(
                                java.time.format.DateTimeFormatter
                                    .ofPattern("dd.MM.yyyy. HH:mm"),
                            )
                    preferences.edit { putString("last_check", lastCheck) }
                    checking = false
                }
            },
            enabled = !checking && !downloading,
            modifier = Modifier.fillMaxWidth().heightIn(min = BabyDimensions.TouchTarget).testTag("check-for-update"),
        ) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                when {
                    checking -> "Provjeravam…"
                    downloading -> "Preuzimam…"
                    else -> "Provjeri i preuzmi novu verziju"
                },
            )
        }
        if (downloading) {
            androidx.compose.material3.LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth())
            Text("Preuzimanje: $progress %")
            TextButton(onClick = { cancellation?.set(true) }, Modifier.fillMaxWidth()) { Text("Prekini") }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("update-message"))
        }
        Text(
            "Aplikacija provjerava službeni GitHub Release. Prije instalacije provjerava SHA-256, paket, verziju i potpis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
