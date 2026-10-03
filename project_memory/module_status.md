# Module Status

This file is maintained by the knowledge-curator agent after each run. It records what the system knows about each module in the project.

## Core Python Downloader Engine
- **Status**: active
- **Last modified run**: `run_20261003_150000`
- **Purpose**: High-performance link extractor & downloader powered by `yt-dlp` with YouTube Mix cleaning and JS runtime support.
- **Key files**: `main.py`, `backends/ytdlp_backend.py`, `core/engine.py`, `core/parser.py`, `utils/helpers.py`, `utils/config.py`
- **Dependencies**: `yt-dlp>=2026.8.19`, `curl_cffi`, `python-dotenv`, `tqdm`
- **Dependents**: Desktop CLI, Desktop GUI, Android Engine Bridge
- **Known issues**: none

## Universal Downloader for Android (Architecture & Blueprint)
- **Status**: planned / designed
- **Last modified run**: `run_20261003_150000`
- **Purpose**: Native Android application architecture featuring a dynamic in-app OTA `yt-dlp` wheel updater, embedded QuickJS challenge runner, and Material 3 Jetpack Compose UI.
- **Key files**: `ANDROID_ARCHITECTURE_AND_DESIGN.md`, `project_memory/adrs/*`, `project_memory/contracts/*`
- **Dependencies**: Chaquopy 15.x, Jetpack Compose, Material 3, QuickJS JNI
- **Dependents**: Android mobile platform
- **Known issues**: Legacy `android/` directory is obsolete (preserved untouched).
