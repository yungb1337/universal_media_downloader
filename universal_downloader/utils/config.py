import os
import sys
import argparse
from pathlib import Path
from dotenv import load_dotenv

from utils.helpers import find_base_dir


# Load .env from project root
_base_dir = find_base_dir()
load_dotenv(_base_dir / ".env")


class Config:
    """Centralized configuration loaded from .env + CLI args."""

    def __init__(self):
        self.base_dir = _base_dir

        # ── Quality ──
        self.highest_res = os.getenv("HIGHEST_RES", "NO").strip().upper() in ("YES", "TRUE", "1")
        self.audio_only = os.getenv("DOWNLOAD_AUDIO_ONLY", "NO").strip().upper() in ("YES", "TRUE", "1")

        # ── Paths ──
        raw_dl_dir = os.getenv("DOWNLOAD_DIR", "./downloads").strip()
        self.downloads_dir = (self.base_dir / raw_dl_dir).resolve()

        raw_links = os.getenv("LINKS_FILE", "./links.txt").strip()
        self.links_file = (self.base_dir / raw_links).resolve()

        # ── Cookies ──
        self.cookies_file = os.getenv("COOKIES_FILE", "").strip() or None
        self.cookies_from_browser = os.getenv("COOKIES_FROM_BROWSER", "").strip() or None

        # Resolve cookies file path relative to base_dir if set
        if self.cookies_file:
            self.cookies_file = str((self.base_dir / self.cookies_file).resolve())

        # ── Behavior ──
        self.max_downloads = int(os.getenv("MAX_CONCURRENT_DOWNLOADS", "1"))
        self.max_retries = int(os.getenv("MAX_RETRIES", "3"))

        # ── Directories ──
        self.logs_dir = self.base_dir / "logs"
        self.logs_dir.mkdir(exist_ok=True)
        self.downloads_dir.mkdir(parents=True, exist_ok=True)

        # ── CLI overrides ──
        self._parse_args()

    def _parse_args(self):
        parser = argparse.ArgumentParser(
            description="Universal Link Downloader — powered by yt-dlp"
        )
        parser.add_argument(
            "--highest-res", action="store_true",
            help="Override HIGHEST_RES: download maximum resolution."
        )
        parser.add_argument(
            "--audio-only", action="store_true",
            help="Override DOWNLOAD_AUDIO_ONLY: extract audio as MP3 320kbps."
        )
        parser.add_argument(
            "--cookies", type=str,
            help="Path to a Netscape cookies.txt file."
        )
        parser.add_argument(
            "--cookies-from-browser", type=str,
            help="Browser to extract cookies from (chrome, firefox, edge, brave)."
        )
        parser.add_argument(
            "--links-file", type=str,
            help="Override the links file path."
        )
        parser.add_argument(
            "--dry-run", action="store_true",
            help="Parse links and show what would be downloaded, without downloading."
        )
        parser.add_argument(
            "--verbose", action="store_true",
            help="Enable debug-level logging."
        )

        args, _ = parser.parse_known_args()

        # CLI flags override .env
        if args.highest_res:
            self.highest_res = True
        if args.audio_only:
            self.audio_only = True
        if args.cookies:
            self.cookies_file = str(Path(args.cookies).resolve())
        if args.cookies_from_browser:
            self.cookies_from_browser = args.cookies_from_browser
        if args.links_file:
            self.links_file = Path(args.links_file).resolve()

        self.dry_run = args.dry_run
        self.verbose = args.verbose


# Singleton
config = Config()


# ── .env file read/write utilities ────────────────────────────────────────────
#
# These are used by the GUI to read current values and write updated
# settings back to .env before spawning the download subprocess.
# Previously these lived as private functions inside gui/app.py,
# duplicating the parsing logic.  Now there's a single implementation.
# ──────────────────────────────────────────────────────────────────────────────

def read_env_file(env_path: Path) -> dict[str, str]:
    """
    Parse a .env file into a dict, ignoring comments and blank lines.

    This is a raw key=value parser (no shell expansion, no interpolation).
    Used by the GUI to pre-fill form fields from the current .env.
    """
    env: dict[str, str] = {}
    if not env_path.exists():
        return env
    for line in env_path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key, _, val = stripped.partition("=")
            env[key.strip()] = val.strip()
    return env


def write_env_file(env_path: Path, updates: dict[str, str]):
    """
    Write updated key=value pairs back to a .env file,
    preserving comments, blank lines, and key order.

    Keys in ``updates`` that already exist in the file are replaced
    in-place.  Keys that don't exist yet are appended at the end.
    """
    if not env_path.exists():
        return

    lines = env_path.read_text(encoding="utf-8").splitlines()
    new_lines: list[str] = []
    updated: set[str] = set()

    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.split("=", 1)[0].strip()
            if key in updates:
                new_lines.append(f"{key}={updates[key]}")
                updated.add(key)
                continue
        new_lines.append(line)

    # Append any keys that didn't exist yet
    for key, val in updates.items():
        if key not in updated:
            new_lines.append(f"{key}={val}")

    env_path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")

