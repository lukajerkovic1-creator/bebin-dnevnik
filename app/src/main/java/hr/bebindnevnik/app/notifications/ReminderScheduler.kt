package hr.bebindnevnik.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    private val context: Context,
) {
    fun schedule(
        enabled: Boolean,
        time: LocalTime,
    ) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = delayUntilNext(time)
        val request =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
        manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "daily_reminder"

        fun delayUntilNext(
            time: LocalTime,
            now: LocalDateTime = LocalDateTime.now(),
        ): Duration {
            var target = now.toLocalDate().atTime(time)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target)
        }
    }
}
