package hr.bebindnevnik.app.cloud

import android.content.Context
import androidx.core.content.edit

data class CloudBackupStatus(
    val enabled: Boolean,
    val accountEmail: String?,
    val lastSuccess: String?,
    val lastError: String?,
    val dirty: Boolean,
)

class CloudBackupPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun status(): CloudBackupStatus =
        CloudBackupStatus(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            accountEmail = preferences.getString(KEY_ACCOUNT, null),
            lastSuccess = preferences.getString(KEY_LAST_SUCCESS, null),
            lastError = preferences.getString(KEY_LAST_ERROR, null),
            dirty = preferences.getBoolean(KEY_DIRTY, false),
        )

    fun enable(accountEmail: String) =
        preferences.edit(commit = true) {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_ACCOUNT, accountEmail)
            putBoolean(KEY_DIRTY, true)
            remove(KEY_LAST_ERROR)
        }

    fun markDirty() = preferences.edit { putBoolean(KEY_DIRTY, true) }

    fun recordSuccess(instant: String) =
        preferences.edit(commit = true) {
            putString(KEY_LAST_SUCCESS, instant)
            putBoolean(KEY_DIRTY, false)
            remove(KEY_LAST_ERROR)
        }

    fun recordError(message: String) = preferences.edit { putString(KEY_LAST_ERROR, message) }

    fun disable() =
        preferences.edit(commit = true) {
            putBoolean(KEY_ENABLED, false)
            putBoolean(KEY_DIRTY, false)
        }

    fun disconnect() = preferences.edit(commit = true) { clear() }

    private companion object {
        const val PREFS = "cloud_backup_status"
        const val KEY_ENABLED = "enabled"
        const val KEY_ACCOUNT = "account"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_DIRTY = "dirty"
    }
}
