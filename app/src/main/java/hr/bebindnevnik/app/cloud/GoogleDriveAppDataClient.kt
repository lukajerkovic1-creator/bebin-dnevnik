package hr.bebindnevnik.app.cloud

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long,
)

class GoogleDriveAppDataClient(
    private val accessToken: String,
) {
    fun list(): List<DriveBackupFile> {
        val fields = URLEncoder.encode("files(id,name,modifiedTime,size)", Charsets.UTF_8.name())
        val response = request("GET", "$API/files?spaces=appDataFolder&orderBy=modifiedTime%20desc&pageSize=100&fields=$fields")
        val files = JSONObject(String(response, Charsets.UTF_8)).getJSONArray("files")
        return List(files.length()) { index ->
            files.getJSONObject(index).run {
                DriveBackupFile(getString("id"), getString("name"), getString("modifiedTime"), optString("size", "0").toLong())
            }
        }.filter { it.name.startsWith(FILE_PREFIX) }
    }

    fun upload(bytes: ByteArray): DriveBackupFile {
        val boundary = "BebinDnevnik-${System.nanoTime()}"
        val name = "$FILE_PREFIX${java.time.Instant.now().toString().replace(':', '-')}.bdc"
        val body =
            ByteArrayOutputStream().use { output ->
                output.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                output.write(
                    JSONObject()
                        .put("name", name)
                        .put("parents", org.json.JSONArray().put("appDataFolder"))
                        .toString()
                        .toByteArray(),
                )
                output.write("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
                output.toByteArray()
            }
        val response = request("POST", "$UPLOAD/files?uploadType=multipart&fields=id,name,modifiedTime,size", body, "multipart/related; boundary=$boundary")
        return JSONObject(String(response, Charsets.UTF_8)).run {
            DriveBackupFile(getString("id"), getString("name"), getString("modifiedTime"), optString("size", bytes.size.toString()).toLong())
        }
    }

    fun download(id: String): ByteArray = request("GET", "$API/files/${encoded(id)}?alt=media")

    fun delete(id: String) {
        request("DELETE", "$API/files/${encoded(id)}")
    }

    fun retainNewest(limit: Int = 5) {
        CloudRetention.filesToDelete(list(), limit).forEach { delete(it.id) }
    }

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): ByteArray {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                    setFixedLengthStreamingMode(body.size)
                }
            }
        return try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail =
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                error("Google Drive je vratio HTTP $code${if (detail.isBlank()) "." else ": $detail"}")
            }
            connection.inputStream?.use { it.readBytes() } ?: ByteArray(0)
        } finally {
            connection.disconnect()
        }
    }

    private fun encoded(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_PREFIX = "BebinDnevnik-cloud-"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}

object CloudRetention {
    fun filesToDelete(
        newestFirst: List<DriveBackupFile>,
        limit: Int = 5,
    ): List<DriveBackupFile> {
        require(limit >= 1)
        return newestFirst.drop(limit)
    }
}
