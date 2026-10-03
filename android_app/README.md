# Universal Downloader - Modern Future-Proof Android App

A production-grade, self-updating universal media downloader for Android (API 26–35). Built with **Jetpack Compose (Material 3)**, **Chaquopy CPython 3.11**, and an **OTA Dynamic Wheel Injection Engine** that permanently solves YouTube and social media extractor breakages without requiring full APK re-installations.

---

## 🌟 Key Features

1. **Self-Updating Extraction Core (Zero-APK OTA Updating)**
   - Queries PyPI / GitHub Releases dynamically.
   - Downloads pure-python `yt_dlp-*.whl` wheel packages straight into `context.filesDir/python_updates/`.
   - Injects the wheel at runtime into `sys.path[0]` without rebuilding or reinstalling the APK.
   
2. **Modern Jetpack Compose UI (Material 3)**
   - Unidirectional Data Flow (UDF) powered by Kotlin Coroutines & `StateFlow`.
   - Material You dynamic theming with dark-mode first contrast styling.
   - Live download cards with animated progress indicators, download speeds, and ETAs.

3. **Background Download Service**
   - Android `ForegroundService` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
   - Low-importance notification channel with live progress updates.
   - `PARTIAL_WAKE_LOCK` to ensure large 4K downloads or playlist batches don't stop when screen sleeps.

4. **Android Scoped Storage & MediaStore Integration**
   - Automatically saves video streams to `Movies/UniversalDownloader` and audio to `Music/UniversalDownloader`.
   - Uses `IS_PENDING = 1` while streaming to prevent half-downloaded corrupt files from appearing in galleries.

5. **Advanced YouTube URL Cleaning & Cookie Authentication**
   - Automatically sanitizes transient YouTube Radio / Mix URLs (`list=RD...`, `start_radio=1`, `index=...`, `rv=...`) so individual songs download instantly without stalling on 50-track mixes.
   - Integrated WebView cookie exporter for age-restricted streams and private playlists.

---

## 🏗️ Architecture Overview

```
android_app/
├── app/
│   ├── src/main/
│   │   ├── java/com/universal/downloader/
│   │   │   ├── UniversalDownloaderApp.kt     # Application entrypoint & engine warmup
│   │   │   ├── MainActivity.kt               # Jetpack Compose Navigation & Share Intent receiver
│   │   │   ├── engine/
│   │   │   │   ├── PythonEngineManager.kt    # JNI Chaquopy Bridge & Downloader caller
│   │   │   │   └── QuickJsRuntime.kt         # JavaScript challenge solver bridge
│   │   │   ├── updater/
│   │   │   │   └── EngineUpdater.kt          # Dynamic wheel downloader & updater
│   │   │   ├── storage/
│   │   │   │   └── MediaStoreManager.kt      # Scoped Storage MediaStore API (Android 10-15)
│   │   │   ├── service/
│   │   │   │   ├── DownloadService.kt        # ForegroundService & WakeLock manager
│   │   │   │   └── NotificationHelper.kt     # Low-noise notification channel
│   │   │   ├── model/
│   │   │   │   └── DownloadModels.kt         # Data classes & state definitions
│   │   │   └── ui/
│   │   │       ├── screens/                  # HomeScreen, EngineSettingsScreen, CookieLoginWebView
│   │   │       ├── components/               # DownloadCard, BatchLinkDialog
│   │   │       ├── theme/                    # Theme, Color, Type
│   │   │       └── viewmodel/                # DownloadViewModel
│   │   └── python/
│   │       ├── engine_loader.py              # Dynamic sys.path wheel injection loader
│   │       ├── backend.py                    # yt-dlp downloader core
│   │       └── helpers.py                    # URL sanitization & format utilities
│   ├── build.gradle.kts                      # Chaquopy & Compose build config
│   └── proguard-rules.pro                   # ProGuard rules for Chaquopy & JNI
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Building & Running

### Prerequisites
- **Android Studio Jellyfish | Ladybug (2024.1+)** or newer.
- **JDK 17** (configured in Android Studio).
- **Android SDK 35** and **NDK 26+**.

### Build APK
1. Open `android_app` in Android Studio.
2. Let Gradle sync dependencies (Chaquopy will download Python 3.11 runtime automatically).
3. Run or Build:
   ```bash
   ./gradlew assembleDebug
   ```
4. Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔄 How the Zero-APK Updater Works

When YouTube introduces player signature changes (resulting in 403 Forbidden errors):
1. Navigate to **Settings** (top right gear icon in the app).
2. Tap **Check for Updates**.
3. Tap **Download & Apply In-Memory Update**.
4. The app pulls the latest `yt-dlp` package from PyPI, writes it to `filesDir/python_updates/`, injects it at the top of `sys.path`, and invalidates the Python module cache.
5. All subsequent downloads immediately use the new extractor code with zero APK re-installation!
