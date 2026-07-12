@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hr.bebindnevnik.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import hr.bebindnevnik.app.BuildConfig
import hr.bebindnevnik.app.cloud.AuthorizationOutcome
import hr.bebindnevnik.app.cloud.CloudBackupCodec
import hr.bebindnevnik.app.cloud.CloudBackupMetadata
import hr.bebindnevnik.app.cloud.CloudBackupPreferences
import hr.bebindnevnik.app.cloud.CloudBackupStatus
import hr.bebindnevnik.app.cloud.CloudBackupWorker
import hr.bebindnevnik.app.cloud.CloudKeyManager
import hr.bebindnevnik.app.cloud.DriveBackupFile
import hr.bebindnevnik.app.cloud.GoogleDriveAppDataClient
import hr.bebindnevnik.app.cloud.GoogleDriveAuthorization
import hr.bebindnevnik.app.data.DATABASE_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private enum class CloudOperation { CONNECT, BACKUP, LIST, DELETE }

private enum class CloudPasswordMode { ENABLE, CHANGE, RESTORE }

private data class CloudFilePreview(
    val file: DriveBackupFile,
    val metadata: CloudBackupMetadata,
)

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // One stateful Compose boundary coordinates authorization result launchers and dialogs.
internal fun CloudBackupSettingsCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val preferences = remember { CloudBackupPreferences(context) }
    val keys = remember { CloudKeyManager(context) }
    var status by remember { mutableStateOf(preferences.status()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var pendingOperation by remember { mutableStateOf<CloudOperation?>(null) }
    var passwordMode by remember { mutableStateOf<CloudPasswordMode?>(null) }
    var cloudFiles by remember { mutableStateOf<List<CloudFilePreview>>(emptyList()) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restorePreview by remember { mutableStateOf<hr.bebindnevnik.app.backup.BackupPreview?>(null) }
    var confirmDeleteCloud by remember { mutableStateOf(false) }

    fun refresh() {
        status = preferences.status()
    }

    suspend fun uploadNow(token: String) {
        val snapshot = viewModel.snapshot()
        withContext(Dispatchers.IO) {
            val dek = keys.localDek()
            val bytes =
                try {
                    CloudBackupCodec.encode(snapshot, dek, keys.passwordWrap(), DATABASE_VERSION)
                } finally {
                    dek.fill(0)
                }
            GoogleDriveAppDataClient(token).run {
                upload(bytes)
                retainNewest(5)
            }
            preferences.recordSuccess(Instant.now().toString())
        }
        refresh()
        message = "Cloud sigurnosna kopija je uspješno spremljena."
    }

    fun executeAuthorized(
        outcome: AuthorizationOutcome.Authorized,
        operation: CloudOperation,
    ) {
        accessToken = outcome.accessToken
        pendingEmail = outcome.email
        scope.launch {
            busy = true
            try {
                when (operation) {
                    CloudOperation.CONNECT -> {
                        passwordMode = CloudPasswordMode.ENABLE
                    }

                    CloudOperation.BACKUP -> {
                        uploadNow(outcome.accessToken)
                    }

                    CloudOperation.LIST -> {
                        cloudFiles =
                            withContext(Dispatchers.IO) {
                                val drive = GoogleDriveAppDataClient(outcome.accessToken)
                                drive.list().take(5).mapNotNull { file ->
                                    runCatching { CloudFilePreview(file, CloudBackupCodec.metadata(drive.download(file.id))) }.getOrNull()
                                }
                            }
                        if (cloudFiles.isEmpty()) message = "Na Google Driveu nema valjanih cloud kopija."
                    }

                    CloudOperation.DELETE -> {
                        withContext(Dispatchers.IO) {
                            GoogleDriveAppDataClient(outcome.accessToken).run { list().forEach { delete(it.id) } }
                        }
                        message = "Cloud sigurnosne kopije su izbrisane."
                    }
                }
            } catch (error: Exception) {
                message = error.message ?: "Cloud postupak nije uspio."
                preferences.recordError(message!!)
            } finally {
                busy = false
                refresh()
            }
        }
    }

    val resolutionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val email = pendingEmail
            val operation = pendingOperation
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && email != null && operation != null && data != null) {
                runCatching { GoogleDriveAuthorization.complete(context, email, data) }
                    .onSuccess { executeAuthorized(it, operation) }
                    .onFailure { message = it.message ?: "Google Drive dopuštenje nije dovršeno." }
            } else {
                message = "Google Drive povezivanje je otkazano."
            }
        }

    fun authorize(operation: CloudOperation) {
        scope.launch {
            busy = true
            try {
                val email = if (operation == CloudOperation.CONNECT) GoogleDriveAuthorization.chooseAccount(activity) else status.accountEmail
                requireNotNull(email) { "Najprije povežite Google Drive." }
                when (val outcome = GoogleDriveAuthorization.authorize(context, email)) {
                    is AuthorizationOutcome.Authorized -> {
                        executeAuthorized(outcome, operation)
                    }

                    is AuthorizationOutcome.NeedsResolution -> {
                        pendingEmail = email
                        pendingOperation = operation
                        resolutionLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build())
                    }
                }
            } catch (error: Exception) {
                message = error.message ?: "Google račun nije moguće povezati."
            } finally {
                busy = false
            }
        }
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Cloud sigurnosna kopija", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                Text("Google Drive još nije konfiguriran. Upute su u README-u.", color = MaterialTheme.colorScheme.error)
            } else if (status.accountEmail == null) {
                Text("Dobrovoljna, klijentski šifrirana kopija u skrivenoj Drive appDataFolder mapi.")
                Button(onClick = { authorize(CloudOperation.CONNECT) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Poveži Google Drive")
                }
            } else {
                Text("Povezano: ${status.accountEmail}")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatski backup", Modifier.weight(1f))
                    Switch(
                        checked = status.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                preferences.enable(status.accountEmail!!)
                                CloudBackupWorker.schedule(context, replace = true)
                            } else {
                                preferences.disable()
                                CloudBackupWorker.cancel(context)
                            }
                            refresh()
                        },
                    )
                }
                status.lastSuccess?.let { Text("Zadnji uspješni backup: $it", style = MaterialTheme.typography.bodySmall) }
                status.lastError?.let { Text("Zadnja pogreška: $it", color = MaterialTheme.colorScheme.error) }
                Button(onClick = { authorize(CloudOperation.BACKUP) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Napravi backup sada")
                }
                OutlinedButton(onClick = { authorize(CloudOperation.LIST) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Vrati podatke iz clouda")
                }
                TextButton(onClick = { passwordMode = CloudPasswordMode.CHANGE }, modifier = Modifier.fillMaxWidth()) {
                    Text("Promijeni lozinku za backup")
                }
                TextButton(
                    onClick = {
                        val email = status.accountEmail!!
                        scope.launch {
                            busy = true
                            runCatching {
                                GoogleDriveAuthorization.disconnect(context, email)
                                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                            }.onFailure { message = "Google pristup nije potpuno opozvan: ${it.message}" }
                            preferences.disconnect()
                            keys.clear()
                            CloudBackupWorker.cancel(context)
                            refresh()
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Odspoji Google račun") }
                TextButton(onClick = { confirmDeleteCloud = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Izbriši cloud sigurnosne kopije", color = MaterialTheme.colorScheme.error)
                }
            }
            if (busy) Text("Radim…", color = MaterialTheme.colorScheme.primary)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (passwordMode != null) {
        val mode = passwordMode!!
        CloudPasswordDialog(
            title = if (mode == CloudPasswordMode.RESTORE) "Lozinka cloud kopije" else "Nova cloud-backup lozinka",
            repeat = mode != CloudPasswordMode.RESTORE,
            onDismiss = { passwordMode = null },
        ) { password ->
            scope.launch {
                try {
                    when (mode) {
                        CloudPasswordMode.ENABLE -> {
                            keys.enable(password.toCharArray())
                            preferences.enable(requireNotNull(pendingEmail))
                            uploadNow(requireNotNull(accessToken))
                        }

                        CloudPasswordMode.CHANGE -> {
                            keys.changePassword(password.toCharArray())
                            preferences.markDirty()
                            CloudBackupWorker.schedule(context, delaySeconds = 0, replace = true)
                            message = "Nova lozinka vrijedi za buduće kopije. Čuvajte je na sigurnom."
                        }

                        CloudPasswordMode.RESTORE -> {
                            val decoded = withContext(Dispatchers.Default) { CloudBackupCodec.decode(requireNotNull(restoreBytes), password.toCharArray()) }
                            restorePreview =
                                hr.bebindnevnik.app.backup.BackupPreview(
                                    decoded.snapshot,
                                    decoded.metadata.mealCount,
                                    decoded.metadata.dailyCount,
                                    decoded.metadata.tummyCount,
                                )
                        }
                    }
                    passwordMode = null
                    refresh()
                } catch (error: Exception) {
                    message = error.message ?: "Cloud lozinka ili kopija nisu valjane."
                }
            }
        }
    }

    if (cloudFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { cloudFiles = emptyList() },
            title = { Text("Odaberite cloud kopiju") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cloudFiles.forEach { preview ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        busy = true
                                        try {
                                            restoreBytes = withContext(Dispatchers.IO) { GoogleDriveAppDataClient(requireNotNull(accessToken)).download(preview.file.id) }
                                            passwordMode = CloudPasswordMode.RESTORE
                                            cloudFiles = emptyList()
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }.padding(8.dp),
                        ) {
                            Text(preview.metadata.createdAt, fontWeight = FontWeight.Bold)
                            Text("Verzija ${preview.metadata.appVersion} · ${preview.metadata.mealCount} obroka · ${preview.metadata.tummyCount} tummy-time sesija")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { cloudFiles = emptyList() }) { Text("Odustani") } },
        )
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { restorePreview = null },
            title = { Text("Vratiti odabranu kopiju?") },
            text = { Text("Vratit će se ${preview.mealCount} obroka, ${preview.dailyCount} dnevnih evidencija i ${preview.tummyCount} tummy-time sesija. Postojeći podaci zamijenit će se tek nakon potvrde.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.replaceAll(preview.snapshot)
                    restorePreview = null
                    message = "Podaci su uspješno vraćeni."
                }) { Text("Vrati podatke") }
            },
            dismissButton = { TextButton(onClick = { restorePreview = null }) { Text("Odustani") } },
        )
    }

    if (confirmDeleteCloud) {
        AlertDialog(
            onDismissRequest = { confirmDeleteCloud = false },
            title = { Text("Izbrisati sve cloud kopije?") },
            text = { Text("Ova radnja trajno briše svih pet verzija iz skrivene Google Drive mape.") },
            confirmButton = {
                Button(onClick = {
                    confirmDeleteCloud = false
                    authorize(CloudOperation.DELETE)
                }) { Text("Izbriši") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteCloud = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun CloudPasswordDialog(
    title: String,
    repeat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val valid = password.length >= 8 && (!repeat || password == repeated)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zaboravljena lozinka ne može se vratiti. Ne šalje se Googleu i ne sprema se kao običan tekst.")
                OutlinedTextField(password, { password = it }, label = { Text("Lozinka (najmanje 8 znakova)") }, visualTransformation = PasswordVisualTransformation())
                if (repeat) OutlinedTextField(repeated, { repeated = it }, label = { Text("Ponovite lozinku") }, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(password) }, enabled = valid) { Text("Potvrdi") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } },
    )
}
