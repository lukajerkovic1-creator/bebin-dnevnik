package hr.bebindnevnik.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import hr.bebindnevnik.app.BebinDnevnikApplication
import hr.bebindnevnik.app.backup.BackupManager
import hr.bebindnevnik.app.backup.BackupPreview
import hr.bebindnevnik.app.backup.LocalSafetyBackup
import hr.bebindnevnik.app.security.DatabaseRecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DatabaseRecoveryScreen(
    error: Throwable,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var askPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
            if (selected != null) {
                uri = selected
                askPassword = true
            }
        }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Podatke nije moguće sigurno otvoriti", style = MaterialTheme.typography.headlineSmall)
            Text(error.message ?: "Šifrirana baza ili migracija nisu dostupne. Izvorna baza nije izbrisana.")
            Button(onClick = onRetry) { Text("Pokušaj ponovno") }
            if (LocalSafetyBackup.exists(context)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { LocalSafetyBackup.restore(context) } }
                            .onSuccess { preview = it }
                            .onFailure { message = it.message }
                    }
                }) { Text("Vrati lokalnu kopiju prije ažuriranja") }
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/octet-stream", "application/*")) }) {
                Text("Uvezi sigurnosnu kopiju iz datoteke")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (askPassword) {
        AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text("Lozinka sigurnosne kopije") },
            text = {
                OutlinedTextField(password, { password = it }, label = { Text("Lozinka") }, visualTransformation = PasswordVisualTransformation())
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            val bytes = context.contentResolver.openInputStream(requireNotNull(uri))?.use { it.readBytes() } ?: error("Datoteku nije moguće otvoriti.")
                            withContext(Dispatchers.Default) { BackupManager.decrypt(bytes, password.toCharArray()) }
                        }.onSuccess {
                            preview = it
                            askPassword = false
                        }.onFailure { message = it.message }
                    }
                }, enabled = password.length >= 8) { Text("Provjeri") }
            },
            dismissButton = { TextButton(onClick = { askPassword = false }) { Text("Odustani") } },
        )
    }
    preview?.let { valid ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Kontrolirano vratiti podatke?") },
            text = { Text("Izvorna baza prvo će se kopirati u privatnu recovery mapu. Tek zatim će se stvoriti nova baza i u jednoj transakciji vratiti ${valid.mealCount} obroka, ${valid.dailyCount} dnevnih evidencija i ${valid.tummyCount} tummy-time sesija.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                DatabaseRecoveryManager.preserveAndReset(context)
                                val app = context.applicationContext as BebinDnevnikApplication
                                app.container.repository.replaceAll(valid.snapshot)
                            }
                        }.onSuccess { onRetry() }.onFailure { message = it.message }
                    }
                }) { Text("Sačuvaj izvornu bazu i vrati") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Odustani") } },
        )
    }
}
