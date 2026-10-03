# Active Objective

## Objective
Design a fully functional, future-proof Android application for Universal Downloader ("Universal Link Downloader for Android") with a built-in automated self-updating mechanism for `yt-dlp` and extractor components. The application must survive constant YouTube/upstream changes without requiring a full APK update for routine extractor/backend fixes.

## Scope
- Complete system architecture & ADRs for Universal Downloader on Android (API 26+ / Android 8.0 - Android 15).
- Future-proofing engine: Dynamic in-app updater for `yt-dlp` wheels, extractor plugins, and remote EJS solvers (Node/QuickJS) without requiring full app re-installs.
- Modern Android UI design & implementation architecture using Jetpack Compose & Material 3.
- Background downloading service with Foreground Service & MediaStore integration (Scoped Storage compatible).
- Multi-threaded download manager, link parser (supporting batch links, mixes, playlists, custom titles, `[DONE]` state).
- Complete agentic workflow documentation, contracts, schemas, and implementation blueprint.
- Explicit constraint: Do NOT touch or overwrite the existing legacy/obsolete `android/` directory.

## Constraints
- Must be future-proof against YouTube/site extractor breaking changes (dynamic backend updating).
- Must include embedded JS challenge runtime compatible with Android (e.g. QuickJS Android).
- Must adhere to Android Scoped Storage and modern background execution limits (WorkManager + ForegroundService).
- Produce complete architectural designs, contracts, diagrams, schemas, and documentation.
