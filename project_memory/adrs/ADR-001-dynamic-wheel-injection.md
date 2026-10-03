# ADR-001: Dynamic In-App Wheel Injection for yt-dlp Updates

## Status
Accepted

## Context
YouTube and other streaming platforms frequently modify streaming signatures, causing download failures (`403 Forbidden`). Standard Android app update cycles through APK compilation and distribution are too slow to keep up with these breaking changes.

## Decision
We decouple the Python extraction backend from the APK compilation cycle. The app will:
1. Bundle a baseline `yt-dlp` wheel inside the APK.
2. Periodically check PyPI / GitHub API for newer `yt_dlp-*.whl` releases.
3. Download the pure-Python wheel into the app's private files directory.
4. Dynamically prepend the downloaded wheel path to Python's `sys.path` on startup before importing `yt_dlp`.

## Consequences
- **Positive**: App can update its extraction core in seconds over-the-air without user APK re-installs.
- **Positive**: Zero risk of breaking Android native code or UI logic when YouTube changes protocols.
- **Negative**: Requires handling fallback to bundled assets if downloaded wheel is corrupted.
