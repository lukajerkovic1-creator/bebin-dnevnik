package hr.bebindnevnik.app.update

import hr.bebindnevnik.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
)

sealed interface UpdateCheckResult {
    data class Available(
        val update: AppUpdate,
    ) : UpdateCheckResult

    data class Current(
        val latestVersionName: String,
    ) : UpdateCheckResult

    data class Failed(
        val message: String,
    ) : UpdateCheckResult
}

object UpdateChecker {
    const val MANIFEST_URL = "https://lukajerkovic1-creator.github.io/bebin-dnevnik/version.json"

    suspend fun check(currentVersionCode: Int = BuildConfig.VERSION_CODE): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection =
                    (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                        useCaches = false
                    }
                try {
                    if (connection.responseCode !in 200..299) {
                        error("Poslužitelj je vratio odgovor ${connection.responseCode}.")
                    }
                    parse(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            }.fold(
                onSuccess = { update ->
                    if (update.versionCode > currentVersionCode) {
                        UpdateCheckResult.Available(update)
                    } else {
                        UpdateCheckResult.Current(update.versionName)
                    }
                },
                onFailure = {
                    UpdateCheckResult.Failed("Provjera nije uspjela. Provjerite internetsku vezu i pokušajte ponovno.")
                },
            )
        }

    internal fun parse(json: String): AppUpdate {
        val data = JSONObject(json)
        val versionCode = data.getInt("versionCode")
        val versionName = data.getString("versionName").trim()
        val downloadUrl = data.getString("downloadUrl").trim()
        val releaseNotes = data.optString("releaseNotes").trim()
        require(versionCode > 0) { "Neispravan broj verzije." }
        require(versionName.isNotEmpty()) { "Nedostaje naziv verzije." }
        val uri = URI(downloadUrl)
        require(uri.scheme == "https") { "Veza za preuzimanje mora koristiti HTTPS." }
        require(uri.host == "lukajerkovic1-creator.github.io") { "Veza za preuzimanje nije pouzdana." }
        return AppUpdate(versionCode, versionName, downloadUrl, releaseNotes)
    }
}
