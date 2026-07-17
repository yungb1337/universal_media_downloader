# Implementation Plan - Interactive Playlist Selection

Implement a feature to analyze a playlist URL and allow the user to select specific videos for download via a modern Material 3 dialog.

## User Review Required

> [!IMPORTANT]
> This change introduces a new "Analyze" button. Clicking "Download" will still download everything in the text field as before. Clicking "Analyze" will specifically fetch playlist details for the first URL detected.

## Proposed Changes

### [Component Name] Python Backend
#### [MODIFY] [download_bridge.py](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/python/download_bridge.py)
- Add `get_playlist_info(url, cookies_file)`: Uses `yt-dlp` with `extract_flat=True` to fetch metadata (titles, URLs) for all entries in a playlist.

### [Component Name] Data Layer
#### [NEW] [PlaylistEntry.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/data/model/PlaylistEntry.kt)
- Define `PlaylistEntry` data class: `val url: String`, `val title: String`, `val isSelected: Boolean`.

#### [MODIFY] [PythonBridge.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/engine/PythonBridge.kt)
- Add `getPlaylistInfo(url, cookiesFile)` to call the Python backend and parse results.

### [Component Name] UI Layer
#### [NEW] [PlaylistSelectionDialog.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/components/PlaylistSelectionDialog.kt)
- Implement a `PlaylistSelectionDialog` with:
    - LazyColumn for the video list.
    - Checkboxes for each item.
    - "Select All" / "Deselect All" logic.
    - "Download" button.

#### [MODIFY] [DownloadViewModel.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/viewmodel/DownloadViewModel.kt)
- Add state for:
    - `isAnalyzing`: Boolean flag for loading state.
    - `playlistEntries`: List of `PlaylistEntry` to show in the dialog.
    - `showPlaylistDialog`: Boolean flag to show/hide the dialog.
- Add `analyzePlaylist(url, settings)`: Fetch metadata.
- Add `startSelectedDownloads(settings)`: Add selected items to the download queue.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/screens/HomeScreen.kt)
- Add the "Analyze" button next to "Download".
- Show the `PlaylistSelectionDialog` when `showPlaylistDialog` is true.

## Verification Plan

### Manual Verification
- **Playlist Link**: Paste a YouTube playlist link.
- **Analyze**: Click "Analyze". Verify the loading state and that the dialog pops up with the correct list of videos.
- **Selection**: Select a subset of videos and click "Download".
- **Execution**: Verify that only the selected videos are processed.
