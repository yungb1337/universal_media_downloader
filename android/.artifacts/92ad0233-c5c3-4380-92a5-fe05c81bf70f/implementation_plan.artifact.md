# Implementation Plan - Rebuilding "Analyze" from Scratch

This plan removes the buggy existing analysis logic and implements a clean, robust "Deep Analysis" system from the ground up.

## User Review Required

> [!IMPORTANT]
> **Performance Trade-off**: Rebuilding this feature to show quality for *every* video in a playlist means we must perform a "Deep Extraction." This will take ~2-3 seconds per video. I will limit the initial scan to the first **20 videos** to keep the app responsive.

> [!TIP]
> **The Goal**: Every item in the list will have its own dropdown, pre-selected to **1080p** (or the next best thing), with a clear option to switch to **"High Quality Audio"** instead.

## Proposed Changes

### 1. New Python Core
#### [MODIFY] [download_bridge.py](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/python/download_bridge.py)
- **Delete** the old `get_playlist_info`.
- **Implement** a new version that:
  - Detects Single Video vs. Playlist.
  - For each item, scans all `formats`.
  - Cleans labels: Replaces `1920x1080` with `1080p`.
  - Categorizes into `video` and `audio` types.
  - Returns a clean, flat JSON structure.

### 2. Smart "Brain" in ViewModel
#### [MODIFY] [DownloadViewModel.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/viewmodel/DownloadViewModel.kt)
- **New Selection Algorithm**:
  1. Priority 1: Find a video format containing `1080p`.
  2. Priority 2: Find the highest video format where height < 1080.
  3. Priority 3: Fallback to the very best available video.
- This will run automatically as soon as the results come in.

### 3. Fresh UI Component
#### [MODIFY] [PlaylistSelectionDialog.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/components/PlaylistSelectionDialog.kt)
- **Rewrite from scratch** to ensure the dropdown is always present.
- Each list item will feature:
  - Thumbnail (if available).
  - Title.
  - **Quality Selector**: A button that opens a grouped dropdown.
  - **Grouped Options**: `--- 🎬 Video ---` and `--- 🎵 Audio ---`.

---

## Verification Plan

### Manual Verification
1.  **Playlist Rebuild Test**: Paste a playlist. Verify that *every* item has its own dropdown immediately.
2.  **Smart Default Test**: Verify that YouTube videos default to 1080p automatically.
3.  **Mixed Mode Test**: Pick 720p for the first video and "Audio Only" for the second. Verify both download correctly.
4.  **Filename Test**: Verify that the "File name too long" fix still works with this new logic.
