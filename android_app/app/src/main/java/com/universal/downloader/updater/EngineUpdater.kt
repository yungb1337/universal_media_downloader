package com.universal.downloader.updater

import android.content.Context
import android.util.Log
import com.universal.downloader.engine.PythonEngineManager
import com.universal.downloader.model.EngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class EngineUpdater(private val context: Context) {

    private val engineManager = PythonEngineManager.getInstance(context)
    private val updatesDir = File(context.filesDir, "python_updates").apply { mkdirs() }

    private val _status = MutableStateFlow(
        EngineStatus(
            version = engineManager.getEngineVersion(),
            location = engineManager.getEnginePath(),
            isCustomUpdated = hasCustomWheels()
        )
    )
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    fun hasCustomWheels(): Boolean {
        val wheels = updatesDir.listFiles { _, name -> name.endsWith(".whl") }
        return !wheels.isNullOrEmpty()
    }

    suspend fun checkForUpdates(): Result<Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        try {
            _status.value = _status.value.copy(isUpdating = true, updateLog = "Checking PyPI for latest releases...")

            val currentVersion = engineManager.getEngineVersion().trim()
            val url = URL("https://pypi.org/pypi/yt-dlp/json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "UniversalDownloader-Android")
            }

            if (connection.responseCode != 200) {
                throw Exception("HTTP ${connection.responseCode} while querying PyPI")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseBody)
            val info = root.getJSONObject("info")
            val latestVersion = info.getString("version").trim()

            val hasUpdate = isVersionNewer(latestVersion, currentVersion)
            _status.value = _status.value.copy(
                isUpdating = false,
                lastCheckedTimestamp = System.currentTimeMillis(),
                updateAvailable = hasUpdate,
                latestVersionAvailable = latestVersion,
                updateLog = if (hasUpdate) "Update available: $latestVersion (current: $currentVersion)" else "Engine is up to date ($currentVersion)"
            )

            Result.success(Pair(hasUpdate, latestVersion))
        } catch (e: Exception) {
            Log.e(TAG, "Failed checking for updates", e)
            _status.value = _status.value.copy(
                isUpdating = false,
                updateLog = "Update check failed: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }

    suspend fun downloadAndApplyUpdate(targetVersion: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _status.value = _status.value.copy(isUpdating = true, updateLog = "Fetching wheel download URL for v$targetVersion...")

            val url = URL("https://pypi.org/pypi/yt-dlp/json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseBody)
            val releases = root.getJSONObject("releases")
            val urlsForVersion = releases.optJSONArray(targetVersion)
                ?: throw Exception("No release packages found for version $targetVersion")

            var wheelUrl: String? = null
            var wheelFilename: String? = null

            for (i in 0 until urlsForVersion.length()) {
                val item = urlsForVersion.getJSONObject(i)
                val packagetype = item.optString("packagetype")
                val filename = item.optString("filename")
                if (packagetype == "bdist_wheel" && filename.endsWith(".whl")) {
                    wheelUrl = item.getString("url")
                    wheelFilename = filename
                    break
                }
            }

            if (wheelUrl == null || wheelFilename == null) {
                throw Exception("Could not find standard .whl package for version $targetVersion")
            }

            _status.value = _status.value.copy(updateLog = "Downloading $wheelFilename...")

            val downloadConn = (URL(wheelUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
            }

            val targetFile = File(updatesDir, wheelFilename)
            val tempFile = File(updatesDir, "$wheelFilename.tmp")

            downloadConn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            } else {
                throw Exception("Downloaded file is empty")
            }

            _status.value = _status.value.copy(updateLog = "Applying update and reloading engine...")

            // Reload python backend to mount the new wheel
            engineManager.reloadEngine()

            // Cleanup old wheels
            updatesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".whl") && file.name != wheelFilename) {
                    file.delete()
                }
            }

            val newVersion = engineManager.getEngineVersion()
            _status.value = _status.value.copy(
                isUpdating = false,
                version = newVersion,
                location = engineManager.getEnginePath(),
                isCustomUpdated = true,
                updateAvailable = false,
                updateLog = "Successfully updated engine to v$newVersion"
            )

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Update application failed", e)
            _status.value = _status.value.copy(
                isUpdating = false,
                updateLog = "Engine update failed: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }

    suspend fun resetToBundledEngine(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            updatesDir.listFiles()?.forEach { it.delete() }
            engineManager.reloadEngine()
            _status.value = _status.value.copy(
                version = engineManager.getEngineVersion(),
                location = engineManager.getEnginePath(),
                isCustomUpdated = false,
                updateLog = "Engine reset to bundled default"
            )
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(newVer: String, currentVer: String): Boolean {
        if (newVer == currentVer) return false
        val newParts = newVer.split(".").mapNotNull { it.toIntOrNull() }
        val curParts = currentVer.split(".").mapNotNull { it.toIntOrNull() }

        val len = maxOf(newParts.size, curParts.size)
        for (i in 0 until len) {
            val n = newParts.getOrElse(i) { 0 }
            val c = curParts.getOrElse(i) { 0 }
            if (n > c) return true
            if (n < c) return false
        }
        return false
    }

    companion object {
        private const val TAG = "EngineUpdater"
    }
}
