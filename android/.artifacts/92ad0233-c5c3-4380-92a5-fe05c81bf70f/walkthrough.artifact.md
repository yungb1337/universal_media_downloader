# Walkthrough - Public Storage & Concurrency Optimization

I have upgraded the storage logic to ensure your downloads are saved to the public `Downloads` folder by default and that concurrent downloads are handled without any file loss.

## Major Enhancements

### 📂 Default Public Storage
- **Feature**: If you don't select a folder in Settings, the app now automatically saves everything to `Downloads/Universal Downloader`.
- **Benefits**:
  - Files are easy to find using any File Manager or Gallery app.
  - **Persistence**: Your videos/audio **will not be deleted** if you uninstall the app.
- **Technology**: Uses the **Android MediaStore API** to safely "publish" files to the system.

### 🛡️ Concurrency & "No-Loss" Logic
- **The Fix**: I implemented a **Processed Files Tracker**. Even if you download 5 videos at once, the app keeps track of which ones have been moved from the temporary cache to the public folder.
- **Benefits**: No more "two copies" issue. The temporary copy is deleted immediately after the system confirms the file is safely saved in your public folder.

### ⚙️ UI Clarity
- **Settings Update**: The "Current Folder Path" in Settings now explicitly shows **"Downloads/Universal Downloader (System Default)"** when no custom folder is chosen.

### ⚙️ Settings & UI Improvements
- **Audio Encoder Fix**: Resolved the "Encoder not Found" error by adding flexible audio format options.
- **Audio Options**: You can now choose between **M4A (AAC)**, **MP3**, or **Original (Best)** in Settings.
- **M4A Default**: Switched the default audio format to M4A for 100% compatibility with Android devices.
- **Worker Limit**: Increased the maximum number of parallel workers from **8 to 16** and retry attempts from **10 to 20**.
- **Editing Fix**: Fixed a bug where numeric settings fields (like workers/retries) would jump back or feel "stuck" while typing. They now allow smooth editing.

---

## 🛠️ Technical Fixes
- **Memory Safety**: Temporary files are now strictly managed in an internal workspace within `cacheDir`.
- **Duplicate Handling**: Since we use the MediaStore API, if you download the same file twice, Android will automatically handle it by adding a number like `(1)` to the filename, preventing "file already exists" errors.

---

## Verification Results
- **Build Status**: ✅ Successfully compiled with MediaStore and WorkManager logic.
- **Concurrency Test**: ✅ Logic verified to process multiple result emissions without duplicate moves or skips.
- **Storage Cleanup**: ✅ Internal cache files are now deleted immediately after being published to MediaStore.
