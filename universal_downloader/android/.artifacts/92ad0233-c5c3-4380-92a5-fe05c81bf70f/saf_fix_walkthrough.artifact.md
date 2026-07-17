# Walkthrough - SAF Directory Fix

I have resolved the issue where picking a folder in the Downloads directory caused a "Failed to create download directory" error.

## The Problem
Android's Storage Access Framework (SAF) uses virtual URIs (like `content://...`) instead of real file paths. The download engine (yt-dlp) and standard Java `File` APIs cannot write directly to these virtual URIs.

## The Solution: Download-then-Move
I implemented a robust strategy to bridge this gap:

1.  **Temporary Download**: The app now downloads files to a temporary internal "work" folder first. This allows the yt-dlp engine to work with standard file paths.
2.  **Real-time Move**: As soon as a download completes, the [DownloadRepository](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/data/repository/DownloadRepository.kt) detects the new file and moves it to your selected SAF folder using the Android system's content resolver.
3.  **Friendly UI**: The Settings screen now shows the human-readable name of the folder you picked (e.g., "Downloads") instead of the long URI string.

## Changes Made
- **[FileUtils.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/util/FileUtils.kt)**: Added `moveFileToSafUri` and `getDisplayName` helpers.
- **[DownloadRepository.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/data/repository/DownloadRepository.kt)**: Implemented the move logic.
- **[SettingsScreen.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/screens/SettingsScreen.kt)**: Updated to display friendly folder names.

## Verification
- Deployed the updated app to your device.
- Please try picking your folder again and running a download. It should now show "Moving to final folder" in the logs and succeed!
