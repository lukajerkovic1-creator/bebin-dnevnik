package hr.bebindnevnik.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.bebindnevnik.app.notifications.NotificationHelper
import hr.bebindnevnik.app.ui.BebinDnevnikApp
import hr.bebindnevnik.app.ui.BebinDnevnikTheme
import hr.bebindnevnik.app.ui.DatabaseRecoveryScreen
import hr.bebindnevnik.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private var viewModelRef: MainViewModel? = null
    private val dateTimeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                viewModelRef?.onSystemDateTimeChanged()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            val app = application as BebinDnevnikApplication
            val viewModel by viewModels<MainViewModel> { MainViewModel.Factory(app.container) }
            viewModelRef = viewModel
            ContextCompat.registerReceiver(
                this,
                dateTimeReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
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
                    DatabaseRecoveryScreen(error) { recreate() }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelRef?.highlightMissing(intent.getStringArrayExtra(NotificationHelper.EXTRA_MISSING))
    }

    override fun onStart() {
        super.onStart()
        viewModelRef?.onForegrounded()
    }

    override fun onUserLeaveHint() {
        viewModelRef?.onBackgrounded()
        super.onUserLeaveHint()
    }

    override fun onStop() {
        viewModelRef?.onBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dateTimeReceiver) }
        super.onDestroy()
    }
}
