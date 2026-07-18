# Usage Guide

Complete reference for using Universal Downloader — CLI, GUI, configuration, and building.

---

## Table of Contents

- [Quick Start](#quick-start)
- [links.txt Format](#linkstxt-format)
- [Configuration (.env)](#configuration-env)
- [CLI Commands & Flags](#cli-commands--flags)
- [GUI Mode](#gui-mode)
- [Cookie Authentication](#cookie-authentication)
- [Building the GUI Executable](#building-the-gui-executable)
- [Examples](#examples)

---

## Quick Start

```bash
# 1. Clone and install
git clone https://github.com/yourusername/universal_downloader.git
cd universal_downloader
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # Linux/macOS
pip install -r requirements.txt

# 2. Configure
copy .env.example .env        # Windows
# cp .env.example .env        # Linux/macOS

# 3. Add links
# Open links.txt and paste URLs (one per line)

# 4. Run
python main.py                # CLI mode
python gui.py                 # GUI mode
```

> **Prerequisite:** [FFmpeg](https://ffmpeg.org/download.html) must be installed and on your system PATH. It's required for merging video/audio streams and MP3 extraction.

---

## links.txt Format

The `links.txt` file is your download queue. Place it in the project root (or set a custom path via `.env` / CLI).

### Syntax

```text
# Lines starting with # are comments (ignored)
# Blank lines are also ignored

# Basic URL — downloads with the original title
https://www.youtube.com/watch?v=dQw4w9WgXcQ

# Custom filename — add a space after the URL, then your preferred name
https://www.youtube.com/watch?v=NR25XauVVfw My Custom Song Name

# Already downloaded — the engine automatically prepends [DONE]- after success
[DONE]-https://www.youtube.com/watch?v=dQw4w9WgXcQ
```

### Rules

| Syntax | Meaning |
|--------|---------|
| `https://...` | A URL to download |
| `https://... Custom Name` | A URL with a custom output filename (no extension needed) |
| `# any text` | A comment — skipped entirely |
| *(blank line)* | Ignored |
| `[DONE]-https://...` | Already downloaded — skipped on re-run |

### How [DONE] Marking Works

1. You add URLs to `links.txt`
2. You run `python main.py`
3. Each successful download gets its line changed from:
   ```
   https://www.youtube.com/watch?v=dQw4w9WgXcQ
   ```
   to:
   ```
   [DONE]-https://www.youtube.com/watch?v=dQw4w9WgXcQ
   ```
4. Next time you run the script, `[DONE]` lines are skipped automatically
5. To re-download something, just remove the `[DONE]-` prefix

This makes the pipeline **crash-safe** — if it dies mid-batch, re-running picks up where it left off.

---

## Configuration (.env)

Copy `.env.example` to `.env` and customize:

```env
# ═══════════════════════════════════════════════════════════════
#  Universal Downloader — Configuration
# ═══════════════════════════════════════════════════════════════

# ── Quality ──────────────────────────────────────────────────
# YES = download the absolute highest resolution available (2K/4K/8K)
# NO  = cap at 1080p (falls back to 720p, 480p, etc. if unavailable)
HIGHEST_RES=NO

# YES = extract audio only as MP3 (320kbps via FFmpeg)
# NO  = download video
DOWNLOAD_AUDIO_ONLY=NO

# ── Paths ────────────────────────────────────────────────────
# Directory where downloaded files will be saved
# Relative paths are resolved from the project root
DOWNLOAD_DIR=./downloads

# File containing URLs to download (one per line)
LINKS_FILE=./links.txt

# ── Cookies (bot bypass) ─────────────────────────────────────
# Option 1: Path to a Netscape-format cookies.txt file
# COOKIES_FILE=./cookies.txt

# Option 2: Extract cookies directly from a browser
# Supported: chrome, firefox, edge, brave, opera, vivaldi
# COOKIES_FROM_BROWSER=chrome

# ── Behavior ─────────────────────────────────────────────────
# Number of parallel downloads (1 = sequential, 3-5 recommended)
MAX_CONCURRENT_DOWNLOADS=1

# Retry attempts per link on connection/SSL failures
MAX_RETRIES=3
```

### Environment Variable Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `HIGHEST_RES` | `NO` | `YES` / `NO` — Remove the 1080p resolution cap |
| `DOWNLOAD_AUDIO_ONLY` | `NO` | `YES` / `NO` — Extract audio as MP3 320kbps |
| `DOWNLOAD_DIR` | `./downloads` | Output directory for downloaded files |
| `LINKS_FILE` | `./links.txt` | Path to the file containing URLs |
| `COOKIES_FILE` | *(empty)* | Path to a Netscape-format `cookies.txt` |
| `COOKIES_FROM_BROWSER` | *(empty)* | Browser name to extract cookies from |
| `MAX_CONCURRENT_DOWNLOADS` | `1` | Number of parallel download threads |
| `MAX_RETRIES` | `3` | Retry attempts per failed download |

> **Priority:** CLI flags override `.env` values. The GUI writes to `.env` before each run.

---

## CLI Commands & Flags

### Basic Usage

```bash
# Run with .env defaults
python main.py

# GUI mode
python gui.py
```

### All CLI Flags

```bash
python main.py [OPTIONS]
```

| Flag | Description | Overrides |
|------|-------------|-----------|
| `--highest-res` | Download maximum resolution (no 1080p cap) | `HIGHEST_RES` |
| `--audio-only` | Extract audio as MP3 320kbps | `DOWNLOAD_AUDIO_ONLY` |
| `--cookies PATH` | Path to a Netscape cookies.txt file | `COOKIES_FILE` |
| `--cookies-from-browser NAME` | Browser to extract cookies from (`chrome`, `firefox`, `edge`, `brave`) | `COOKIES_FROM_BROWSER` |
| `--links-file PATH` | Override the links file path | `LINKS_FILE` |
| `--dry-run` | Show what would be downloaded without actually downloading | *(CLI only)* |
| `--verbose` | Enable debug-level logging (shows full yt-dlp output) | *(CLI only)* |

### CLI Examples

```bash
# Download all links at maximum resolution
python main.py --highest-res

# Extract audio only
python main.py --audio-only

# Use a specific cookies file
python main.py --cookies ./my_cookies.txt

# Extract cookies from Chrome automatically
python main.py --cookies-from-browser chrome

# Preview what would be downloaded (no actual downloads)
python main.py --dry-run

# Use a different links file
python main.py --links-file ./batch2.txt

# Full verbose debug output
python main.py --verbose

# Combine multiple flags
python main.py --audio-only --cookies-from-browser chrome --verbose

# Use a completely separate batch with custom settings
python main.py --links-file ./playlist.txt --highest-res --cookies ./cookies.txt
```

---

## GUI Mode

Launch the graphical interface:

```bash
python gui.py
```

### What the GUI Does

1. **Reads** your `.env` file and pre-fills all form fields
2. **You edit** settings (paths, toggles, concurrency, retries)
3. **Click Start** → the GUI saves your settings to `.env`, then spawns `main.py` as a subprocess
4. **Live log output** streams into the log panel with color-coded messages
5. **Click Stop** to terminate the download at any time

### GUI Controls

| Control | Description |
|---------|-------------|
| **Links File** | Path to your `links.txt` (with Browse button) |
| **Download Dir** | Output directory for downloads (with Browse button) |
| **Cookies File** | Optional path to `cookies.txt` (with Browse button) |
| **Max Concurrent** | Number of parallel download threads |
| **Max Retries** | Retry attempts per failed link |
| **Highest Res** | Toggle ON/OFF — removes the 1080p cap |
| **Audio Only (MP3)** | Toggle ON/OFF — extract audio as MP3 320kbps |
| **START DOWNLOAD** | Begin downloading |
| **STOP** | Terminate the current download session |
| **Clear** | Clear the log panel |

---

## Cookie Authentication

Some sites require authentication or block bot traffic. Two methods are supported:

### Method 1: Cookie File

1. Install a browser extension like [Get cookies.txt LOCALLY](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc)
2. Navigate to the site you want to download from
3. Export cookies to a `cookies.txt` file (Netscape format)
4. Set the path in `.env`:
   ```env
   COOKIES_FILE=./cookies.txt
   ```
   Or via CLI:
   ```bash
   python main.py --cookies ./cookies.txt
   ```

### Method 2: Browser Cookie Extraction

Automatically extract cookies from your installed browser:

```env
COOKIES_FROM_BROWSER=chrome
```

Or via CLI:

```bash
python main.py --cookies-from-browser chrome
```

Supported browsers: `chrome`, `firefox`, `edge`, `brave`, `opera`, `vivaldi`

> **Note:** Browser cookie extraction requires the browser to be closed. If cookies aren't working, try Method 1 instead.

---

## Building the GUI Executable

Build a standalone `.exe` using PyInstaller and the included spec file.

### Prerequisites

```bash
pip install pyinstaller
```

### Build Command

```bash
python -m PyInstaller --noconfirm "Universal Downloader.spec"
```

### What the Spec File Does

The [`Universal Downloader.spec`](Universal%20Downloader.spec) configures:

| Setting | Value | Why |
|---------|-------|-----|
| Entry point | `gui.py` | Dual-mode launcher (GUI + engine via `--run-engine`) |
| Console | `False` | No terminal window when running the `.exe` |
| Hidden imports | `yt_dlp.extractor`, `curl_cffi`, etc. | PyInstaller can't auto-detect these |
| Data files | `customtkinter`, `yt_dlp` | Theme assets and extractor data |
| Excluded | `matplotlib`, `numpy`, `pandas` | Reduces bundle size |
| UPX | `True` | Compresses the final binary |

### Output

After building:

```
dist/
└── Universal Downloader/
    ├── Universal Downloader.exe   ← Run this
    ├── _internal/                 ← Dependencies (auto-bundled)
    └── ...
```

### Running the Built Executable

1. Copy the entire `dist/Universal Downloader/` folder wherever you want
2. Place your `.env`, `links.txt`, and optional `cookies.txt` **next to the `.exe`**
3. Double-click `Universal Downloader.exe`

> **Important:** The `.env` and `links.txt` files must be in the **same directory** as the `.exe` — the app resolves paths relative to the executable location when frozen.

---

## Examples

### Download a YouTube playlist as MP3s

```text
# links.txt
https://www.youtube.com/watch?v=dQw4w9WgXcQ
https://www.youtube.com/watch?v=NR25XauVVfw
https://www.youtube.com/watch?v=haNrw-FH0Gs
```

```env
# .env
DOWNLOAD_AUDIO_ONLY=YES
MAX_CONCURRENT_DOWNLOADS=3
```

```bash
python main.py
```

### Download videos from mixed sites at 1080p

```text
# links.txt
https://www.youtube.com/watch?v=dQw4w9WgXcQ
https://vimeo.com/123456789
https://www.twitch.tv/videos/987654321
```

```bash
python main.py --cookies-from-browser chrome
```

### Download with custom filenames

```text
# links.txt
https://www.youtube.com/watch?v=dQw4w9WgXcQ Never Gonna Give You Up
https://www.youtube.com/watch?v=NR25XauVVfw My Favorite Remix
```

```bash
python main.py
```

Output:
```
downloads/
├── Never Gonna Give You Up [dQw4w9WgXcQ].mp4
└── My Favorite Remix [NR25XauVVfw].mp4
```

### Dry run to preview without downloading

```bash
python main.py --dry-run
```

Output:
```
DRY RUN — Links that would be downloaded:
═══════════════════════════════════════════
  1. [Line 1] https://www.youtube.com/watch?v=dQw4w9WgXcQ
  2. [Line 2] https://www.youtube.com/watch?v=NR25XauVVfw
═══════════════════════════════════════════
Total: 2 link(s)
```
