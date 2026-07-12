package hr.bebindnevnik.app.update

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hr.bebindnevnik.app.BuildConfig
import java.time.Instant
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences("update_status", Context.MODE_PRIVATE)
        return when (val result = UpdateChecker.check()) {
            is UpdateCheckResult.Available -> {
                preferences.edit {
                    putInt("available_code", result.update.versionCode)
                    putString("available_name", result.update.versionName)
                }
                Result.success()
            }

            is UpdateCheckResult.Current -> {
                preferences.edit {
                    remove("available_code")
                    remove("available_name")
                }
                Result.success()
            }

            is UpdateCheckResult.Failed -> {
                Result.retry()
            }
        }.also {
            preferences.edit {
                putString("last_background_check", Instant.now().toString())
                putInt("installed_code", BuildConfig.VERSION_CODE)
            }
        }
    }

    companion object {
        private const val UNIQUE_NAME = "daily-app-update-check"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
