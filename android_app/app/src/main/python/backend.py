import os
import sys
import json
import traceback
from typing import Callable, Optional, Dict, Any

from helpers import clean_youtube_url, sanitize_filename
import engine_loader

class CancelledException(Exception):
    pass

class AndroidDownloader:
    def __init__(self, updates_dir: Optional[str] = None):
        engine_loader.init_engine(updates_dir)
        import yt_dlp
        self.yt_dlp = yt_dlp

    def get_version(self) -> str:
        return engine_loader.get_engine_version()

    def probe_url(self, url: str, cookie_file: Optional[str] = None) -> str:
        """
        Probes a URL and returns JSON metadata including available resolutions and metadata.
        """
        clean_url = clean_youtube_url(url.strip())
        opts = {
            "quiet": True,
            "no_warnings": True,
            "extract_flat": "in_playlist",
            "noplaylist": True,
            "remote_components": ["ejs:github"],
        }
        if cookie_file and os.path.isfile(cookie_file):
            opts["cookiefile"] = cookie_file

        try:
            with self.yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(clean_url, download=False)
                if not info:
                    return json.dumps({"status": "error", "message": "Failed to extract media information"})

                # Handle playlist vs single
                is_playlist = "entries" in info and bool(info["entries"])

                title = info.get("title", "Unknown Title")
                uploader = info.get("uploader") or info.get("channel") or "Unknown Uploader"
                thumbnail = info.get("thumbnail") or ""
                duration = info.get("duration") or 0

                # Collect available heights
                formats = info.get("formats", [])
                heights = sorted(list(set(
                    f.get("height") for f in formats
                    if f.get("height") and f.get("vcodec") != "none"
                )), reverse=True)

                return json.dumps({
                    "status": "success",
                    "url": clean_url,
                    "title": title,
                    "uploader": uploader,
                    "thumbnail": thumbnail,
                    "duration": duration,
                    "is_playlist": is_playlist,
                    "available_resolutions": [f"{h}p" for h in heights] if heights else ["Best"]
                })
        except Exception as e:
            return json.dumps({
                "status": "error",
                "message": str(e),
                "traceback": traceback.format_exc()
            })

    def download(
        self,
        url: str,
        output_dir: str,
        format_type: str = "video", # "video" or "audio"
        resolution_cap: str = "1080",
        cookie_file: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None,
        is_cancelled_callback: Optional[Callable[[], bool]] = None
    ) -> str:
        """
        Downloads the media and streams status to progress_callback.
        Returns JSON string with outcome.
        """
        clean_url = clean_youtube_url(url.strip())
        os.makedirs(output_dir, exist_ok=True)

        # Build format string
        if format_type.lower() == "audio":
            format_str = "bestaudio/best"
            ext_target = "mp3"
        else:
            res = resolution_cap.replace("p", "").strip()
            if res.isdigit():
                format_str = f"bestvideo[height<={res}]+bestaudio/best[height<={res}]/best"
            else:
                format_str = "bestvideo+bestaudio/best"
            ext_target = "mp4"

        output_template = os.path.join(output_dir, "%(title)s.%(ext)s")
        downloaded_file = [None]

        def ydl_progress_hook(d):
            if is_cancelled_callback and is_cancelled_callback():
                raise CancelledException("Download cancelled by user")

            status = d.get("status")
            if status == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                downloaded = d.get("downloaded_bytes") or 0
                speed = d.get("speed") or 0
                eta = d.get("eta") or 0
                percent = (downloaded / total * 100.0) if total > 0 else 0.0

                if progress_callback:
                    event = {
                        "status": "downloading",
                        "percent": percent,
                        "downloaded_bytes": downloaded,
                        "total_bytes": total,
                        "speed_bytes_per_sec": speed,
                        "eta_seconds": eta,
                        "filename": d.get("filename", "")
                    }
                    progress_callback(json.dumps(event))

            elif status == "finished":
                downloaded_file[0] = d.get("filename")
                if progress_callback:
                    progress_callback(json.dumps({
                        "status": "processing",
                        "message": "Finalizing and converting media..."
                    }))

        opts = {
            "outtmpl": output_template,
            "format": format_str,
            "quiet": True,
            "no_warnings": True,
            "noprogress": True,
            "progress_hooks": [ydl_progress_hook],
            "remote_components": ["ejs:github"],
            "noplaylist": True,
            "retries": 10,
            "fragment_retries": 10,
        }

        if format_type.lower() == "audio":
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }]

        if cookie_file and os.path.isfile(cookie_file):
            opts["cookiefile"] = cookie_file

        try:
            with self.yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(clean_url, download=True)
                final_path = ydl.prepare_filename(info)
                if format_type.lower() == "audio":
                    # Audio post-processor might change extension to .mp3
                    base, _ = os.path.splitext(final_path)
                    final_path = base + ".mp3"

                return json.dumps({
                    "status": "completed",
                    "file_path": final_path,
                    "title": info.get("title", ""),
                    "uploader": info.get("uploader", ""),
                    "duration": info.get("duration", 0),
                    "size_bytes": os.path.getsize(final_path) if os.path.exists(final_path) else 0
                })
        except CancelledException:
            return json.dumps({
                "status": "cancelled",
                "message": "Download was cancelled by user"
            })
        except Exception as e:
            return json.dumps({
                "status": "error",
                "message": str(e),
                "traceback": traceback.format_exc()
            })
