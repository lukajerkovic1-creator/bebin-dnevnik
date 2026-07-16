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
    val apkUrl: String,
    val checksumUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
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
    const val RELEASE_API_URL = "https://api.github.com/repos/lukajerkovic1-creator/bebin-dnevnik/releases/latest"
    const val VERSION_URL = "https://lukajerkovic1-creator.github.io/bebin-dnevnik/version.json"
    private const val APK_ASSET = "BebinDnevnik.apk"
    private const val CHECKSUM_ASSET = "BebinDnevnik.apk.sha256"

    suspend fun check(currentVersionCode: Int = BuildConfig.VERSION_CODE): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val release = parseRelease(get(RELEASE_API_URL))
                val manifest = runCatching { parseVersionManifest(get(VERSION_URL)) }.getOrNull()
                val versionCode =
                    manifest?.takeIf { it.first == release.versionName }?.second
                        ?: semanticVersionCode(release.versionName)
                release.copy(versionCode = versionCode)
            }.fold(
                onSuccess = { update ->
                    if (update.versionCode > currentVersionCode) UpdateCheckResult.Available(update) else UpdateCheckResult.Current(update.versionName)
                },
                onFailure = {
                    UpdateCheckResult.Failed("Provjera nije uspjela. Provjerite internetsku vezu i pokušajte ponovno.")
                },
            )
        }

    internal fun parseRelease(json: String): AppUpdate {
        val data = JSONObject(json)
        require(!data.optBoolean("draft") && !data.optBoolean("prerelease")) { "Izdanje nije produkcijsko." }
        val versionName = data.getString("tag_name").removePrefix("v").trim()
        val assets = data.getJSONArray("assets")
        var apkUrl: String? = null
        var checksumUrl: String? = null
        repeat(assets.length()) { index ->
            val asset = assets.getJSONObject(index)
            when (asset.getString("name")) {
                APK_ASSET -> apkUrl = asset.getString("browser_download_url")
                CHECKSUM_ASSET -> checksumUrl = asset.getString("browser_download_url")
            }
        }
        validateAssetUrl(requireNotNull(apkUrl) { "Nedostaje $APK_ASSET." })
        validateAssetUrl(requireNotNull(checksumUrl) { "Nedostaje $CHECKSUM_ASSET." })
        return AppUpdate(
            versionCode = 0,
            versionName = versionName,
            apkUrl = apkUrl,
            checksumUrl = checksumUrl,
            releaseNotes = data.optString("body").trim(),
            publishedAt = data.optString("published_at").trim(),
        )
    }

    internal fun parseVersionManifest(json: String): Pair<String, Int> {
        val data = JSONObject(json)
        val name = data.getString("versionName").trim()
        val code = data.getInt("versionCode")
        require(name.isNotEmpty() && code > 0) { "Manifest verzije nije valjan." }
        return name to code
    }

    internal fun semanticVersionCode(versionName: String): Int {
        val match =
            Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(versionName.trim())
                ?: throw IllegalArgumentException("Oznaka verzije nije podržana.")
        val (major, minor, patch) = match.destructured
        val values = listOf(major, minor, patch).map(String::toInt)
        require(values[1] < 1_000 && values[2] < 1_000) { "Oznaka verzije nije podržana." }
        return Math.addExact(Math.addExact(Math.multiplyExact(values[0], 1_000_000), values[1] * 1_000), values[2])
    }

    private fun validateAssetUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host == "github.com") { "Asset nije na pouzdanoj GitHub adresi." }
        require(uri.path.contains("/releases/download/")) { "Asset nije dio GitHub Releasea." }
    }

    private fun get(url: String): String {
        val connection =
            (URL(url + (if ('?' in url) "&nocache=" else "?nocache=") + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BebinDnevnik/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                useCaches = false
            }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
