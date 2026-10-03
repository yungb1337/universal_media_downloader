import urllib.parse
import re

def clean_youtube_url(url: str) -> str:
    """
    Strips transient YouTube radio/mix parameters (list=RD..., start_radio=1, index=..., rv=...)
    from video URLs while keeping legitimate user playlists (list=PL...) intact.
    """
    try:
        parsed = urllib.parse.urlparse(url)
        hostname = (parsed.hostname or "").lower()
        if "youtube.com" in hostname or "youtu.be" in hostname:
            qs = urllib.parse.parse_qs(parsed.query)
            if "v" in qs:
                vid = qs["v"][0]
                list_id = qs.get("list", [""])[0]
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

def format_size(bytes_val: int) -> str:
    """Formats byte counts into human-readable strings (KB, MB, GB)."""
    if not bytes_val or bytes_val <= 0:
        return "0 B"
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if bytes_val < 1024.0:
            return f"{bytes_val:.1f} {unit}"
        bytes_val /= 1024.0
    return f"{bytes_val:.1f} PB"

def sanitize_filename(name: str) -> str:
    """Removes invalid filesystem characters."""
    return re.sub(r'[\\/*?:"<>|]', "_", name).strip()
