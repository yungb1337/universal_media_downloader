"""
android_downloader.py — Python-to-Kotlin bridge for Android App via Chaquopy.

Provides entry points for Kotlin to execute single downloads, query metadata,
and cancel active downloads mid-progress.
"""

import os
import sys
from pathlib import Path

# Ensure dependencies resolve
ROOT_DIR = Path(__file__).parent
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from utils.config import config
from utils.logger import logger
from backends.ytdlp_backend import YtDlpBackend
from core.models import DownloadResult

# Global cancel flag to abort yt-dlp mid-download
_cancel_flag = False


def cancel_download():
    """Signalled by Kotlin UI to stop downloading immediately."""
    global _cancel_flag
    _cancel_flag = True


class AndroidProgressHook:
    """Redirects progress callbacks from yt-dlp to a Kotlin listener."""

    def __init__(self, callback):
        self.callback = callback
        self.last_percent = -1

    def __call__(self, d: dict):
        global _cancel_flag
        if _cancel_flag:
            # Raising an error is the standard way to abort yt-dlp mid-download
            raise RuntimeError("ABORTED")

        if d["status"] == "downloading" and self.callback:
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes", 0)
            
            if total > 0:
                percent = int((downloaded / total) * 100)
                if percent != self.last_percent:
                    self.last_percent = percent
                    # Invoke Kotlin callback: (percent: Int, downloadedBytes: Long, totalBytes: Long)
                    self.callback(percent, downloaded, total)


def download_single(
    url: str,
    config_map: dict,
    progress_callback,
    log_callback
) -> dict:
    """
    Downloads a single URL with configurations passed from Kotlin.
    Returns a dict representation of DownloadResult.
    """
    global _cancel_flag
    _cancel_flag = False

    # 1. Apply config from Kotlin
    config.downloads_dir = Path(config_map.get("downloads_dir", "/sdcard/Download"))
    config.highest_res = bool(config_map.get("highest_res", False))
    config.audio_only = bool(config_map.get("audio_only", False))
    config.max_retries = int(config_map.get("max_retries", 3))
    config.cookies_file = config_map.get("cookies_file", None)
    config.ffmpeg_location = config_map.get("ffmpeg_location", None)

    # 2. Redirect python logger output to Kotlin's log callback
    class KotlinLogHandler:
        def write(self, msg):
            if msg.strip() and log_callback:
                log_callback(msg.strip())
        def flush(self):
            pass

    sys.stdout = KotlinLogHandler()
    sys.stderr = KotlinLogHandler()

    # 3. Setup backend and custom hook
    backend = YtDlpBackend()
    
    # We patch backend's _build_ydl_opts at runtime to use our AndroidProgressHook
    original_build_opts = backend._build_ydl_opts
    def android_build_opts(template):
        opts = original_build_opts(template)
        opts["progress_hooks"] = [AndroidProgressHook(progress_callback)]
        return opts
    
    backend._build_ydl_opts = android_build_opts

    # 4. Start download
    try:
        res = backend.download(url)
        return {
            "success": res.success,
            "title": res.title or "Unknown Title",
            "file_path": str(res.file_path) if res.file_path else None,
            "error": res.error,
            "skipped": res.skipped,
            "file_size": res.file_size_bytes
        }
    except Exception as e:
        error_msg = str(e)
        if "ABORTED" in error_msg or _cancel_flag:
            error_msg = "PAUSED"
        return {
            "success": False,
            "title": None,
            "file_path": None,
            "error": error_msg,
            "skipped": False,
            "file_size": 0
        }
