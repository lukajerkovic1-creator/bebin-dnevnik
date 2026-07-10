package hr.bebindnevnik.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hr.bebindnevnik.app.BebinDnevnikApplication
import hr.bebindnevnik.app.domain.AppLogic
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BebinDnevnikApplication).container
        val settings = container.repository.currentSnapshot().settings
        if (!settings.reminderEnabled) return Result.success()
        try {
            val today = LocalDate.now()
            val summary = container.repository.summary(today)
            val missing = AppLogic.missing(summary)
            if (AppLogic.shouldSendReminder(missing, settings.lastNotificationDate, today)) {
                container.notifications.showReminder(missing)
                if (container.notifications.notificationsAllowed()) {
                    container.repository.updateSettings { it.copy(lastNotificationDate = today.toString()) }
                }
            } else if (missing.isEmpty()) {
                container.notifications.cancelReminder()
            }
            return Result.success()
        } finally {
            val latest = container.repository.currentSnapshot().settings
            container.reminderScheduler.schedule(latest.reminderEnabled, LocalTime.parse(latest.reminderTime))
        }
    }
}
