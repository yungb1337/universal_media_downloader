# Implementation Plan - Fixing File Saving and Resume Reliability

The current issues (files not saving to MediaStore and Resume "not working") are caused by a synchronization mismatch and redundant processing logic. The `DownloadEngine.run` function returns immediately, causing background workers to die prematurely, and multiple workers (Batch + Resume) can sometimes conflict when trying to move the same file.

## User Review Required

> [!IMPORTANT]
> **Wait for Completion**: I will change `DownloadEngine.run` to be a synchronous suspend function. This ensures that the Background Worker and the File Mover stay alive until the downloads are actually finished and moved to your Downloads folder.
>
> **Centralized Moving**: I will move the "File Moving" logic to be a shared state in the Repository. This prevents a "Resume" worker and a "Batch" worker from trying to move the same file at the same time, which can cause "File not found" errors.

## Proposed Changes

### 1. Fix Engine Synchronization
#### [MODIFY] [DownloadEngine.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/engine/DownloadEngine.kt)
- Remove `scope.launch` from the `run` function.
- Make `run` a standard `suspend` function that waits for `runParallel` or `runSequential` to complete.
- This ensures the caller (Worker) stays active during the entire download process.

### 2. Centralized File Mover
#### [MODIFY] [DownloadRepository.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/data/repository/DownloadRepository.kt)
- Move `processedFiles` from a local variable to a class-level `ConcurrentHashMap`.
- This ensures that if a file is moved by one worker (e.g., a Resume task), another worker won't try to move it again.
- Move the `moveJob` collector into a more stable lifecycle or ensure it's properly joined.

### 3. Resume Parameter Alignment
#### [MODIFY] [DownloadViewModel.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/viewmodel/DownloadViewModel.kt)
- Ensure `resumeJob` correctly triggers the worker and provides all necessary context.

## Verification Plan

### Manual Verification
1.  **Direct Download Test**: Start a fresh download.
    - Verify: After 100%, the logs show "📂 Saving to Public Downloads...".
    - Verify: The file appears in `Downloads/Universal Downloader`.
2.  **Pause/Resume Test**: Start a download, pause it at 50%, then resume.
    - Verify: The status changes back to "Downloading".
    - Verify: Upon completion, the file is moved to the public folder.
3.  **Conflict Test**: Start a batch of 5. Pause 1. Let the others finish. Resume the 1st one while the others are being saved.
    - Verify: No "File not found" errors in the logs; each file is moved exactly once.
