# Implementation Plan - Individual Download Controls (Pause/Resume/Stop)

This plan replaces the global stop button with per-item controls in the active downloads list, allowing you to manage each download independently.

## User Review Required

> [!IMPORTANT]
> **Pause/Resume Behavior**: Since the Python engine (`yt-dlp`) doesn't have a native "suspend" mode, "Pause" will stop the current download process while keeping the partial data (`.part` files). "Resume" will start a new process that intelligently picks up where it left off.

> [!WARNING]
> **Stop/Dump Behavior**: Clicking "Stop" on an individual item will cancel the download and **strictly delete** all temporary and partial files associated with that specific video from your storage.

## Proposed Changes

### 1. Data Model Enhancements
#### [MODIFY] [DownloadState.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/data/model/DownloadState.kt)
- Add `PAUSED` and `WAITING` statuses to `DownloadStatus`.
- Update `DownloadProgress` to include the `url` so the UI can identify which task to control.

### 2. Job-Aware Control System
#### [MODIFY] [PythonBridge.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/engine/PythonBridge.kt)
- Replace the global `_shouldCancel` with a **Thread-Safe Map**: `urlStates: Map<String, JobState>`.
- Add methods: `setJobState(url, state)`, `getJobState(url)`.

#### [MODIFY] [download_bridge.py](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/python/download_bridge.py)
- Update `progress_hook` to check the status of its specific URL via the new bridge methods.
- If status is `STOPPED`, it will raise a `StopException` and the script will perform an immediate cleanup of `.part` files.
- If status is `PAUSED`, it will raise a `PauseException` to exit cleanly and preserve fragments.

### 3. UI Redesign
#### [MODIFY] [DownloadItemCard.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/components/DownloadItemCard.kt)
- Add a new **Control Row** inside the card:
    - **⏸️/▶️ Toggle**: Switches between Pause and Resume.
    - **⏹️ Stop**: Cancels and deletes the file.
- Add visual indicators for "Paused" state (dimmed progress bar).

### 4. ViewModel Logic
#### [MODIFY] [DownloadViewModel.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/viewmodel/DownloadViewModel.kt)
- Add `pauseDownload(url)`, `resumeDownload(url)`, and `stopDownload(url)` methods.
- `resumeDownload` will re-trigger the `DownloadWorker` for just that specific URL.

---

## Verification Plan

### Manual Verification
1.  **Individual Pause**: Start 2 downloads. Pause the first one. Verify the second one continues while the first one stays at its current percentage.
2.  **Individual Resume**: Click Resume on the paused download. Verify it starts from the previous percentage (resuming fragments).
3.  **Stop & Wipe**: Click Stop on a download. Verify the item disappears from the active list and check with a file manager that no `.part` files remain in `cache/download_work`.
4.  **Global App Closure**: Verify that swiping the app away still allows these controls to work via the WorkManager once the app is reopened.
