package hr.bebindnevnik.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import hr.bebindnevnik.app.MainActivity
import hr.bebindnevnik.app.R

class NotificationHelper(
    private val context: Context,
) : TimerNotifications {
    init {
        createChannels()
    }

    fun notificationsAllowed(): Boolean =
        (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun showTimer() {
        if (!notificationsAllowed()) return
        val content =
            PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).putExtra(EXTRA_TIMER, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop = actionPendingIntent(TimerActionReceiver.ACTION_STOP, 11)
        val cancel = actionPendingIntent(TimerActionReceiver.ACTION_CANCEL, 12)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_TIMER)
                .setSmallIcon(R.drawable.ic_journal)
                .setContentTitle("Tummy time u tijeku")
                .setContentText("Štoperica radi samo dok je aplikacija u prvom planu.")
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "Zaustavi", stop)
                .addAction(0, "Poništi", cancel)
                .build()
        notifyIfAllowed(TIMER_ID, notification)
    }

    override fun cancelTimer() = NotificationManagerCompat.from(context).cancel(TIMER_ID)

    fun showReminder(missing: List<String>) {
        if (!notificationsAllowed() || missing.isEmpty()) return
        val content =
            PendingIntent.getActivity(
                context,
                20,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_MISSING, missing.toTypedArray())
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val sentence = missing.joinCroatian()
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_journal)
                .setContentTitle("Bebin dnevnik")
                .setContentText("Nisu evidentirani: $sentence.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Nisu evidentirani: $sentence."))
                .setAutoCancel(true)
                .setContentIntent(content)
                .build()
        notifyIfAllowed(REMINDER_ID, notification)
    }

    fun cancelReminder() = NotificationManagerCompat.from(context).cancel(REMINDER_ID)

    private fun actionPendingIntent(
        action: String,
        code: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notifyIfAllowed(
        id: Int,
        notification: android.app.Notification,
    ) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Dozvola se može opozvati između provjere i poziva; podsjetnik se tada sigurno preskače.
        }
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_REMINDER,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_TIMER,
                    context.getString(R.string.notification_channel_timer),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    private fun List<String>.joinCroatian(): String =
        when (size) {
            0 -> ""
            1 -> first()
            else -> dropLast(1).joinToString(", ") + " i " + last()
        }

    companion object {
        const val EXTRA_MISSING = "missing"
        const val EXTRA_TIMER = "timer"
        const val CHANNEL_REMINDER = "daily_reminders"
        const val CHANNEL_TIMER = "tummy_timer"
        const val TIMER_ID = 101
        const val REMINDER_ID = 102
    }
}
