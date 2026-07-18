"""
app.py — Main CTk application window for Universal Downloader.

Reads .env → pre-fills fields → user edits → clicks Start →
  writes .env → spawns main.py subprocess → streams output to LogPanel.

The links.txt [DONE]- write-back is handled automatically by the
existing engine (core/parser.py mark_done), so no extra logic needed.
"""

import queue
import re
import sys
from pathlib import Path

import customtkinter as ctk

from gui.widgets import BrowseEntry, LogPanel, ToggleButton, COLORS
from gui.runner import DownloadRunner
from utils.helpers import find_base_dir
from utils.config import read_env_file, write_env_file


BASE_DIR = find_base_dir()
ENV_FILE = BASE_DIR / ".env"


# ── App ────────────────────────────────────────────────────────────────────────

class App(ctk.CTk):
    """Main application window."""

    WIDTH  = 760
    HEIGHT = 680

    def __init__(self):
        super().__init__()

        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("dark-blue")

        self.title("Universal Downloader")
        self.geometry(f"{self.WIDTH}x{self.HEIGHT}")
        self.minsize(680, 580)
        self.configure(fg_color=COLORS["bg"])

        self._log_queue: queue.Queue = queue.Queue()
        self._runner: DownloadRunner | None = None
        self._active_downloads = {}

        self._build_ui()
        self._load_env()
        self._poll_logs()   # start the 100ms polling loop

    # ── Build UI ───────────────────────────────────────────────────────────────

    def _build_ui(self):
        self.columnconfigure(0, weight=1)
        self.rowconfigure(4, weight=1)   # log panel expands

        # ── Header ──────────────────────────────────────────────────────────
        header = ctk.CTkFrame(self, fg_color=COLORS["panel"], corner_radius=0)
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            header,
            text="🌐  Universal Downloader",
            font=("Segoe UI", 20, "bold"),
            text_color=COLORS["text"],
        ).grid(row=0, column=0, pady=14, padx=20, sticky="w")

        ctk.CTkLabel(
            header,
            text="Powered by yt-dlp",
            font=("Segoe UI", 11),
            text_color=COLORS["subtext"],
        ).grid(row=1, column=0, pady=(0, 12), padx=20, sticky="w")

        # ── Config form ─────────────────────────────────────────────────────
        form = ctk.CTkFrame(self, fg_color=COLORS["surface"], corner_radius=10)
        form.grid(row=1, column=0, sticky="ew", padx=16, pady=12)
        form.columnconfigure(0, weight=1)

        # File/folder pickers
        self._links_entry = BrowseEntry(form, "Links File:", mode="file", placeholder="path/to/links.txt")
        self._links_entry.grid(row=0, column=0, padx=16, pady=(14, 6), sticky="ew")

        self._dl_dir_entry = BrowseEntry(form, "Download Dir:", mode="dir", placeholder="path/to/downloads/")
        self._dl_dir_entry.grid(row=1, column=0, padx=16, pady=6, sticky="ew")

        self._cookies_entry = BrowseEntry(form, "Cookies File:", mode="file", placeholder="path/to/cookies.txt (optional)")
        self._cookies_entry.grid(row=2, column=0, padx=16, pady=(6, 14), sticky="ew")

        # Separator
        ctk.CTkFrame(form, height=1, fg_color=COLORS["border"]).grid(
            row=3, column=0, sticky="ew", padx=16
        )

        # Numeric row
        num_row = ctk.CTkFrame(form, fg_color="transparent")
        num_row.grid(row=4, column=0, padx=16, pady=10, sticky="ew")

        ctk.CTkLabel(
            num_row, text="Max Concurrent:", width=130, anchor="w",
            text_color=COLORS["subtext"], font=("Segoe UI", 12)
        ).pack(side="left")

        self._max_concurrent = ctk.CTkEntry(
            num_row, width=50, height=34,
            fg_color=COLORS["bg"], border_color=COLORS["border"],
            text_color=COLORS["text"], font=("Segoe UI", 12), justify="center"
        )
        self._max_concurrent.pack(side="left", padx=(0, 24))

        ctk.CTkLabel(
            num_row, text="Max Retries:", width=90, anchor="w",
            text_color=COLORS["subtext"], font=("Segoe UI", 12)
        ).pack(side="left")

        self._max_retries = ctk.CTkEntry(
            num_row, width=50, height=34,
            fg_color=COLORS["bg"], border_color=COLORS["border"],
            text_color=COLORS["text"], font=("Segoe UI", 12), justify="center"
        )
        self._max_retries.pack(side="left")

        # Toggle buttons
        toggle_row = ctk.CTkFrame(form, fg_color="transparent")
        toggle_row.grid(row=5, column=0, padx=16, pady=(4, 14), sticky="ew")
        toggle_row.columnconfigure((0, 1), weight=1)

        self._highest_res_btn = ToggleButton(toggle_row, "Highest Res", initial=False)
        self._highest_res_btn.grid(row=0, column=0, padx=(0, 8), sticky="ew")

        self._audio_only_btn = ToggleButton(toggle_row, "Audio Only (MP3)", initial=False)
        self._audio_only_btn.grid(row=0, column=1, padx=(8, 0), sticky="ew")

        # ── Action row ──────────────────────────────────────────────────────
        action_row = ctk.CTkFrame(self, fg_color="transparent")
        action_row.grid(row=2, column=0, sticky="ew", padx=16, pady=(0, 8))
        action_row.columnconfigure(0, weight=1)

        self._start_btn = ctk.CTkButton(
            action_row,
            text="▶   START DOWNLOAD",
            height=46,
            fg_color=COLORS["accent"],
            hover_color="#c0392b",
            text_color="white",
            font=("Segoe UI", 15, "bold"),
            corner_radius=8,
            command=self._on_start,
        )
        self._start_btn.grid(row=0, column=0, sticky="ew", padx=(0, 8))

        self._stop_btn = ctk.CTkButton(
            action_row,
            text="⏹  STOP",
            height=46,
            width=120,
            fg_color=COLORS["panel"],
            hover_color=COLORS["accent2"],
            text_color="white",
            font=("Segoe UI", 15, "bold"),
            corner_radius=8,
            command=self._on_stop,
            state="disabled",
        )
        self._stop_btn.grid(row=0, column=1)

        # ── Progress panel ──────────────────────────────────────────────────
        self._progress_frame = ctk.CTkFrame(self, fg_color=COLORS["surface"], corner_radius=10)
        self._progress_frame.grid(row=3, column=0, sticky="ew", padx=16, pady=(0, 10))
        self._progress_frame.columnconfigure(0, weight=1)

        self._progress_label = ctk.CTkLabel(
            self._progress_frame,
            text="Idle",
            font=("Segoe UI", 12, "bold"),
            text_color=COLORS["subtext"],
            anchor="w",
            justify="left",
        )
        self._progress_label.grid(row=0, column=0, padx=16, pady=(10, 4), sticky="w")

        self._progress_bar = ctk.CTkProgressBar(
            self._progress_frame,
            fg_color=COLORS["bg"],
            progress_color=COLORS["download"],
            height=10,
        )
        self._progress_bar.grid(row=1, column=0, padx=16, pady=(0, 12), sticky="ew")
        self._progress_bar.set(0)

        # ── Log panel ───────────────────────────────────────────────────────
        log_frame = ctk.CTkFrame(self, fg_color=COLORS["surface"], corner_radius=10)
        log_frame.grid(row=4, column=0, sticky="nsew", padx=16, pady=(0, 14))
        log_frame.rowconfigure(1, weight=1)
        log_frame.columnconfigure(0, weight=1)

        log_header = ctk.CTkFrame(log_frame, fg_color=COLORS["panel"], corner_radius=0)
        log_header.grid(row=0, column=0, sticky="ew")
        log_header.columnconfigure(0, weight=1)

        ctk.CTkLabel(
            log_header, text=" 📋  Live Output",
            font=("Segoe UI", 12, "bold"),
            text_color=COLORS["subtext"],
        ).grid(row=0, column=0, padx=12, pady=6, sticky="w")

        ctk.CTkButton(
            log_header, text="Clear", width=55, height=24,
            fg_color="transparent", hover_color=COLORS["border"],
            text_color=COLORS["subtext"], font=("Segoe UI", 11),
            command=lambda: self._log.clear(),
        ).grid(row=0, column=1, padx=8, pady=6)

        self._log = LogPanel(log_frame)
        self._log.grid(row=1, column=0, sticky="nsew", padx=8, pady=8)

        self._log.append("Ready. Configure settings and press Start.", tag="info")

    # ── .env I/O ───────────────────────────────────────────────────────────────

    def _load_env(self):
        """Read .env and pre-fill all form fields."""
        env = read_env_file(ENV_FILE)

        # Resolve relative paths from BASE_DIR
        def resolve(key, default=""):
            raw = env.get(key, default).strip()
            if not raw:
                return ""
            p = Path(raw)
            if not p.is_absolute():
                p = (BASE_DIR / p).resolve()
            return str(p)

        self._links_entry.set(resolve("LINKS_FILE", "./links.txt"))
        self._dl_dir_entry.set(resolve("DOWNLOAD_DIR", "./downloads"))
        self._cookies_entry.set(resolve("COOKIES_FILE", ""))

        # Numerics
        self._max_concurrent.insert(0, env.get("MAX_CONCURRENT_DOWNLOADS", "1"))
        self._max_retries.insert(0, env.get("MAX_RETRIES", "5"))

        # Toggles
        self._highest_res_btn.set(env.get("HIGHEST_RES", "NO").upper() in ("YES", "TRUE", "1"))
        self._audio_only_btn.set(env.get("DOWNLOAD_AUDIO_ONLY", "NO").upper() in ("YES", "TRUE", "1"))

    def _save_env(self):
        """Write current form values back to .env."""
        write_env_file(ENV_FILE, {
            "LINKS_FILE":              self._links_entry.get() or "./links.txt",
            "DOWNLOAD_DIR":            self._dl_dir_entry.get() or "./downloads",
            "COOKIES_FILE":            self._cookies_entry.get(),
            "MAX_CONCURRENT_DOWNLOADS": self._max_concurrent.get().strip() or "1",
            "MAX_RETRIES":             self._max_retries.get().strip() or "5",
            "HIGHEST_RES":             "YES" if self._highest_res_btn.get() else "NO",
            "DOWNLOAD_AUDIO_ONLY":     "YES" if self._audio_only_btn.get() else "NO",
        })

    # ── Actions ────────────────────────────────────────────────────────────────

    def _on_start(self):
        if self._runner and self._runner.running:
            return

        self._save_env()
        self._log.clear()
        self._log.append("⚙️  Saving configuration...", tag="info")
        self._log.append("▶  Starting download session...", tag="download")

        self._active_downloads = {}
        self._progress_bar.set(0)
        self._progress_label.configure(text="Preparing downloads...", text_color=COLORS["subtext"])

        self._start_btn.configure(state="disabled", text="⏳  Running...")
        self._stop_btn.configure(state="normal")

        self._runner = DownloadRunner(self._log_queue)
        self._runner.start()

    def _on_stop(self):
        if self._runner:
            self._runner.stop()
        self._active_downloads = {}
        self._progress_bar.set(0)
        self._progress_label.configure(text="Idle", text_color=COLORS["subtext"])
        self._reset_buttons()

    def _reset_buttons(self):
        self._start_btn.configure(state="normal", text="▶   START DOWNLOAD")
        self._stop_btn.configure(state="disabled")

    # ── Log polling & Progress ──────────────────────────────────────────────────

    def _poll_logs(self):
        """Drain the log queue and refresh buttons — called every 100ms."""
        try:
            while True:
                line, tag = self._log_queue.get_nowait()
                if tag == "progress":
                    self._update_progress(line)
                    continue
                self._log.append(line, tag=tag)
        except queue.Empty:
            pass

        # Auto-reset buttons when runner finishes
        if self._runner and not self._runner.running:
            if self._start_btn.cget("state") == "disabled":
                self._reset_buttons()
                self._active_downloads = {}
                self._progress_bar.set(0)
                self._progress_label.configure(text="Idle", text_color=COLORS["subtext"])

        self.after(100, self._poll_logs)

    def _update_progress(self, data: dict):
        """Update progress bar and status labels based on structured JSON progress."""
        from utils.helpers import format_filesize, format_duration

        filename = data.get("filename", "Unknown")
        status = data.get("status")

        if status in ("finished", "error"):
            self._active_downloads.pop(filename, None)
        elif status == "downloading":
            self._active_downloads[filename] = {
                "downloaded": data.get("downloaded", 0),
                "total": data.get("total", 0),
                "speed": data.get("speed"),
                "eta": data.get("eta"),
            }

        # If no active downloads:
        if not self._active_downloads:
            if self._runner and self._runner.running:
                self._progress_label.configure(text="Waiting for next download...", text_color=COLORS["subtext"])
            else:
                self._progress_label.configure(text="Idle", text_color=COLORS["subtext"])
            self._progress_bar.set(0)
            return

        # Calculate totals
        total_downloaded = 0
        total_size = 0
        total_speed = 0
        valid_speeds = 0
        etas = []

        for item in self._active_downloads.values():
            total_downloaded += item["downloaded"]
            if item["total"] > 0:
                total_size += item["total"]
            
            speed = item["speed"]
            if speed is not None:
                total_speed += speed
                valid_speeds += 1

            eta = item["eta"]
            if eta is not None:
                etas.append(eta)

        # Progress fraction
        if total_size > 0:
            fraction = min(max(total_downloaded / total_size, 0.0), 1.0)
        else:
            fraction = 0.0

        self._progress_bar.set(fraction)

        # Status text
        count = len(self._active_downloads)
        speed_str = format_filesize(total_speed) + "/s" if valid_speeds > 0 else "connecting..."
        eta_str = f"ETA: {format_duration(min(etas))}" if etas else ""

        if count == 1:
            # Show file-specific detail
            fname = list(self._active_downloads.keys())[0]
            file_percent = int(fraction * 100) if total_size > 0 else 0
            display_name = fname[:40] + "..." if len(fname) > 40 else fname
            status_text = f"Downloading: {display_name} ({file_percent}% of {format_filesize(total_size)} @ {speed_str})  {eta_str}"
        else:
            # Multi-file detail
            batch_percent = int(fraction * 100)
            status_text = f"Downloading {count} files ({batch_percent}% @ {speed_str})  {eta_str}"

        self._progress_label.configure(text=status_text, text_color=COLORS["download"])
