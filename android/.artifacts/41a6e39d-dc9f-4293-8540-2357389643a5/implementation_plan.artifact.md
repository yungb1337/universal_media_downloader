# Implementation Plan - Fixing Pause/Resume "Crash" and Improving Stability

The current implementation of Pause/Resume is functional but leads to a poor user experience. When a download is paused, it is treated as a terminal failure by the `DownloadEngine`, causing it to be removed from the active downloads list and counted as a "Failed" job in the summary. This makes the app appear to "crash" or fail when it is actually performing a requested pause.

## User Review Required

> [!IMPORTANT]
> **Persistent State**: The "Paused" state will be kept in memory within the `DownloadSessionState`. If the app is force-closed or the system kills the background process, this in-memory paused state will be lost. We are prioritizing fixing the active session behavior first.

## Proposed Changes

### Core Models & Bridge

#### [MODIFY] [DownloadResult.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/data/model/DownloadResult.kt)
- Add `isPaused: Boolean` and `isStopped: Boolean` fields to differentiate user-initiated stops from actual errors.

#### [MODIFY] [PythonBridge.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/engine/PythonBridge.kt)
- Update `parseDownloadResult` to populate the new `isPaused` and `isStopped` flags from the JSON returned by the Python bridge.

### Engine Logic

#### [MODIFY] [DownloadEngine.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/engine/DownloadEngine.kt)
- **Status Persistence**: In `downloadSingle`, if a download is paused, update its entry in `currentProgress` to `DownloadStatus.PAUSED` instead of removing it.
- **Summary Logic**: Update `printSummary` to exclude paused and stopped items from the "Failed" count and add a "Paused" count to the summary output.
- **Log Suppression**: Prevent the "❌ Failed" log from appearing when a download is merely paused or stopped by the user.

### Worker & Service

#### [MODIFY] [DownloadWorker.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/service/DownloadWorker.kt)
- Ensure the worker returns `Result.success()` even if the batch contains paused items, preventing system-level "Worker failed" messages.

## Verification Plan

### Automated Tests
- N/A (Manual verification on device is more effective for this UI/State flow).

### Manual Verification
1.  **Pause Test**: Start a download, click "Pause".
    - Verify: The item stays in the "Active Downloads" list.
    - Verify: The status changes to "(Paused)".
    - Verify: The "Resume" (Play) button appears.
    - Verify: The logs show "⏸️ Paused" and NOT "❌ Failed".
2.  **Resume Test**: Click "Resume" on the paused item.
    - Verify: The download picks up from the previous percentage (using `.part` files).
    - Verify: The status changes back to "Downloading".
3.  **Batch Pause**: Start 3 downloads. Pause the first one.
    - Verify: The engine continues to the second and third downloads while the first remains in the list as "Paused".
4.  **Summary Test**: Finish a batch with one paused item.
    - Verify: The summary shows "1 paused" and "0 failed".
