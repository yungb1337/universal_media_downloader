# Universal Downloader

A lightweight, robust, and site-agnostic download manager powered by `yt-dlp` and `curl-cffi`. Built to download video/audio locally from thousands of websites without complex setups, featuring advanced bot/SSL bypass and custom filename parsing.

## Features

- **1700+ Sites Supported**: Works universally on any platform supported by `yt-dlp` (YouTube, Vimeo, PornTrex, EPorner, NoodleMagazine, etc.).
- **Smart Resolution Control**:
  - Caps downloads at **1080p** by default to save bandwidth.
  - Automatically falls back to the next highest available resolution (e.g., 720p, 480p) if 1080p is unavailable.
  - Optional `HIGHEST_RES=YES` flag to download maximum resolutions (2K/4K/8K).
- **Advanced Bot & SSL Bypass**:
  - Uses `curl_cffi` to mimic Google Chrome's TLS fingerprint, bypassing Cloudflare/bot mitigation systems.
  - Bypasses strict SSL handshake exceptions (`UNEXPECTED_EOF_WHILE_READING` on OpenSSL 3.x / Python 3.10+).
  - Supports loading cookie files or importing cookies directly from your default browser.
- **Custom Filename Mapping**: Rename downloads directly from your link list using the `[URL] [Custom Name]` format.
- **Robust Error Recovery & Resume**:
  - Automatically prepends `[DONE]-` to successfully downloaded URLs in your text file.
  - Exponential backoff retry handler for both the metadata probe phase and download phase.
  - Handles ffmpeg file lock resolution on Windows systems (`WinError 32`).
- **Parallel Processing**: Supports multi-threaded downloading with a configurable worker pool.
- **Audio Extraction**: Easily toggle MP3 extraction (320kbps) with FFmpeg.

---

## Prerequisites

- **Python**: Version 3.10 or higher.
- **FFmpeg**: Must be installed and added to your system's PATH. (Required for merging video/audio streams and transcoding MP3s).

---

## Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/universal_downloader.git
   cd universal_downloader
   ```

2. Create a virtual environment and activate it:
   ```bash
   python -m venv .venv
   # Windows:
   .venv\Scripts\activate
   # Linux/macOS:
   source .venv/bin/activate
   ```

3. Install the dependencies:
   ```bash
   pip install -r requirements.txt
   ```

---

## Configuration

Copy `.env.example` to `.env` and adjust the variables:

```env
# Path to the text file containing links (one per line)
LINKS_FILE=links.txt

# Directory where downloaded files should be saved
DOWNLOADS_DIR=downloads

# Number of parallel downloads (set to 1 to download sequentially)
MAX_DOWNLOADS=1

# Maximum retries per link on connection/SSL drops
MAX_RETRIES=3

# Toggles for audio extraction & resolution
AUDIO_ONLY=NO
HIGHEST_RES=NO

# Browser to extract cookies from (chrome, firefox, edge, safari, etc.)
# Leave blank to disable
COOKIES_FROM_BROWSER=

# Path to a netscape format cookie file
# COOKIES_FILE=cookies.txt

# Log output level (YES for debug info)
VERBOSE=NO
```

---

## Usage

1. Create a `links.txt` file in the root folder.
2. Add your URLs (one per line).
3. **Optional Custom Names**: To save a video with a custom name instead of its default title, simply put a space after the URL followed by your preferred name (no extension needed):
   ```text
   https://www.youtube.com/watch?v=dQw4w9WgXcQ
   https://www.youtube.com/watch?v=NR25XauVVfw Custom Song Name
   ```
4. Run the downloader:
   ```bash
   python main.py
   ```

When a download succeeds, the script will automatically edit `links.txt` to prepend `[DONE]-` to that line. The next time you run the script, those links will be skipped.

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
