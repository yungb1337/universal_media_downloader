import os
import uuid
import shutil
import threading
import time
from pathlib import Path
from typing import Dict, Optional
from pydantic import BaseModel

from fastapi import FastAPI, BackgroundTasks, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles

# Save the base dir of the project
from utils.helpers import find_base_dir, format_filesize, format_duration
from utils.logger import logger
BASE_DIR = find_base_dir()

# ── Dynamic Config Proxy (Thread-Safe Overrides) ──
from utils.config import config

class ThreadLocalConfigProxy:
    """
    Wraps the global config singleton so each worker thread can have its own
    custom settings (downloads folder, audio/video settings, browser/file cookies)
    without causing race conditions under concurrent downloads.
    """
    def __init__(self, base_config):
        self._base = base_config
        self._local = threading.local()

    def set_override(self, audio_only: bool, highest_res: bool, downloads_dir: Path, 
                     cookies_from_browser: Optional[str] = None, cookies_file: Optional[str] = None):
        self._local.audio_only = audio_only
        self._local.highest_res = highest_res
        self._local.downloads_dir = downloads_dir
        self._local.cookies_from_browser = cookies_from_browser
        self._local.cookies_file = cookies_file

    def clear_override(self):
        if hasattr(self._local, "audio_only"): del self._local.audio_only
        if hasattr(self._local, "highest_res"): del self._local.highest_res
        if hasattr(self._local, "downloads_dir"): del self._local.downloads_dir
        if hasattr(self._local, "cookies_from_browser"): del self._local.cookies_from_browser
        if hasattr(self._local, "cookies_file"): del self._local.cookies_file

    def __getattr__(self, name):
        if hasattr(self._local, name):
            return getattr(self._local, name)
        return getattr(self._base, name)

# Patch the ytdlp_backend config with our proxy
import backends.ytdlp_backend
config_proxy = ThreadLocalConfigProxy(config)
backends.ytdlp_backend.config = config_proxy


# ── Thread-Safe Custom Progress Interceptor ──
OriginalTqdmProgressHook = backends.ytdlp_backend.TqdmProgressHook
THREAD_CALLBACKS: Dict[int, callable] = {}

class DynamicProgressHook:
    """
    Intercepts TqdmProgressHook creation inside YtDlpBackend and routes callbacks
    to the active thread's web progress handler. Falls back to original CLI tqdm otherwise.
    """
    def __init__(self):
        thread_id = threading.get_ident()
        self.callback = THREAD_CALLBACKS.get(thread_id)
        if not self.callback:
            self.original = OriginalTqdmProgressHook()
        else:
            self.original = None

    def __call__(self, d: dict):
        if self.callback:
            self.callback(d)
        elif self.original:
            self.original(d)

    def close(self):
        if self.original:
            self.original.close()

# Apply hook interception
backends.ytdlp_backend.TqdmProgressHook = DynamicProgressHook


# ── App Initialization & Configuration State ──
app = FastAPI(title="Universal Downloader API")

MAX_CONCURRENT = 2
DOWNLOAD_TASKS: Dict[str, dict] = {}
ACTIVE_THREADS: Dict[str, threading.Thread] = {}
QUEUE_LOCK = threading.Lock()

class DownloadRequest(BaseModel):
    url: str
    audio_only: bool = False
    highest_res: bool = False
    cookies_from_browser: Optional[str] = None
    cookies_file_content: Optional[str] = None

class SettingsRequest(BaseModel):
    max_concurrent: int

# Ensure temp and base downloads directory exists
WEB_DOWNLOADS_ROOT = BASE_DIR / "downloads" / "web_tmp"
WEB_DOWNLOADS_ROOT.mkdir(parents=True, exist_ok=True)


# ── View Formatter & Metadata Extractor ──
def format_views(count) -> str:
    if not count:
        return "Unknown views"
    try:
        count = int(count)
        if count >= 1_000_000:
            return f"{count / 1_000_000:.1f}M views"
        elif count >= 1_000:
            return f"{count / 1_000:.1f}K views"
        return f"{count} views"
    except Exception:
        return "Unknown views"

def extract_metadata(url: str, cookies_from_browser: Optional[str], cookies_file: Optional[str]) -> dict:
    import yt_dlp
    from yt_dlp.networking.impersonate import ImpersonateTarget
    
    probe_opts = {
        "extract_flat": False,
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "impersonate": ImpersonateTarget(client="chrome"),
    }
    if cookies_file:
        probe_opts["cookiefile"] = cookies_file
    elif cookies_from_browser:
        probe_opts["cookiesfrombrowser"] = (cookies_from_browser,)
        
    with yt_dlp.YoutubeDL(probe_opts) as ydl:
        info = ydl.extract_info(url, download=False)
        
        # Thumbnail extraction
        thumbnail = info.get("thumbnail")
        if not thumbnail and info.get("thumbnails"):
            thumbnail = info["thumbnails"][-1].get("url")
            
        return {
            "title": info.get("title", "Unknown Title"),
            "thumbnail": thumbnail or "https://img.icons8.com/neon/96/download.png",
            "views": info.get("view_count")
        }


# ── Download Task Worker Thread ──
def download_worker(task_id: str, url: str, audio_only: bool, highest_res: bool, 
                    cookies_from_browser: Optional[str], cookies_file_content: Optional[str]):
    thread_id = threading.get_ident()
    task_dir = WEB_DOWNLOADS_ROOT / task_id
    task_dir.mkdir(parents=True, exist_ok=True)

    # Resolve cookies
    cookies_path = None
    if cookies_file_content and cookies_file_content.strip():
        c_path = task_dir / "cookies.txt"
        c_path.write_text(cookies_file_content, encoding="utf-8")
        cookies_path = str(c_path)

    # 1. Fetch Metadata (Title, views, thumbnail)
    try:
        meta = extract_metadata(url, cookies_from_browser, cookies_path)
        DOWNLOAD_TASKS[task_id].update({
            "title": meta["title"],
            "thumbnail": meta["thumbnail"],
            "views": format_views(meta["views"])
        })
    except Exception as e:
        logger.warning(f"Failed to extract metadata: {e}")
        DOWNLOAD_TASKS[task_id].update({
            "title": "Unknown Title",
            "thumbnail": "https://img.icons8.com/neon/96/download.png",
            "views": "Unknown views"
        })

    # Abort if user cancelled during metadata extraction
    if DOWNLOAD_TASKS[task_id].get("status") == "cancelled":
        shutil.rmtree(task_dir, ignore_errors=True)
        return

    # 2. Configure proxy settings for this specific download thread
    config_proxy.set_override(
        audio_only=audio_only,
        highest_res=highest_res,
        downloads_dir=task_dir,
        cookies_from_browser=cookies_from_browser,
        cookies_file=cookies_path
    )

    # Define progress callback with support for Pause and Cancel
    def progress_callback(d: dict):
        # Handle User Cancel
        if DOWNLOAD_TASKS[task_id].get("status") == "cancelled":
            raise Exception("Download cancelled by user")

        # Handle User Pause (sleep-loop blocking)
        while DOWNLOAD_TASKS[task_id].get("paused"):
            time.sleep(0.5)
            if DOWNLOAD_TASKS[task_id].get("status") == "cancelled":
                raise Exception("Download cancelled by user")

        if d["status"] == "downloading":
            downloaded = d.get("downloaded_bytes", 0)
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            percent = (downloaded / total * 100) if total > 0 else 0.0

            speed_val = d.get("speed", 0)
            speed = format_filesize(speed_val) + "/s" if speed_val else "Calculating..."

            eta_val = d.get("eta", 0)
            eta = format_duration(eta_val) if eta_val else "Calculating..."

            DOWNLOAD_TASKS[task_id].update({
                "status": "downloading",
                "progress": round(percent, 1),
                "speed": speed,
                "eta": eta
            })
        elif d["status"] == "finished":
            DOWNLOAD_TASKS[task_id].update({
                "status": "processing",
                "progress": 100.0,
                "speed": "0 B/s",
                "eta": "0s"
            })

    THREAD_CALLBACKS[thread_id] = progress_callback

    try:
        from backends.ytdlp_backend import YtDlpBackend
        backend = YtDlpBackend()
        
        # Start download
        result = backend.download(url)
        
        if result.success and result.file_path:
            DOWNLOAD_TASKS[task_id].update({
                "status": "completed",
                "progress": 100.0,
                "filename": result.file_path.name,
                "file_path": str(result.file_path),
                "file_size": format_filesize(result.file_size_bytes)
            })
        else:
            DOWNLOAD_TASKS[task_id].update({
                "status": "failed",
                "error": result.error or "Unknown error occurred during download."
            })
    except Exception as e:
        if DOWNLOAD_TASKS[task_id].get("status") == "cancelled":
            DOWNLOAD_TASKS[task_id].update({
                "status": "cancelled",
                "error": "Download cancelled by user."
            })
        else:
            DOWNLOAD_TASKS[task_id].update({
                "status": "failed",
                "error": str(e)
            })
        # Cleanup partial files on failure or cancellation
        shutil.rmtree(task_dir, ignore_errors=True)
    finally:
        # Clean up thread mappings
        THREAD_CALLBACKS.pop(thread_id, None)
        config_proxy.clear_override()
        with QUEUE_LOCK:
            ACTIVE_THREADS.pop(task_id, None)


# ── Concurrency Queue Manager Thread ──
def get_active_count():
    return sum(1 for t in DOWNLOAD_TASKS.values() if t["status"] in ("pending", "downloading", "processing", "paused"))

def queue_processor():
    while True:
        time.sleep(0.5)
        with QUEUE_LOCK:
            active_count = get_active_count()
            if active_count >= MAX_CONCURRENT:
                continue

            # Find the next queued task
            next_task_id = None
            for t_id, task in DOWNLOAD_TASKS.items():
                if task["status"] == "queued":
                    next_task_id = t_id
                    break

            if next_task_id:
                task = DOWNLOAD_TASKS[next_task_id]
                t = threading.Thread(
                    target=download_worker,
                    args=(next_task_id, task["url"], task["audio_only"], task["highest_res"], 
                          task["cookies_from_browser"], task["cookies_file_content"]),
                    daemon=True
                )
                ACTIVE_THREADS[next_task_id] = t
                # Switch status to pending (metadata phase) immediately so it doesn't get re-scheduled
                task["status"] = "pending"
                t.start()


@app.on_event("startup")
def start_queue():
    t = threading.Thread(target=queue_processor, daemon=True)
    t.start()


# ── API Routes ──

@app.post("/api/download")
def start_download(req: DownloadRequest):
    if not req.url.strip():
        raise HTTPException(status_code=400, detail="URL cannot be empty")

    task_id = str(uuid.uuid4())
    DOWNLOAD_TASKS[task_id] = {
        "id": task_id,
        "url": req.url.strip(),
        "audio_only": req.audio_only,
        "highest_res": req.highest_res,
        "cookies_from_browser": req.cookies_from_browser,
        "cookies_file_content": req.cookies_file_content,
        
        "status": "queued",
        "progress": 0.0,
        "speed": "0 B/s",
        "eta": "Waiting in queue...",
        "title": "Queued in download list...",
        "thumbnail": "https://img.icons8.com/neon/96/download.png",
        "views": "",
        "filename": "",
        "file_path": "",
        "file_size": "",
        "error": "",
        "paused": False
    }

    return {"task_id": task_id}


@app.get("/api/status/{task_id}")
def get_status(task_id: str):
    task = DOWNLOAD_TASKS.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return task


@app.post("/api/pause/{task_id}")
def pause_task(task_id: str):
    task = DOWNLOAD_TASKS.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    if task["status"] not in ("downloading", "pending"):
        raise HTTPException(status_code=400, detail="Task is not active and cannot be paused")
    task["paused"] = True
    task["status"] = "paused"
    return {"status": "paused"}


@app.post("/api/resume/{task_id}")
def resume_task(task_id: str):
    task = DOWNLOAD_TASKS.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    if task["status"] != "paused":
        raise HTTPException(status_code=400, detail="Task is not paused")
    task["paused"] = False
    task["status"] = "downloading"
    return {"status": "downloading"}


@app.post("/api/cancel/{task_id}")
def cancel_task(task_id: str):
    task = DOWNLOAD_TASKS.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    # If it is in queued state, cancel it instantly
    if task["status"] == "queued":
        task["status"] = "cancelled"
        task["error"] = "Cancelled by user."
    else:
        task["status"] = "cancelled"
        
    return {"status": "cancelled"}


@app.get("/api/settings")
def get_settings():
    return {"max_concurrent": MAX_CONCURRENT}


@app.post("/api/settings")
def update_settings(req: SettingsRequest):
    global MAX_CONCURRENT
    if req.max_concurrent < 1 or req.max_concurrent > 10:
        raise HTTPException(status_code=400, detail="Max concurrent downloads must be between 1 and 10")
    MAX_CONCURRENT = req.max_concurrent
    return {"max_concurrent": MAX_CONCURRENT}


@app.get("/api/file/{task_id}")
def get_file(task_id: str, background_tasks: BackgroundTasks):
    task = DOWNLOAD_TASKS.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    if task["status"] != "completed":
        raise HTTPException(status_code=400, detail="Task is not completed yet")

    file_path = Path(task["file_path"])
    if not file_path.exists():
        raise HTTPException(status_code=404, detail="File has been cleaned up or does not exist")

    # Clean up the task's temp folder after sending the response to the user
    def cleanup_temp_dir(path: Path):
        try:
            parent = path.parent
            if parent.exists() and parent.name == task_id:
                # Sleep a little to ensure file handles are released
                time.sleep(2)
                shutil.rmtree(parent, ignore_errors=True)
        except Exception as e:
            print(f"Error cleaning up folder {path.parent}: {e}")

    background_tasks.add_task(cleanup_temp_dir, file_path)

    return FileResponse(
        path=file_path,
        filename=task["filename"],
        media_type="application/octet-stream"
    )


# Serve Static files (Frontend UI)
static_path = BASE_DIR / "web" / "static"
static_path.mkdir(parents=True, exist_ok=True)

# Mount static folder
app.mount("/static", StaticFiles(directory=str(static_path)), name="static")

@app.get("/", response_class=HTMLResponse)
def get_home():
    index_file = static_path / "index.html"
    if index_file.exists():
        return index_file.read_text(encoding="utf-8")
    return """
    <html>
        <body style="font-family: sans-serif; text-align: center; padding-top: 100px; background: #0b0b14; color: #fff;">
            <h1>Universal Downloader UI Setup</h1>
            <p>UI file index.html is being generated. Please refresh in a moment...</p>
        </body>
    </html>
    """
