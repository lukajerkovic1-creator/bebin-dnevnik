package hr.bebindnevnik.app

import android.app.Application

class BebinDnevnikApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
