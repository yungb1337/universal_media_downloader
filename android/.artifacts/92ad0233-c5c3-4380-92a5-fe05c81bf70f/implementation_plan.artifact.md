# Implementation Plan - FFmpeg Integration & Persistence

This plan restores the 1080p+ download capability by resolving code conflicts and ensuring the FFmpeg binaries are correctly tracked in your repository.

## User Review Required

> [!IMPORTANT]
> **Git Tracking**: We will modify `.gitignore` to specifically allow the FFmpeg binaries. This will increase your repository size by ~30MB, but ensures you never lose this progress again.

> [!WARNING]
> **File Renaming**: Android requires native binaries to follow a specific naming convention. You must rename `ffmpeg` to `libffmpeg.so` and `ffprobe` to `libffprobe.so` before pasting them.

## Proposed Changes

### 1. Resolve Code Conflicts
I will fix the "broken" state caused by the unmerged cherry-pick.

#### [MODIFY] [PythonBridge.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/engine/PythonBridge.kt)
- Remove all `<<<<<<<`, `=======`, and `>>>>>>>` markers.
- Consolidate the `getFFmpegPath()` logic.
- Ensure only one instance of `val ffmpegPath = getFFmpegPath()` exists.

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Asus/Downloads/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/screens/HomeScreen.kt)
- Resolve UI button style conflicts, keeping the modern Material 3 design.

---

### 2. Update Git Configuration
#### [MODIFY] [.gitignore](file:///C:/Users/Asus/Downloads/universal_downloader/.gitignore)
- Add an exception rule to stop ignoring FFmpeg binaries:
  ```gitignore
  !**/jniLibs/**/*.so
  ```

---

### 3. Binary Placement Instructions

Please follow these exact steps to restore the binaries:

1.  **Rename your files**:
    - Rename `ffmpeg` → `libffmpeg.so`
    - Rename `ffprobe` → `libffprobe.so`
2.  **Create the directory**:
    - Go to `app/src/main/`
    - Create a new folder named `jniLibs` (if it doesn't exist).
    - Inside `jniLibs`, create a folder named `arm64-v8a`.
3.  **Paste the files**:
    - Copy both renamed `.so` files into `app/src/main/jniLibs/arm64-v8a/`.

---

## Verification Plan

### Automated Tests
- I will run `git status` to verify the files are being tracked after you paste them.
- I will check the build logs for any remaining Kotlin compiler errors.

### Manual Verification
1.  **Build and Run**: Deploy to your phone.
2.  **Check Logs**: The app should no longer show "FFmpeg missing" warnings when starting a 1080p download.
3.  **Commit**: After pasting, run `git add .` to see if the `.so` files are included in the staging area.
