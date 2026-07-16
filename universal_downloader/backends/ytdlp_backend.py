import os
import time
import traceback
import json
import sys
from pathlib import Path
from typing import Optional

import yt_dlp
from yt_dlp.networking.impersonate import ImpersonateTarget
from tqdm import tqdm

from core.models import DownloadResult
from utils.config import config
from utils.logger import logger
from utils.helpers import sanitize_filename, extract_video_id


class TqdmProgressHook:
    """Maps yt-dlp progress callbacks into tqdm progress bars or GUI updates."""

    def __init__(self):
        self.pbar: Optional[tqdm] = None
        self.current_file = ""
        self.is_gui = os.getenv("UNIVERSAL_DOWNLOADER_GUI") == "1"

    def __call__(self, d: dict):
        if d["status"] == "downloading":
            filename = d.get("filename", "")
            basename = os.path.basename(filename)
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes", 0)

            if self.is_gui:
                progress_info = {
                    "type": "progress",
                    "filename": basename,
                    "downloaded": downloaded,
                    "total": total,
                    "speed": d.get("speed"),
                    "eta": d.get("eta"),
                    "status": "downloading"
                }
                print(f"[PROGRESS] {json.dumps(progress_info)}", flush=True)
                return

            if self.pbar is None or self.current_file != filename:
                self.close()
                self.current_file = filename
                desc = f"↓ {basename[:40]}..." if len(basename) > 40 else f"↓ {basename}"
                self.pbar = tqdm(
                    total=total or None, unit="B", unit_scale=True,
                    desc=desc, leave=False, ncols=100
                )

            if self.pbar.total is None and total:
                self.pbar.total = total

            self.pbar.n = downloaded
            self.pbar.refresh()

        elif d["status"] == "finished":
            if self.is_gui:
                filename = d.get("filename", "")
                basename = os.path.basename(filename)
                progress_info = {
                    "type": "progress",
                    "filename": basename,
                    "status": "finished"
                }
                print(f"[PROGRESS] {json.dumps(progress_info)}", flush=True)
                return

            if self.pbar:
                self.pbar.n = self.pbar.total or d.get("downloaded_bytes", 0)
                self.pbar.refresh()
            self.close()

        elif d["status"] == "error":
            if self.is_gui:
                filename = d.get("filename", "")
                basename = os.path.basename(filename)
                progress_info = {
                    "type": "progress",
                    "filename": basename,
                    "status": "error"
                }
                print(f"[PROGRESS] {json.dumps(progress_info)}", flush=True)
                return
            self.close()

    def close(self):
        if self.pbar:
            self.pbar.close()
            self.pbar = None


class YtDlpBackend:
    """
    Universal download backend powered by yt-dlp.
    
    Handles format selection, cookies, retries, and progress reporting.
    Works across all 1700+ sites yt-dlp supports without any hardcoding.
    """

    def _build_format_string(self) -> str:
        """
        Builds a universal format selection string with graceful fallbacks.
        
        yt-dlp's format selectors are site-agnostic — the '/' separator
        defines a fallback chain that yt-dlp walks through automatically.
        """
        if config.audio_only:
            # Audio extraction is handled via postprocessor, not format string.
            # We request the best audio stream available.
            return "bestaudio/best"

        if config.highest_res:
            # No resolution cap — grab the absolute best
            return "bestvideo+bestaudio/best"

        # 1080p cap with 4-level fallback:
        #   1. Separate video (≤1080p) + best audio → merge into mp4
        #   2. Video-only ≤1080p (if no audio split available)
        #   3. Best combined format ≤1080p (pre-muxed)
        #   4. Whatever is available (for sites with weird formats)
        return (
            "bestvideo[height<=1080]+bestaudio"
            "/bestvideo[height<=1080]"
            "/best[height<=1080]"
            "/best"
        )

    def _build_ydl_opts(self, output_template: str) -> dict:
        """Builds the yt-dlp options dict with all config applied."""
        progress_hook = TqdmProgressHook()

        opts = {
            "outtmpl": output_template,
            "format": self._build_format_string(),
            "quiet": True,
            "no_warnings": True,
            "noprogress": True,
            "progress_hooks": [progress_hook],
            "ignoreerrors": False,
            # Embed metadata for better file organization
            "writethumbnail": False,
            # Windows compatibility
            "windowsfilenames": True,
            # Use curl_cffi to impersonate Chrome's TLS fingerprint.
            # This bypasses SSL errors and bot detection on strict sites.
            "impersonate": ImpersonateTarget(client="chrome"),
            # Retry options to handle intermittent connection drops
            "retries": 10,
            "fragment_retries": 10,
        }

        # Merge output format (only for video, not audio-only)
        if not config.audio_only:
            opts["merge_output_format"] = "mp4"

        # Audio-only: extract to MP3 320kbps
        if config.audio_only:
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "320",
            }]

        # Cookie injection for bot bypass
        if config.cookies_from_browser:
            opts["cookiesfrombrowser"] = (config.cookies_from_browser,)
        elif config.cookies_file:
            opts["cookiefile"] = config.cookies_file

        return opts

    def _check_already_downloaded(self, video_id: str | None) -> Path | None:
        """
        Checks if a file with this video ID already exists in the download dir.
        Returns the path if found, None otherwise.
        """
        if not video_id:
            return None

        for f in config.downloads_dir.iterdir():
            if f.is_file() and f"[{video_id}]" in f.name:
                return f
        return None

    def download(self, url: str, line_number: int = 0, custom_name: str | None = None) -> DownloadResult:
        """
        Downloads a single URL. Returns a DownloadResult with full metadata.
        
        Handles:
        - Duplicate detection (skips if already downloaded)
        - Retry with exponential backoff
        - WinError 32 file lock recovery
        - Graceful error capture for every failure type
        """
        start_time = time.time()

        # ── Step 1: Probe the URL for metadata (title, ID) ──
        probe_opts = {
            "extract_flat": False,
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            # Use curl_cffi for probing as well
            "impersonate": ImpersonateTarget(client="chrome"),
        }
        if config.cookies_from_browser:
            probe_opts["cookiesfrombrowser"] = (config.cookies_from_browser,)
        elif config.cookies_file:
            probe_opts["cookiefile"] = config.cookies_file

        title = None
        video_id = None
        probe_success = False
        last_probe_error = None

        for attempt in range(1, config.max_retries + 1):
            try:
                with yt_dlp.YoutubeDL(probe_opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                    if info:
                        title = info.get("title", "Unknown")
                        video_id = info.get("id")
                probe_success = True
                break
            except yt_dlp.utils.UnsupportedError:
                elapsed = time.time() - start_time
                logger.error(f"Unsupported URL (yt-dlp has no extractor for this site): {url}")
                return DownloadResult(
                    url=url, success=False, error="Unsupported URL",
                    duration_seconds=elapsed, line_number=line_number
                )
            except yt_dlp.utils.DownloadError as e:
                last_probe_error = str(e).split("\n")[0]  # First line only
                if attempt < config.max_retries:
                    wait = 2 ** attempt
                    logger.warning(f"Probe attempt {attempt} failed: {last_probe_error}. Retrying in {wait}s...")
                    time.sleep(wait)
            except Exception as e:
                last_probe_error = str(e)
                if attempt < config.max_retries:
                    wait = 2 ** attempt
                    logger.warning(f"Probe attempt {attempt} failed unexpectedly: {last_probe_error}. Retrying in {wait}s...")
                    time.sleep(wait)

        if not probe_success:
            elapsed = time.time() - start_time
            logger.error(f"Cannot access URL after {config.max_retries} probe attempts: {last_probe_error}")
            return DownloadResult(
                url=url, success=False, error=last_probe_error,
                duration_seconds=elapsed, line_number=line_number
            )

        # ── Step 2: Check for duplicates ──
        existing = self._check_already_downloaded(video_id)
        if existing:
            elapsed = time.time() - start_time
            logger.info(f"⏭️  Already downloaded: {existing.name}")
            return DownloadResult(
                url=url, success=True, file_path=existing,
                title=title, skipped=True,
                duration_seconds=elapsed, line_number=line_number,
                file_size_bytes=existing.stat().st_size
            )

        # ── Step 3: Build output template ──
        final_title = custom_name or title or "Unknown"
        safe_title = sanitize_filename(final_title)
        if video_id:
            template = str(config.downloads_dir / f"{safe_title} [{video_id}].%(ext)s")
        else:
            # For sites without a clean video ID, use title + extractor
            template = str(config.downloads_dir / f"{safe_title}.%(ext)s")

        # ── Step 4: Download with retries ──
        ydl_opts = self._build_ydl_opts(template)
        last_error = None

        for attempt in range(1, config.max_retries + 1):
            try:
                logger.info(
                    f"⬇️  Downloading: {title or url}"
                    + (f" (attempt {attempt}/{config.max_retries})" if attempt > 1 else "")
                )

                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    info = ydl.extract_info(url, download=True)

                    if not info:
                        raise RuntimeError("yt-dlp returned no info after download")

                    # Find the actual downloaded file path
                    final_path = self._resolve_downloaded_path(info, video_id, safe_title)

                    if final_path and final_path.exists():
                        elapsed = time.time() - start_time
                        fsize = final_path.stat().st_size
                        logger.info(f"✅ Saved: {final_path.name} ({self._fmt_size(fsize)})")
                        return DownloadResult(
                            url=url, success=True, file_path=final_path,
                            title=final_title, duration_seconds=elapsed,
                            file_size_bytes=fsize, line_number=line_number
                        )
                    else:
                        raise RuntimeError("Download completed but file not found on disk")

            except Exception as e:
                last_error = str(e)

                # WinError 32: file locked by FFmpeg during merge — retry
                if "WinError 32" in last_error and attempt < config.max_retries:
                    wait = 2 ** attempt
                    logger.warning(f"File locked (WinError 32), retrying in {wait}s...")
                    # Reset progress hooks for clean retry
                    ydl_opts["progress_hooks"] = [TqdmProgressHook()]
                    time.sleep(wait)
                    continue

                # Other retryable errors
                if attempt < config.max_retries:
                    wait = 2 ** attempt
                    logger.warning(f"Attempt {attempt} failed: {last_error}. Retrying in {wait}s...")
                    ydl_opts["progress_hooks"] = [TqdmProgressHook()]
                    time.sleep(wait)
                    continue

                # All retries exhausted
                break

        elapsed = time.time() - start_time
        logger.error(f"❌ Failed after {config.max_retries} attempts: {title or url}")
        logger.error(f"   Error: {last_error}")
        return DownloadResult(
            url=url, success=False, error=last_error,
            title=final_title, duration_seconds=elapsed,
            line_number=line_number
        )

    def _resolve_downloaded_path(
        self, info: dict, video_id: str | None, safe_title: str
    ) -> Path | None:
        """
        Resolves the actual file path after download.
        yt-dlp can rename files during post-processing (merge, audio extract),
        so we check multiple sources.
        """
        # Source 1: requested_downloads (most reliable after post-processing)
        requested = info.get("requested_downloads")
        if requested:
            filepath = requested[0].get("filepath")
            if filepath:
                p = Path(filepath)
                if p.exists():
                    return p

        # Source 2: direct filepath from info
        filepath = info.get("filepath")
        if filepath:
            p = Path(filepath)
            if p.exists():
                return p

        # Source 3: reconstruct from template
        ext = info.get("ext", "mp4")
        if config.audio_only:
            ext = "mp3"

        if video_id:
            candidate = config.downloads_dir / f"{safe_title} [{video_id}].{ext}"
            if candidate.exists():
                return candidate

        # Source 4: scan directory for matching ID
        if video_id:
            for f in config.downloads_dir.iterdir():
                if f.is_file() and f"[{video_id}]" in f.name:
                    return f

        # Source 5: scan for matching title
        for f in config.downloads_dir.iterdir():
            if f.is_file() and safe_title in f.name:
                return f

        return None

    @staticmethod
    def _fmt_size(size_bytes: int) -> str:
        """Quick file size formatter."""
        for unit in ("B", "KB", "MB", "GB"):
            if abs(size_bytes) < 1024.0:
                return f"{size_bytes:.1f} {unit}"
            size_bytes /= 1024.0
        return f"{size_bytes:.1f} TB"
