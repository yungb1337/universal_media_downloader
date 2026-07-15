# Universal Downloader - Android Native App

This folder contains a complete, compilation-ready Android Studio project wrapper that runs the Python engine of `universal_downloader` natively inside a Kotlin app using **Chaquopy** and **Jetpack Compose**.

## Features Added

1. **Tab-Based Navigation**:
   - **Queue**: Add URLs manually, run queue downloads, pause/resume active downloads, and monitor progress bars.
   - **Completed**: Track finished downloads.
   - **Settings & Logs**: Configure directory paths, quality levels (highest res/audio only switches), and watch the engine's live terminal log in real time.
2. **Queue Pause & Resume**:
   - Spawns background worker tasks executing `android_downloader.py` for each download.
   - Pausing a download throws a clean Python interruption which forces `yt-dlp` to abort immediately and keep `.part` files on disk.
   - Resuming automatically triggers yt-dlp to pick up the existing `.part` files and continue the download from where it was suspended.
3. **Internal FFmpeg Kit**:
   - Integrates `ffmpeg-kit-android` to dynamically provide static FFmpeg binaries on Android for video/audio merging and MP3 transcoding.

---

## How to Import & Run in Android Studio

1. **Install Android Studio**: Ensure you have a modern version (Ladybug/Hedgehog) installed.
2. **Open Project**:
   - Open Android Studio.
   - Select **Open** and browse to this folder: `universal_downloader_android`.
3. **Gradle Sync**:
   - Android Studio will automatically start downloading dependencies (Compose libraries, Kotlin dependencies, and FFmpeg Kit).
   - Chaquopy will install its Python distribution during build.
4. **Compile & Run**:
   - Plug in an Android device with Developer Options enabled, or start a Virtual Emulator (AVD).
   - Press the **Run** button (green play icon) in the top toolbar to install the APK!

---

## Technical Notes

- **`curl_cffi` Limitation**: Because Android runs on ARM hardware and `curl_cffi` does not distribute precompiled ARM binaries, TLS impersonation is disabled in this build. It falls back to default Python HTTP connections.
- **Scoped Storage**: Remember to give the app permissions when prompted on run, and set the download path in the Settings tab to a folder inside `/storage/emulated/0/Download/` to avoid Android file writing blocks.
