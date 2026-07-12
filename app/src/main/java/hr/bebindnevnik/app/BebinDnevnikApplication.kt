package hr.bebindnevnik.app

import android.app.Application
import hr.bebindnevnik.app.update.UpdateCheckWorker

class BebinDnevnikApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
    }
}
