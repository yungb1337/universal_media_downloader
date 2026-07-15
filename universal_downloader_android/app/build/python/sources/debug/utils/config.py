import os
import sys
import argparse
from pathlib import Path
from dotenv import load_dotenv


def _find_base_dir() -> Path:
    """Resolve the project root correctly in both normal and PyInstaller modes."""
    if getattr(sys, 'frozen', False):
        # Running as a PyInstaller bundle — use the dir that contains the .exe
        return Path(sys.executable).parent
    # Running as normal Python script
    return Path(__file__).parent.parent


# Load .env from project root
_base_dir = _find_base_dir()
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
