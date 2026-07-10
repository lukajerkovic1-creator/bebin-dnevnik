package hr.bebindnevnik.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hr.bebindnevnik.app.BebinDnevnikApplication

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val timer = (context.applicationContext as BebinDnevnikApplication).container.timerController
        when (intent.action) {
            ACTION_STOP -> timer.stopAndSave()
            ACTION_CANCEL -> timer.cancel()
        }
    }

    companion object {
        const val ACTION_STOP = "hr.bebindnevnik.app.STOP_TIMER"
        const val ACTION_CANCEL = "hr.bebindnevnik.app.CANCEL_TIMER"
    }
}
