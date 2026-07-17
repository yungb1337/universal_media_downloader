# Walkthrough - In-App Cookies & Storage Refinement

I have implemented two major power-user features: a built-in cookie extractor and a refined default download location.

## Features Added

### 🔑 In-App Cookie Extractor
- **Built-in Browser**: Added a "Login to YouTube / Extract Cookies" button in Settings.
- **Automatic Extraction**: When you log in and tap **DONE**, the app automatically:
    1. Grabs the login cookies from the browser.
    2. Formats them into the **Netscape `cookies.txt`** format required by `yt-dlp`.
    3. Saves them internally and **automatically updates your settings** to use this file.
- **Why this is better**: No more PC, no more browser extensions, and no more manual file transfers!

### 📂 Storage Refinement
- **Default Folder**: Changed the default download location to a dedicated **"Universal Downloader"** folder inside your phone's standard **Downloads** directory.
- **Root Directory Support**: Added a "Set Default Download Folder" button that launches the folder picker. This allows you to select the root directory (where DCIM, Android, etc. are) and grant the app permission to create your folder there.

## Changes Made
- **[LoginBrowserDialog.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/components/LoginBrowserDialog.kt)**: New browser component.
- **[DownloadViewModel.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/viewmodel/DownloadViewModel.kt)**: Implemented extraction and file saving logic.
- **[FileUtils.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/util/FileUtils.kt)**: Updated default directory logic and added Netscape formatting helpers.
- **[SettingsScreen.kt](file:///C:/Users/Asus/Downloads/projects/universal_downloader/android/app/src/main/java/com/universaldownloader/ui/screens/SettingsScreen.kt)**: Added the new action buttons and integrated the login dialog.

## Verification
- **Build**: Successfully compiled and deployed.
- **UI**: Verified that the new buttons appear in Settings and the Login Browser launches correctly.

---

> [!TIP]
> After your first login, the app will remember the cookies file. You only need to do this again if you log out or your session expires!
