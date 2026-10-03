import re
import string
import sys
import urllib.parse
from pathlib import Path


def clean_youtube_url(url: str) -> str:
    """
    Strips transient YouTube Mix/Radio and tracking parameters (such as
    list=RD..., start_radio=1, index=..., rv=...) from video URLs so that
    individual videos are downloaded directly rather than attempting to scrape
    an infinite/algorithmic recommendation playlist.

    Preserves actual user/channel playlists (e.g. playlist?list=PL... or list=OLAK...).
    """
    try:
        parsed = urllib.parse.urlparse(url)
        hostname = (parsed.hostname or "").lower()
        if "youtube.com" in hostname or "youtu.be" in hostname:
            qs = urllib.parse.parse_qs(parsed.query)
            if "v" in qs:
                vid = qs["v"][0]
                list_id = qs.get("list", [""])[0]
                # If list is an auto-generated Mix (starts with RD or UL) or has radio/index params
                if (
                    list_id.startswith("RD")
                    or list_id.startswith("UL")
                    or "start_radio" in qs
                    or "index" in qs
                    or "rv" in qs
                ):
                    return f"https://www.youtube.com/watch?v={vid}"
            elif "youtu.be" in hostname:
                vid = parsed.path.strip("/").split("/")[0]
                if vid:
                    list_id = qs.get("list", [""])[0]
                    if (
                        list_id.startswith("RD")
                        or list_id.startswith("UL")
                        or "start_radio" in qs
                        or "index" in qs
                    ):
                        return f"https://youtu.be/{vid}"
    except Exception:
        pass
    return url


def find_base_dir() -> Path:
    """
    Resolve the project root directory.

    Handles both normal Python execution and PyInstaller bundles.
    This is the SINGLE source of truth — all modules should import
    this instead of defining their own _find_base_dir().

    When frozen (PyInstaller):  directory containing the .exe
    When normal:                two levels up from this file (utils/ → project root)
    """
    if getattr(sys, 'frozen', False):
        return Path(sys.executable).parent
    return Path(__file__).parent.parent




# Regex to find YouTube ID in filenames like "Title [abc12345678].mp4"
YT_ID_REGEX = re.compile(r"\[([a-zA-Z0-9_-]{11})\]")


def extract_video_id(filename: str) -> str | None:
    """
    Extracts the 11-character YouTube ID from a filename
    if it's enclosed in square brackets.
    """
    match = YT_ID_REGEX.search(filename)
    if match:
        return match.group(1)
    return None


def sanitize_filename(filename: str) -> str:
    """
    Removes characters that are invalid for Windows file systems.
    Preserves unicode letters (non-ASCII) for international titles.
    """
    if not filename:
        return "Unknown"

    # Remove characters illegal on Windows: \ / : * ? " < > |
    sanitized = re.sub(r'[\\/:*?"<>|]', "", filename)

    # Collapse multiple spaces/dots
    sanitized = re.sub(r"\s+", " ", sanitized)
    sanitized = re.sub(r"\.{2,}", ".", sanitized)

    return sanitized.strip()


def format_duration(seconds: int | float | None) -> str:
    """Formats seconds into HH:MM:SS or MM:SS."""
    if seconds is None:
        return "??:??"
    seconds = int(seconds)
    hours, remainder = divmod(seconds, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours > 0:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    return f"{minutes}:{secs:02d}"


def format_filesize(size_bytes: int | float | None) -> str:
    """Formats bytes into a human-readable string."""
    if size_bytes is None or size_bytes <= 0:
        return "?? MB"
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(size_bytes) < 1024.0:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.1f} PB"
