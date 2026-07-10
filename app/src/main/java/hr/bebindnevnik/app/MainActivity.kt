package hr.bebindnevnik.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.bebindnevnik.app.notifications.NotificationHelper
import hr.bebindnevnik.app.ui.BebinDnevnikApp
import hr.bebindnevnik.app.ui.BebinDnevnikTheme
import hr.bebindnevnik.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private var viewModelRef: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            val app = application as BebinDnevnikApplication
            val viewModel by viewModels<MainViewModel> { MainViewModel.Factory(app.container) }
            viewModelRef = viewModel
            viewModel.highlightMissing(intent.getStringArrayExtra(NotificationHelper.EXTRA_MISSING))
            setContent {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val timer by viewModel.timer.collectAsStateWithLifecycle()
                DisposableEffect(timer.running) {
                    if (timer.running) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                BebinDnevnikTheme(state.settings.theme) { BebinDnevnikApp(viewModel, app.container.notifications) }
            }
        } catch (error: Exception) {
            setContent {
                BebinDnevnikTheme(hr.bebindnevnik.app.data.AppTheme.SUSTAV) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Podatke nije moguće sigurno otvoriti", style = MaterialTheme.typography.headlineSmall)
                            Text(error.message ?: "Šifrirana baza ili zaštitni ključ nisu dostupni. Nemojte brisati podatke.")
                            Button(onClick = { recreate() }) { Text("Pokušaj ponovno") }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelRef?.highlightMissing(intent.getStringArrayExtra(NotificationHelper.EXTRA_MISSING))
    }

    override fun onUserLeaveHint() {
        viewModelRef?.onBackgrounded()
        super.onUserLeaveHint()
    }

    override fun onStop() {
        viewModelRef?.onBackgrounded()
        super.onStop()
    }
}
