"""
download_bridge.py — Android bridge for yt-dlp downloads.

Adapted from the desktop backends/ytdlp_backend.py.
Called from Kotlin via Chaquopy.

Key differences from the desktop version:
  - No curl_cffi (ImpersonateTarget not available on Android)
  - No tqdm progress bars (progress reported via Kotlin callback)
  - Results returned as JSON strings for Kotlin interop
  - No Windows-specific error handling (WinError 32)
  - Accepts a Java ProgressListener object for real-time progress
"""

import os
import time
import json
import re
from pathlib import Path

import yt_dlp
from com.universaldownloader.engine import PythonBridge


# ── Utility functions (ported from utils/helpers.py) ─────────────────────────

def sanitize_filename(filename):
    """Remove characters invalid for Android file systems."""
    if not filename:
        return "Unknown"
    sanitized = re.sub(r'[\\/:*?"<>|]', "", filename)
    sanitized = re.sub(r"\s+", " ", sanitized)
    sanitized = re.sub(r"\.{2,}", ".", sanitized)
    return sanitized.strip()


def extract_video_id(filename):
    """Extract 11-char YouTube ID from filename if present."""
    match = re.search(r"\[([a-zA-Z0-9_-]{11})\]", filename)
    return match.group(1) if match else None


# ── Format selection (ported from YtDlpBackend._build_format_string) ─────────

def build_format_string(highest_res, audio_only):
    """
    Build yt-dlp format selection string.
    Prioritizes high-bitrate video and audio streams.
    Falls back to pre-merged 'best' to avoid FFmpeg errors if possible.
    """
    if audio_only:
        return "bestaudio/best"

    if highest_res:
        # Prefer merging, but fallback to single-file 'best' if FFmpeg is missing
        return "bestvideo+bestaudio/best"

    # 1080p cap — prefer best 1080p video + best audio, then pre-merged 1080p, then just 'best'
    return (
        "bestvideo[height<=1080]+bestaudio"
        "/bestvideo[height<=1080]"
        "/best[height<=1080]"
        "/best"
    )


# ── yt-dlp options builder (ported from YtDlpBackend._build_ydl_opts) ────────

def build_ydl_opts(output_template, format_string, cookies_file=None,
                   ffmpeg_path=None, audio_only=False, progress_listener=None):
    """
    Build yt-dlp options dict.

    The progress_listener is a Java object (passed from Kotlin via Chaquopy)
    with an onProgress(String) method that receives JSON progress updates.
    """

    def progress_hook(d):
        if PythonBridge.isCancelled():
            raise RuntimeError("Download cancelled by user")

        if progress_listener is None:
            return
        try:
            status = d.get("status", "")
            filename = os.path.basename(d.get("filename", ""))

            if status == "downloading":
                info = json.dumps({
                    "type": "progress",
                    "filename": filename,
                    "downloaded": d.get("downloaded_bytes", 0),
                    "total": d.get("total_bytes") or d.get("total_bytes_estimate") or 0,
                    "speed": d.get("speed"),
                    "eta": d.get("eta"),
                    "status": "downloading"
                })
                progress_listener.onProgress(info)

            elif status == "finished":
                info = json.dumps({
                    "type": "progress",
                    "filename": filename,
                    "status": "finished"
                })
                progress_listener.onProgress(info)

            elif status == "error":
                info = json.dumps({
                    "type": "progress",
                    "filename": filename,
                    "status": "error"
                })
                progress_listener.onProgress(info)
        except Exception:
            pass  # Don't let callback errors break the download

    opts = {
        "outtmpl": output_template,
        "format": format_string,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "progress_hooks": [progress_hook],
        "ignoreerrors": False,
        "writethumbnail": False,
        # yt-dlp internal retries for fragments / connections
        "retries": 10,
        "fragment_retries": 10,
    }

    # Only set merge format if FFmpeg is available
    if not audio_only and ffmpeg_path:
        opts["merge_output_format"] = "mp4"

    if ffmpeg_path:
        # On Android, the binary is renamed to libffmpeg.so
        opts["ffmpeg_location"] = ffmpeg_path

    # Audio-only: extract to MP3 320kbps (only if FFmpeg is available)
    if audio_only and ffmpeg_path:
        opts["postprocessors"] = [{
            "key": "FFmpegExtractAudio",
            "preferredcodec": "mp3",
            "preferredquality": "320",
        }]

    # Cookie file for bot bypass
    if cookies_file:
        opts["cookiefile"] = cookies_file
        # Extra check for valid cookie file format to provide better errors
        try:
            with open(cookies_file, 'r') as f:
                first_line = f.readline()
                if not first_line.startswith("# Netscape") and not first_line.startswith("# HTTP"):
                    progress_listener.onProgress(json.dumps({
                        "type": "log",
                        "message": "⚠️ Cookie file has invalid header: " + first_line.strip(),
                        "tag": "warning"
                    }))
        except Exception:
            pass

    return opts


# ── Probe function (ported from YtDlpBackend.download step 1) ────────────────

def probe_url(url, cookies_file=None, max_retries=3):
    """
    Probe a URL for metadata (title, ID) without downloading.
    Returns a JSON string with the probe result.
    """
    probe_opts = {
        "extract_flat": False,
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
    }
    if cookies_file:
        probe_opts["cookiefile"] = cookies_file

    last_error = None

    for attempt in range(1, max_retries + 1):
        try:
            with yt_dlp.YoutubeDL(probe_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if info:
                    return json.dumps({
                        "success": True,
                        "title": info.get("title", "Unknown"),
                        "video_id": info.get("id"),
                    })
        except yt_dlp.utils.UnsupportedError:
            return json.dumps({
                "success": False,
                "error": "Unsupported URL (no yt-dlp extractor for this site)",
                "fatal": True,
            })
        except yt_dlp.utils.DownloadError as e:
            last_error = str(e).split("\n")[0]
            if attempt < max_retries:
                time.sleep(2 ** attempt)
        except Exception as e:
            last_error = str(e)
            if attempt < max_retries:
                time.sleep(2 ** attempt)

    return json.dumps({
        "success": False,
        "error": last_error or "Unknown error during probe",
        "fatal": False,
    })


def get_playlist_info(url, cookies_file=None):
    """
    Fetch metadata for all videos in a playlist (or a single video).
    Uses extract_flat=True to be very fast.
    """
    opts = {
        "extract_flat": True,
        "quiet": True,
        "no_warnings": True,
    }
    if cookies_file:
        opts["cookiefile"] = cookies_file

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=False)

            if not info:
                return json.dumps({"success": False, "error": "No info found"})

            # Check if it's a single video or a playlist
            entries = []
            if "entries" in info:
                # It's a playlist
                for entry in info["entries"]:
                    if entry:
                        entries.append({
                            "url": entry.get("url") or entry.get("webpage_url"),
                            "title": entry.get("title") or "Unknown Title"
                        })
            else:
                # It's a single video
                entries.append({
                    "url": info.get("webpage_url") or url,
                    "title": info.get("title") or "Unknown Title"
                })

            return json.dumps({
                "success": True,
                "entries": entries,
                "playlist_title": info.get("title", "Selection")
            })

    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})


# ── Main download function (called from Kotlin via Chaquopy) ─────────────────

def download_url(url, output_dir, highest_res, audio_only,
                 cookies_file, ffmpeg_path, max_retries,
                 custom_name, progress_listener):
    """
    Download a single URL. This is the main entry point called from Kotlin.

    All parameters are passed from the Kotlin PythonBridge.
    progress_listener is a Java object with an onProgress(String) method.

    Returns a JSON string with the download result.
    """
    start_time = time.time()

    # Normalize empty strings to None
    cookies_file = cookies_file if cookies_file else None
    ffmpeg_path = ffmpeg_path if ffmpeg_path else None
    custom_name = custom_name if custom_name else None

    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)

    # ── Step 1: Probe for metadata ──
    probe_result = json.loads(probe_url(url, cookies_file, max_retries))

    if not probe_result["success"]:
        elapsed = time.time() - start_time
        return json.dumps({
            "url": url,
            "success": False,
            "error": probe_result.get("error", "Probe failed"),
            "duration_seconds": elapsed,
        })

    title = probe_result.get("title", "Unknown")
    video_id = probe_result.get("video_id")

    # ── Step 2: Check for duplicates (same logic as desktop) ──
    if video_id:
        downloads_path = Path(output_dir)
        if downloads_path.exists():
            for f in downloads_path.iterdir():
                if f.is_file() and "[{}]".format(video_id) in f.name:
                    elapsed = time.time() - start_time
                    return json.dumps({
                        "url": url,
                        "success": True,
                        "skipped": True,
                        "title": title,
                        "file_path": str(f),
                        "file_size_bytes": f.stat().st_size,
                        "duration_seconds": elapsed,
                    })

    # ── Step 3: Build output template (same naming as desktop) ──
    final_title = custom_name or title or "Unknown"
    safe_title = sanitize_filename(final_title)

    if video_id:
        template = str(Path(output_dir) / "{} [{}].%(ext)s".format(safe_title, video_id))
    else:
        template = str(Path(output_dir) / "{}.%(ext)s".format(safe_title))

    # ── Step 4: Download with retries (same exponential backoff) ──
    format_string = build_format_string(highest_res, audio_only)
    last_error = None

    for attempt in range(1, max_retries + 1):
        try:
            # Rebuild opts each attempt for clean progress hooks
            ydl_opts = build_ydl_opts(
                template, format_string,
                cookies_file=cookies_file,
                ffmpeg_path=ffmpeg_path,
                audio_only=audio_only,
                progress_listener=progress_listener,
            )

            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)

                if not info:
                    raise RuntimeError("yt-dlp returned no info after download")

                # Find the actual downloaded file
                final_path = _resolve_downloaded_path(
                    info, video_id, safe_title, output_dir, audio_only
                )

                if final_path and Path(final_path).exists():
                    elapsed = time.time() - start_time
                    fsize = Path(final_path).stat().st_size
                    return json.dumps({
                        "url": url,
                        "success": True,
                        "skipped": False,
                        "title": final_title,
                        "file_path": final_path,
                        "file_size_bytes": fsize,
                        "duration_seconds": elapsed,
                    })
                else:
                    raise RuntimeError("Download completed but file not found")

        except Exception as e:
            last_error = str(e)

            # Special handling for FFmpeg missing: retry with a single-file format
            if ("ffmpeg" in last_error.lower() or "ffprobe" in last_error.lower()) and attempt == 1:
                progress_listener.onProgress(json.dumps({
                    "type": "log",
                    "message": "⚠️ FFmpeg missing. Falling back to single-file format (720p)...",
                    "tag": "warning"
                }))
                format_string = "best"
                continue

            if attempt < max_retries:
                time.sleep(2 ** attempt)
                continue
            break

    elapsed = time.time() - start_time
    return json.dumps({
        "url": url,
        "success": False,
        "error": last_error,
        "title": final_title,
        "duration_seconds": elapsed,
    })


def _resolve_downloaded_path(info, video_id, safe_title, output_dir, audio_only):
    """
    Resolve the actual file path after download.
    Same 5-source fallback chain as the desktop YtDlpBackend._resolve_downloaded_path.
    """
    # Source 1: requested_downloads (most reliable after post-processing)
    requested = info.get("requested_downloads")
    if requested:
        filepath = requested[0].get("filepath")
        if filepath and Path(filepath).exists():
            return filepath

    # Source 2: direct filepath from info
    filepath = info.get("filepath")
    if filepath and Path(filepath).exists():
        return filepath

    # Source 3: reconstruct from template
    ext = info.get("ext", "mp4")
    if audio_only:
        ext = "mp3"

    downloads_dir = Path(output_dir)

    if video_id:
        candidate = downloads_dir / "{} [{}].{}".format(safe_title, video_id, ext)
        if candidate.exists():
            return str(candidate)

    # Source 4: scan directory for matching video ID
    if video_id:
        for f in downloads_dir.iterdir():
            if f.is_file() and "[{}]".format(video_id) in f.name:
                return str(f)

    # Source 5: scan for matching title
    for f in downloads_dir.iterdir():
        if f.is_file() and safe_title in f.name:
            return str(f)

    return None
