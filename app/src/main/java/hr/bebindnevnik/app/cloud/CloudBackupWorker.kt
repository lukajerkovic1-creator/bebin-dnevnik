package hr.bebindnevnik.app.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hr.bebindnevnik.app.BebinDnevnikApplication
import hr.bebindnevnik.app.data.DATABASE_VERSION
import java.time.Instant
import java.util.concurrent.TimeUnit

class CloudBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preferences = CloudBackupPreferences(applicationContext)
        val status = preferences.status()
        if (!status.enabled || !status.dirty || status.accountEmail == null) return Result.success()
        return try {
            val authorization = GoogleDriveAuthorization.authorize(applicationContext, status.accountEmail)
            if (authorization !is AuthorizationOutcome.Authorized) {
                preferences.recordError("Ponovno otvorite aplikaciju i obnovite Google Drive dopuštenje.")
                return Result.retry()
            }
            val app = applicationContext as BebinDnevnikApplication
            val snapshot = app.container.repository.currentSnapshot()
            val keys = CloudKeyManager(applicationContext)
            val dek = keys.localDek()
            val bytes =
                try {
                    CloudBackupCodec.encode(snapshot, dek, keys.passwordWrap(), DATABASE_VERSION)
                } finally {
                    dek.fill(0)
                }
            val drive = GoogleDriveAppDataClient(authorization.accessToken)
            drive.upload(bytes)
            drive.retainNewest(5)
            preferences.recordSuccess(Instant.now().toString())
            Result.success()
        } catch (error: Exception) {
            preferences.recordError(error.message ?: "Cloud backup nije uspio.")
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "cloud-backup"

        fun schedule(
            context: Context,
            delaySeconds: Long = 45,
            replace: Boolean = false,
        ) {
            val request =
                OneTimeWorkRequestBuilder<CloudBackupWorker>()
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
