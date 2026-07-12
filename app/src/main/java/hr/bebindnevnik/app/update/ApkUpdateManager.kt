package hr.bebindnevnik.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import hr.bebindnevnik.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class ApkMetadata(
    val packageName: String,
    val versionCode: Long,
    val certificateSha256: String,
)

sealed interface ApkValidation {
    data object Valid : ApkValidation

    data class Invalid(
        val message: String,
    ) : ApkValidation
}

object ApkVerificationRules {
    fun validate(
        candidate: ApkMetadata,
        installed: ApkMetadata,
        expectedSha256: String,
    ): ApkValidation =
        when {
            candidate.packageName != PRODUCTION_PACKAGE -> ApkValidation.Invalid("APK pripada drugoj aplikaciji.")
            installed.packageName != PRODUCTION_PACKAGE -> ApkValidation.Invalid("Instalirana aplikacija nije produkcijska verzija.")
            candidate.versionCode <= installed.versionCode -> ApkValidation.Invalid("Preuzeta verzija nije novija od instalirane.")
            candidate.certificateSha256 != installed.certificateSha256 -> ApkValidation.Invalid("APK nije potpisan istim ključem kao instalirana aplikacija.")
            candidate.certificateSha256 != expectedSha256 -> ApkValidation.Invalid("APK nema očekivani produkcijski certifikat.")
            else -> ApkValidation.Valid
        }

    const val PRODUCTION_PACKAGE = "hr.bebindnevnik.app"
    const val PRODUCTION_CERT_SHA256 = "f1b4b84d9b0c729bd2ddf56309d581f0a541a80c822f18e69f2b3bbe659d2d5e"
}

class UpdateDownloadException(
    message: String,
) : Exception(message)

class ApkUpdateManager(
    private val context: Context,
) {
    suspend fun downloadAndVerify(
        update: AppUpdate,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val expectedHash = downloadText(update.checksumUrl).trim().substringBefore(' ').lowercase()
            require(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "Objavljeni SHA-256 nije valjan." }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "BebinDnevnik-${update.versionName}.apk")
            val partial = File(directory, "${target.name}.part")
            runCatching { downloadFile(update.apkUrl, partial, cancelled, onProgress) }
                .onFailure { partial.delete() }
                .getOrThrow()
            if (sha256(partial) != expectedHash) {
                partial.delete()
                throw UpdateDownloadException("SHA-256 provjera preuzetog APK-a nije prošla.")
            }
            val installed = packageMetadata(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
            val archive = archiveInfo(partial) ?: throw UpdateDownloadException("Preuzeta datoteka nije valjan APK.")
            when (val validation = ApkVerificationRules.validate(packageMetadata(archive), installed, ApkVerificationRules.PRODUCTION_CERT_SHA256)) {
                ApkValidation.Valid -> {
                    Unit
                }

                is ApkValidation.Invalid -> {
                    partial.delete()
                    throw UpdateDownloadException(validation.message)
                }
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Preuzeti APK nije moguće pripremiti." }
            target
        }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    private fun archiveInfo(file: File): PackageInfo? =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun packageMetadata(info: PackageInfo): ApkMetadata {
        val signing = requireNotNull(info.signingInfo) { "APK nema podatke o potpisu." }
        val signatures =
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        val fingerprint = sha256(signatures.first().toByteArray())
        return ApkMetadata(info.packageName, info.longVersionCode, fingerprint)
    }

    private fun downloadText(url: String): String = open(url).use { connection -> connection.inputStream.bufferedReader().use { it.readText() } }

    private fun downloadFile(
        url: String,
        target: File,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ) {
        open(url).use { connection ->
            copyResponse(connection, target, cancelled, onProgress)
        }
    }

    @Suppress("NestedBlockDepth") // Resource-safe use blocks deliberately surround the cancellable streaming loop.
    private fun copyResponse(
        connection: ConnectionResource,
        target: File,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ) = connection.inputStream.use { input ->
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                if (cancelled.get()) throw UpdateDownloadException("Preuzimanje je prekinuto.")
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                copied += count
                val total = connection.contentLengthLong
                if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
            }
        }
    }

    private fun open(url: String): ConnectionResource {
        var current = URL(url)
        repeat(6) {
            require(current.protocol == "https" && current.host in TRUSTED_HOSTS) { "Preusmjeravanje nije pouzdano." }
            val connection =
                (current.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "BebinDnevnik/${BuildConfig.VERSION_NAME}")
                }
            if (connection.responseCode in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw UpdateDownloadException("Nedostaje adresa preusmjeravanja.")
                connection.disconnect()
                current = URL(current, location)
            } else {
                if (connection.responseCode !in 200..299) {
                    val code = connection.responseCode
                    connection.disconnect()
                    throw UpdateDownloadException("Preuzimanje nije uspjelo (HTTP $code).")
                }
                return ConnectionResource(connection)
            }
        }
        throw UpdateDownloadException("Previše preusmjeravanja.")
    }

    private fun sha256(file: File): String = file.inputStream().use { input -> digest(input.readBytes()) }

    private fun sha256(bytes: ByteArray): String = digest(bytes)

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class ConnectionResource(
        val connection: HttpURLConnection,
    ) : AutoCloseable {
        val inputStream get() = connection.inputStream
        val contentLengthLong get() = connection.contentLengthLong

        override fun close() = connection.disconnect()
    }

    private companion object {
        val TRUSTED_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }
}
