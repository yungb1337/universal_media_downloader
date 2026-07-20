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

def sanitize_filename(filename, max_length=100):
    """Remove characters invalid for Android file systems and truncate strictly."""
    if not filename:
        return "Unknown"

    # If title is a URL, use a generic name
    if filename.startswith("http") or "://" in filename:
        return "Video"

    # Basic illegal character removal
    sanitized = re.sub(r'[\\/:*?"<>|]', "", filename)
    sanitized = re.sub(r"\s+", " ", sanitized)
    sanitized = re.sub(r"\.{2,}", ".", sanitized)
    sanitized = sanitized.strip()

    # Truncate strictly to 100 characters for Android safety
    if len(sanitized) > max_length:
        sanitized = sanitized[:max_length].strip()

    return sanitized


def clean_error_message(error_str):
    """Strip HTML tags and truncate massive server responses for cleaner logs."""
    if not error_str:
        return "Unknown error"

    # Aggressive cleaning of server trash
    err_lower = error_str.lower()
    if "<html" in err_lower or "<body" in err_lower:
        return "Server Error: Access Denied or Bot Challenge detected. Please re-extract cookies in Settings."

    if "file name too long" in err_lower or "path too long" in err_lower:
        return "File Name Error: The video title is too complex. I'm trying a simpler name."

    if '{"' in error_str and '"}' in error_str:
        return "Server Error: The website returned raw technical data. Try a different resolution."

    # Strip URL-looking strings to prevent log clutter
    error_str = re.sub(r'https?://\S+', '[URL]', error_str)

    # If it's a massive block of text, only keep the first and last bit
    if len(error_str) > 250:
        return error_str[:120] + " ... " + error_str[-120:]

    return error_str.strip()


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

class PauseException(Exception): pass
class StopException(Exception): pass

def build_ydl_opts(url, output_template, format_string, cookies_file=None,
                   ffmpeg_path=None, audio_only=False, audio_format="m4a",
                   audio_quality="320", progress_listener=None):
    """
    Build yt-dlp options dict.
    """

    def progress_hook(d):
        if PythonBridge.isCancelledGlobal():
            raise RuntimeError("Download cancelled by user")

        # Per-Job Check
        job_state = PythonBridge.getJobState(url)
        if job_state == "PAUSED":
            raise PauseException("Download paused by user")
        if job_state == "STOPPED":
            raise StopException("Download stopped and removed by user")

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

    # Audio-only: extract to chosen format (only if FFmpeg is available)
    if audio_only and ffmpeg_path:
        if audio_format == "best":
            # Original high-quality stream, no conversion (fastest, safest)
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "best",
            }]
        else:
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": audio_format,
                "preferredquality": audio_quality,
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
    CLEAN REBUILD: Fetch full metadata for videos/playlists with grouped formats.
    """
    opts = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": False,
        "playlist_items": "1-20", # Safety limit
    }
    if cookies_file:
        opts["cookiefile"] = cookies_file

    def process_entry(info):
        """Extract and clean formats for a single video entry."""
        formats = []
        for f in info.get("formats", []):
            vcodec = f.get("vcodec")
            acodec = f.get("acodec")

            is_video = vcodec != "none"
            is_audio = vcodec == "none" and acodec != "none"

            if is_video or is_audio:
                # Clean label: 1080p, 720p, etc.
                if is_video:
                    h = f.get("height")
                    label = "{}p".format(h) if h else (f.get("resolution") or "Video")
                else:
                    label = "Audio"

                formats.append({
                    "id": f.get("format_id"),
                    "res": label,
                    "ext": f.get("ext"),
                    "type": "video" if is_video else "audio",
                    "size": f.get("filesize") or f.get("filesize_approx") or 0,
                    "height": f.get("height") or 0
                })

        # Sort: Video (Highest First), then Audio
        formats.sort(key=lambda x: (0 if x["type"] == "video" else 1, -x["height"], -x["size"]))

        return {
            "url": info.get("webpage_url") or info.get("url"),
            "title": info.get("title") or "Unknown",
            "thumb": info.get("thumbnail"),
            "formats": formats[:25]
        }

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            data = ydl.extract_info(url, download=False)
            if not data:
                return json.dumps({"success": False, "error": "No data found"})

            results = []
            if "entries" in data:
                for entry in data["entries"]:
                    if entry: results.append(process_entry(entry))
                is_list = True
            else:
                results.append(process_entry(data))
                is_list = False

            return json.dumps({
                "success": True,
                "is_playlist": is_list,
                "entries": results
            })
    except Exception as e:
        return json.dumps({"success": False, "error": clean_error_message(str(e))})


# ── Main download function (called from Kotlin via Chaquopy) ─────────────────

def download_url(url, output_dir, highest_res, audio_only,
                 cookies_file, ffmpeg_path, max_retries,
                 custom_name, format_id, audio_format, audio_quality,
                 progress_listener):
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
    format_id = format_id if format_id and format_id != "auto" else None
    audio_format = audio_format if audio_format else "m4a"
    audio_quality = audio_quality if audio_quality else "320"

    # DECOUPLING LOGIC: If a specific format_id is provided, it dictates the job type.
    if format_id:
        if format_id == "bestaudio":
            audio_only = True
        else:
            audio_only = False

    # Ensure output directory exists
    try:
        os.makedirs(output_dir, exist_ok=True)
    except OSError as e:
        return json.dumps({
            "url": url,
            "success": False,
            "error": "Folder Error: Cannot create download directory. Path may be too long or restricted.",
            "duration_seconds": 0.1,
        })

    # ── Step 1: Probe for metadata ──
    probe_result = json.loads(probe_url(url, cookies_file, max_retries))

    if not probe_result["success"]:
        elapsed = time.time() - start_time
        return json.dumps({
            "url": url,
            "success": False,
            "error": clean_error_message(probe_result.get("error", "Probe failed")),
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

    # ── Step 3: Build output template ──
    # Quality prefix logic
    quality_label = ""
    if format_id:
        # Sanitize format_id: remove URL parts and illegal chars, limit to 10 chars
        if "://" in str(format_id):
            quality_label = "Fmt"
        else:
            quality_label = re.sub(r'[^a-zA-Z0-9]', '', str(format_id))[:10]
    elif audio_only:
        quality_label = audio_format.upper()[:5]
    elif highest_res:
        quality_label = "BEST"

    # Define final_title for return JSON (must exist for Step 4 error handling and final return)
    final_title = custom_name or title or "Video"

    # Sanitize title and ensure it's not a URL for the file system
    clean_title = sanitize_filename(final_title, max_length=80)

    # Construct final safe filename base
    if video_id:
        # e.g. "1080p VideoTitle [id]"
        filename_base = "{} {} [{}]".format(quality_label, clean_title, video_id).strip()
    else:
        filename_base = "{} {}".format(quality_label, clean_title).strip()

    # Final safety check on the whole name
    filename_base = sanitize_filename(filename_base, max_length=120)

    template = str(Path(output_dir) / "{}.%(ext)s".format(filename_base))

    # ── Step 4: Download with retries ──
    if audio_only:
        format_string = "bestaudio/best"
    elif format_id:
        if format_id == "bestaudio":
            format_string = "bestaudio/best"
        else:
            # If user picked a specific format, use it (and add best audio if it's video only)
            format_string = "{}+bestaudio/{}".format(format_id, format_id)
    else:
        format_string = build_format_string(highest_res, audio_only)

    last_error = None

    for attempt in range(1, max_retries + 1):
        try:
            # Rebuild opts each attempt for clean progress hooks
            ydl_opts = build_ydl_opts(
                url, template, format_string,
                cookies_file=cookies_file,
                ffmpeg_path=ffmpeg_path,
                audio_only=audio_only,
                audio_format=audio_format,
                audio_quality=audio_quality,
                progress_listener=progress_listener,
            )

            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)

                if not info:
                    raise RuntimeError("yt-dlp returned no info after download")

                # Find the actual downloaded file
                final_path = _resolve_downloaded_path(
                    info, video_id, filename_base, output_dir, audio_only, audio_format
                )

                if final_path and Path(final_path).exists():
                    elapsed = time.time() - start_time
                    fsize = Path(final_path).stat().st_size
                    # Cleanup job state after success
                    PythonBridge.removeJobState(url)
                    return json.dumps({
                        "url": url,
                        "success": True,
                        "skipped": False,
                        "title": final_title,
                        "file_path": final_path,
                        "file_size_bytes": fsize,
                        "duration_seconds": elapsed,
                        "thumbnail": info.get("thumbnail"),
                        "quality": info.get("format_note") or info.get("resolution") or "auto"
                    })
                else:
                    raise RuntimeError("Download completed but file not found")

        except PauseException:
            # Clean exit, keep part files
            return json.dumps({
                "url": url,
                "success": False,
                "error": "Paused",
                "paused": True,
                "duration_seconds": time.time() - start_time,
            })

        except StopException:
            # Cleanup part files and exit
            try:
                # Basic cleanup: look for files containing video_id and ending in .part
                if video_id:
                    for f in Path(output_dir).glob("*{}*.part*".format(video_id)):
                        f.unlink(missing_ok=True)
            except: pass
            PythonBridge.removeJobState(url)
            return json.dumps({
                "url": url,
                "success": False,
                "error": "Stopped",
                "stopped": True,
                "duration_seconds": time.time() - start_time,
            })

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
    # Cleanup job state on failure
    PythonBridge.removeJobState(url)
    return json.dumps({
        "url": url,
        "success": False,
        "error": clean_error_message(last_error),
        "title": final_title,
        "duration_seconds": elapsed,
    })


def _resolve_downloaded_path(info, video_id, safe_title, output_dir, audio_only, audio_format="m4a"):
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
        # If 'best', extension will depend on source (m4a, mp3, opus)
        # For simplicity, we fallback to common extensions if 'best' is chosen
        if audio_format == "best":
            ext = info.get("ext", "m4a")
        else:
            ext = audio_format

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

