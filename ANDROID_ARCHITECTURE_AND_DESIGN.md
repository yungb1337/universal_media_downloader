# Universal Downloader for Android — Architecture & Future-Proof Engineering Blueprint

> **Complete Android System Design, Dynamic Engine Updater, Material 3 UI, and Background Pipeline**  
> *Target Android Versions:* Android 8.0 (API 26) through Android 15 (API 35+)  
> *Engine Model:* Native Kotlin + Embedded Python (Chaquopy) + QuickJS JNI + In-App OTA Wheel Updater

---

## Table of Contents

1. [Executive Summary & Core Philosophy](#1-executive-summary--core-philosophy)
2. [High-Level System Architecture](#2-high-level-system-architecture)
3. [The Future-Proofing Dynamic Engine (Zero-APK Update)](#3-the-future-proofing-dynamic-engine-zero-apk-update)
4. [Embedded JS Challenge Solver (QuickJS JNI + EJS)](#4-embedded-js-challenge-solver-quickjs-jni--ejs)
5. [User Interface & UX Design (Jetpack Compose Material 3)](#5-user-interface--ux-design-jetpack-compose-material-3)
6. [Background Download Service & Scoped Storage Pipeline](#6-background-download-service--scoped-storage-pipeline)
7. [In-App Cookie Authentication (WebView Session Bridge)](#7-in-app-cookie-authentication-webview-session-bridge)
8. [Complete Project File Structure](#8-complete-project-file-structure)
9. [Automated Agentic Workflow Definition](#9-automated-agentic-workflow-definition)

---

## 1. Executive Summary & Core Philosophy

### The "Cat-and-Mouse" Problem
YouTube and other streaming sites continuously deploy updates to their player JavaScript and format streaming protocols (such as `n-sig` transformations, Proof-of-Origin / PO tokens, and SABR streams). Consequently, desktop or mobile apps with hardcoded or bundled extractor binaries fail with `HTTP 403 Forbidden` within weeks.

### The Solution: Decoupled Dynamic Architecture
This architecture decouples the **Android Application Shell (Kotlin/Jetpack Compose)** from the **Dynamic Extraction Engine (Python/yt-dlp + QuickJS)**:
- **Zero-APK Updates for Core Fixes:** Whenever YouTube changes their player code, `yt-dlp` publishes a patch. The Android app checks for new wheels (`.whl`), downloads them directly to internal storage, and injects them into Python's `sys.path` on the fly.
- **Dynamic JavaScript Challenge Solver:** An embedded native QuickJS engine executes YouTube's player JavaScript dynamically, paired with GitHub EJS remote challenge solvers (`remote_components=["ejs:github"]`).
- **Complete Android System Integration:** Modern Material 3 UI, Scoped Storage / `MediaStore` audio/video registry, and foreground notification download tracking with battery optimization bypass.

---

## 2. High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ANDROID HOST APPLICATION                           │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    Jetpack Compose UI (Material 3)                    │  │
│  │  - Dashboard / Queue Screen          - Batch Link Editor              │  │
│  │  - Quality & Format Pills            - Engine Diagnostics Sheet       │  │
│  │  - In-App Cookie Auth WebView        - Live Log Terminal Modal        │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
│                                     │ StateFlow / Intents                   │
│  ┌──────────────────────────────────▼────────────────────────────────────┐  │
│  │                      DownloadViewModel / UI State                     │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
│                                     │                                       │
│  ┌──────────────────────────────────▼────────────────────────────────────┐  │
│  │              DownloadManager & Foreground Service                     │  │
│  │  - Notification Controller (Progress, ETA, Speed, Cancel)            │  │
│  │  - Concurrent Worker Pool (Kotlin Coroutines)                         │  │
│  │  - MediaStore Writer (Scoped Storage: Music/ & Movies/)               │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
│                                     │ JNI Bridge                            │
├─────────────────────────────────────┼───────────────────────────────────────┤
│                                     │                                       │
│  ┌──────────────────────────────────▼────────────────────────────────────┐  │
│  │                 Python Embedded Runtime (Chaquopy)                    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │ Dynamic Wheel Loader (sys.path injection)                       │  │  │
│  │  │ Priority: 1. Downloaded Updates (.whl) -> 2. Bundled Assets     │  │  │
│  │  └────────────────────────────────┬────────────────────────────────┘  │  │
│  │                                   │                                   │  │
│  │  ┌────────────────────────────────▼────────────────────────────────┐  │  │
│  │  │ yt-dlp Engine Core                                              │  │  │
│  │  │ - Extractor Plugins & URL Sanitization (YouTube Mix Cleaning)   │  │  │
│  │  │ - Impersonation / TLS Fingerprinting (Chrome/Safari)            │  │  │
│  │  │ - Dynamic EJS Solver (`ejs:github` remote challenge fetch)      │  │  │
│  │  └────────────────────────────────┬────────────────────────────────┘  │  │
│  │                                   │                                   │  │
│  │  ┌────────────────────────────────▼────────────────────────────────┐  │  │
│  │  │ Native QuickJS JNI Engine (JavaScript Challenge Runtime)        │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
│                                     │                                       │
└─────────────────────────────────────┼───────────────────────────────────────┘
                                      │ Network I/O
                                      ▼
                      ┌───────────────────────────────┐
                      │ External Endpoints:           │
                      │ - Media Streams (Googlevideo) │
                      │ - PyPI / GitHub API (Updates) │
                      │ - EJS GitHub Challenge Solvers│
                      └───────────────────────────────┘
```

---

## 3. The Future-Proofing Dynamic Engine (Zero-APK Update)

### 3.1 Over-The-Air Wheel Injector
The Android application downloads pure-Python wheels (`yt_dlp-YYYY.MM.DD-py3-none-any.whl`) directly from PyPI or GitHub Releases to the app's protected internal storage directory (`context.filesDir/python_updates/`).

#### Python Dynamic Module Loader (`engine_loader.py`):
```python
import sys
import os
import glob
import logging

logger = logging.getLogger("universal_downloader_engine")

def initialize_engine(updates_dir: str, quickjs_bin_path: str = None) -> dict:
    """
    Scans internal app storage for dynamically updated yt-dlp wheels.
    Dynamically injects the newest wheel to the front of sys.path.
    """
    if os.path.exists(updates_dir):
        wheels = glob.glob(os.path.join(updates_dir, "yt_dlp*.whl"))
        if wheels:
            # Sort wheels to pick the newest version
            wheels.sort(reverse=True)
            latest_wheel = wheels[0]
            if latest_wheel not in sys.path:
                sys.path.insert(0, latest_wheel)
                logger.info(f"Loaded dynamic yt-dlp wheel: {latest_wheel}")

    import yt_dlp
    
    return {
        "ytdlp_version": yt_dlp.version.__version__,
        "wheel_source": sys.path[0] if sys.path[0].endswith(".whl") else "bundled",
        "quickjs_available": bool(quickjs_bin_path and os.path.exists(quickjs_bin_path)),
    }
```

### 3.2 Kotlin OTA Engine Updater (`EngineUpdater.kt`)
```kotlin
package com.universal.downloader.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class EngineUpdater(private val context: Context) {

    private val updatesDir = File(context.filesDir, "python_updates").apply { mkdirs() }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://pypi.org/pypi/yt-dlp/json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val latestVersion = json.getJSONObject("info").getString("version")
            
            val releases = json.getJSONObject("releases").getJSONArray(latestVersion)
            var wheelUrl: String? = null
            var wheelSha256: String? = null

            for (i in 0 until releases.length()) {
                val fileObj = releases.getJSONObject(i)
                val filename = fileObj.getString("filename")
                if (filename.endsWith("py3-none-any.whl")) {
                    wheelUrl = fileObj.getString("url")
                    wheelSha256 = fileObj.getJSONObject("digests").getString("sha256")
                    break
                }
            }

            if (wheelUrl != null) {
                UpdateCheckResult.UpdateAvailable(
                    latestVersion = latestVersion,
                    downloadUrl = wheelUrl,
                    sha256 = wheelSha256
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to check PyPI")
        }
    }

    suspend fun downloadWheel(downloadUrl: String, version: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(updatesDir, "yt_dlp-$version-py3-none-any.whl")
            if (targetFile.exists()) return@withContext true

            val url = URL(downloadUrl)
            url.openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val latestVersion: String, val downloadUrl: String, val sha256: String?) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
```

---

## 4. Embedded JS Challenge Solver (QuickJS JNI + EJS)

YouTube signatures require executing player JavaScript for `n` challenge algorithms.

### 4.1 Engine Options Configuration in Python:
```python
def build_engine_opts(output_path: str, quickjs_bin: str, is_audio: bool) -> dict:
    opts = {
        "outtmpl": output_path,
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "windowsfilenames": False,
        "impersonate": "chrome",
        "remote_components": ["ejs:github"],
        "retries": 10,
        "fragment_retries": 10,
    }
    
    if quickjs_bin and os.path.exists(quickjs_bin):
        opts["js_runtimes"] = {"quickjs": {"path": quickjs_bin}}
        
    if is_audio:
        opts["format"] = "bestaudio"
        opts["postprocessors"] = [{
            "key": "FFmpegExtractAudio",
            "preferredcodec": "mp3",
            "preferredquality": "320",
        }]
    else:
        opts["format"] = "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
        opts["merge_output_format"] = "mp4"

    return opts
```

---

## 5. User Interface & UX Design (Jetpack Compose Material 3)

### 5.1 UI Layout Preview

```
┌─────────────────────────────────────────────────────────┐
│  ⚡ Universal Downloader       [yt-dlp v2026.8.19]  ⚙️   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ https://www.youtube.com/watch?v=...     [📋 PASTE]│  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
│   Format:                                               │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐  │
│  │ ● MP3 320kbps │ │ ○ 1080p Video │ │ ○ Max Quality │  │
│  └───────────────┘ └───────────────┘ └───────────────┘  │
│                                                         │
│  [  ⚡ DOWNLOAD NOW  ]       [ 📋 BATCH PASTE (links.txt) ]│
│                                                         │
├─────────────────────────────────────────────────────────┤
│  ACTIVE QUEUE (2 items)                                 │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 🎵 Frozy - Parano (Lyrics) ft. DDB                │  │
│  │ [████████████████████░░░░░░░] 74% • 14.2 MB/s     │  │
│  │ 4.2 MB / 5.7 MB • ETA: 00:02         [⏸] [✖]      │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 🎬 Daft Punk - Instant Crush                      │  │
│  │ [██████████████████████████] 100% • COMPLETE      │  │
│  │ 84.1 MB • Saved to Music/UniversalDownloader  [▶] │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 5.2 Main Dashboard Composable (`HomeScreen.kt`)
```kotlin
package com.universal.downloader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.universal.downloader.ui.viewmodel.DownloadItemState
import com.universal.downloader.ui.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DownloadViewModel,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputUrl by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(DownloadFormat.AUDIO_MP3_320) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Downloader", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    AssistChip(
                        onClick = onOpenSettings,
                        label = { Text("v${uiState.engineVersion}") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) }
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // URL Input Field
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                label = { Text("Paste Video or Playlist URL") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.pasteFromClipboard { inputUrl = it } }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Format Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFormat == DownloadFormat.AUDIO_MP3_320,
                    onClick = { selectedFormat = DownloadFormat.AUDIO_MP3_320 },
                    label = { Text("MP3 320k") }
                )
                FilterChip(
                    selected = selectedFormat == DownloadFormat.VIDEO_1080P,
                    onClick = { selectedFormat = DownloadFormat.VIDEO_1080P },
                    label = { Text("1080p Video") }
                )
                FilterChip(
                    selected = selectedFormat == DownloadFormat.VIDEO_MAX,
                    onClick = { selectedFormat = DownloadFormat.VIDEO_MAX },
                    label = { Text("Max Quality") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            viewModel.startDownload(inputUrl, selectedFormat)
                            inputUrl = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download Now")
                }
                
                OutlinedButton(onClick = { viewModel.openBatchDialog() }) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Batch")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Downloads Queue", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Download List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.queueItems, key = { it.id }) { item ->
                    DownloadCard(item = item, onCancel = { viewModel.cancelDownload(item.id) })
                }
            }
        }
    }
}

@Composable
fun DownloadCard(item: DownloadItemState, onCancel: () -> Unit) {
    val animatedProgress by animateFloatAsState(targetValue = item.progressFraction, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(item.progressFraction * 100).toInt()}% • ${item.speedFormatted}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "ETA: ${item.etaFormatted}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

enum class DownloadFormat {
    AUDIO_MP3_320,
    VIDEO_1080P,
    VIDEO_MAX
}
```

---

## 6. Background Download Service & Scoped Storage Pipeline

### 6.1 Foreground Service (`DownloadService.kt`)
```kotlin
package com.universal.downloader.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.universal.downloader.R

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "universal_downloader_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Preparing download...", 0)
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    fun updateProgress(title: String, percent: Int, speed: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$percent% • $speed")
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Universal Downloader")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setProgress(100, progress, true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Download Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows real-time progress for active downloads"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

### 6.2 Scoped Storage Integration (`MediaStoreManager.kt`)
```kotlin
package com.universal.downloader.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

class MediaStoreManager(private val context: Context) {

    fun openMediaOutputStream(filename: String, isAudio: Boolean): Pair<Uri, OutputStream> {
        val collection = if (isAudio) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val relativePath = if (isAudio) {
            "${Environment.DIRECTORY_MUSIC}/UniversalDownloader"
        } else {
            "${Environment.DIRECTORY_MOVIES}/UniversalDownloader"
        }

        val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to create MediaStore record")

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Failed to open output stream for URI: $uri")

        return Pair(uri, outputStream)
    }

    fun finishMediaWrite(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }
}
```

---

## 7. In-App Cookie Authentication (WebView Session Bridge)

For age-restricted content or sites requiring authentication:

```kotlin
package com.universal.downloader.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun CookieLoginWebView(
    targetUrl: String = "https://accounts.google.com/ServiceLogin?service=youtube",
    cookiesOutputFile: File,
    onAuthComplete: () -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (cookies != null && (cookies.contains("SID=") || cookies.contains("SSID="))) {
                            exportNetscapeCookies(cookies, cookiesOutputFile)
                            onAuthComplete()
                        }
                    }
                }
                loadUrl(targetUrl)
            }
        }
    )
}

fun exportNetscapeCookies(rawCookieHeader: String, destination: File) {
    val lines = mutableListOf("# Netscape HTTP Cookie File", "# Exported by Universal Downloader Android")
    rawCookieHeader.split(";").forEach { pair ->
        val parts = pair.trim().split("=", limit = 2)
        if (parts.size == 2) {
            lines.add(".youtube.com\tTRUE\t/\tTRUE\t2147483647\t${parts[0]}\t${parts[1]}")
        }
    }
    destination.writeText(lines.joinToString("\n"))
}
```

---

## 8. Complete Project File Structure

```
android_app/
├── app/
│   ├── build.gradle.kts                   # Chaquopy, Compose & Material 3 configs
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml        # Foreground services & network permissions
│   │   │   ├── cpp/                       # Native QuickJS JNI library
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   └── quickjs_wrapper.c
│   │   │   ├── java/com/universal/downloader/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── engine/
│   │   │   │   │   ├── PythonEngineManager.kt
│   │   │   │   │   └── QuickJsRuntime.kt
│   │   │   │   ├── updater/
│   │   │   │   │   └── EngineUpdater.kt
│   │   │   │   ├── storage/
│   │   │   │   │   └── MediaStoreManager.kt
│   │   │   │   ├── service/
│   │   │   │   │   ├── DownloadService.kt
│   │   │   │   │   └── NotificationHelper.kt
│   │   │   │   └── ui/
│   │   │   │       ├── screens/
│   │   │   │       │   ├── HomeScreen.kt
│   │   │   │       │   ├── EngineSettingsScreen.kt
│   │   │   │       │   └── CookieLoginWebView.kt
│   │   │   │       ├── viewmodel/
│   │   │   │       │   └── DownloadViewModel.kt
│   │   │   │       └── theme/
│   │   │   │           ├── Color.kt
│   │   │   │           ├── Theme.kt
│   │   │   │           └── Type.kt
│   │   │   └── python/                    # Python engine source code
│   │   │       ├── engine_loader.py
│   │   │       ├── backend.py
│   │   │       └── helpers.py
│   │   └── res/
│   │       ├── drawable/
│   │       └── values/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 9. Automated Agentic Workflow Definition

The engineering lifecycle for this project is managed by autonomous subagents defined in `.claude/agents/`:

| Agent Role | Responsibility in Android Pipeline |
|---|---|
| **`research-lead`** | Researches YouTube algorithm changes, PyPI releases, and Android API changes. |
| **`chief-architect`** | Maintains ADRs, verifies contract stability between Kotlin UI and Python engine. |
| **`technical-planner`** | Converts architectural goals into phased task graphs. |
| **`implementation-lead`** | Coordinates frontend (Compose) and backend (Python/JNI) code deliverables. |
| **`verification-lead`** | Runs static analysis, verifies Scoped Storage security, and validates wheel updates. |
| **`release-engineer`** | Creates versioned checkpoints, artifacts, and release notes. |
| **`knowledge-curator`** | Updates `project_memory/` and maintains system integrity. |

---
*Created by Autonomous Dev-Team Architecture Pipeline (Run ID: run_20261003_150000)*
