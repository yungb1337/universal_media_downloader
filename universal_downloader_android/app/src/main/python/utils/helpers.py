import re
import string


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
