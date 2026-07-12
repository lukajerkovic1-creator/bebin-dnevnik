package hr.bebindnevnik.app.security

import android.content.Context
import java.io.File
import java.time.Instant

object DatabaseRecoveryManager {
    fun preserveAndReset(context: Context): File {
        val database = context.getDatabasePath(DATABASE_NAME)
        val recovery = File(context.filesDir, "database-recovery/${Instant.now().toEpochMilli()}").apply { mkdirs() }
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).filter(File::exists).forEach { source ->
            val destination = File(recovery, source.name)
            source.copyTo(destination, overwrite = false)
            check(destination.length() == source.length()) { "Izvornu bazu nije moguće sigurno sačuvati." }
        }
        check(recovery.listFiles()?.isNotEmpty() == true) { "Izvorna baza nije pronađena." }
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm")).filter(File::exists).forEach { source ->
            check(source.delete()) { "Izvornu bazu nije moguće pripremiti za kontrolirani restore." }
        }
        DatabaseKeyManager(context).resetAfterPreservingDatabase()
        return recovery
    }

    private const val DATABASE_NAME = "bebin-dnevnik.db"
}
