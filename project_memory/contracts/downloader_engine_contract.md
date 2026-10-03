# Contract: Kotlin ↔ Python Downloader Engine Bridge

## Interface Definition

```kotlin
interface AndroidDownloaderBridge {
    /**
     * Initializes Python runtime, sets up sys.path with dynamic updates, and checks version.
     * @param updatesDir Path to context.filesDir/python_updates
     * @param quickJsPath Path to native quickjs binary
     * @return VersionInfo containing yt-dlp version and engine health
     */
    fun initializeEngine(updatesDir: String, quickJsPath: String): EngineStatus

    /**
     * Probes metadata for a URL (extracts title, formats, duration, playlist items).
     */
    fun probeUrl(url: String, cookiesPath: String?): ProbeResponse

    /**
     * Starts a download stream with callback for real-time progress.
     */
    fun startDownload(
        request: DownloadRequest,
        progressCallback: (ProgressEvent) -> Unit
    ): DownloadResult

    /**
     * Checks PyPI for the newest yt-dlp wheel release and downloads if newer.
     */
    fun checkAndUpdateEngine(updatesDir: String): UpdateResult
}
```
