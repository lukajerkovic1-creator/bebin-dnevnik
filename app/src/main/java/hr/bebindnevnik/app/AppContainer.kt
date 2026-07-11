package hr.bebindnevnik.app

import android.content.Context
import androidx.room.Room
import hr.bebindnevnik.app.data.AppDatabase
import hr.bebindnevnik.app.data.AppRepository
import hr.bebindnevnik.app.notifications.NotificationHelper
import hr.bebindnevnik.app.notifications.ReminderScheduler
import hr.bebindnevnik.app.notifications.TimerController
import hr.bebindnevnik.app.security.DatabaseKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase
    val repository: AppRepository
    val notifications = NotificationHelper(appContext)
    val reminderScheduler = ReminderScheduler(appContext)
    val timerController: TimerController

    init {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyManager(appContext).getOrCreatePassphrase()
        try {
            database =
                Room
                    .databaseBuilder(appContext, AppDatabase::class.java, "bebin-dnevnik.db")
                    .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
                    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                    .build()
        } finally {
            passphrase.fill(0)
        }
        repository = AppRepository(database)
        timerController = TimerController(repository, notifications, scope)
        scope.launch {
            repository.initialize()
            val settings = repository.currentSnapshot().settings
            reminderScheduler.schedule(settings.reminderEnabled, java.time.LocalTime.parse(settings.reminderTime))
        }
    }
}
