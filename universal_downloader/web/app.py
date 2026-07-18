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
BASE_DIR = find_base_dir()

# ── Dynamic Config Proxy (Thread-Safe Overrides) ──
from utils.config import config

class ThreadLocalConfigProxy:
    """
    Wraps the global config singleton so each worker thread can have its own
    custom settings (e.g. downloads folder, audio/video settings) without race conditions.
    """
    def __init__(self, base_config):
        self._base = base_config
        self._local = threading.local()

    def set_override(self, audio_only: bool, highest_res: bool, downloads_dir: Path):
        self._local.audio_only = audio_only
        self._local.highest_res = highest_res
        self._local.downloads_dir = downloads_dir

    def clear_override(self):
        if hasattr(self._local, "audio_only"):
            del self._local.audio_only
        if hasattr(self._local, "highest_res"):
            del self._local.highest_res
        if hasattr(self._local, "downloads_dir"):
            del self._local.downloads_dir

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


# ── Task Storage & App Initialization ──
app = FastAPI(title="Universal Downloader API")

DOWNLOAD_TASKS: Dict[str, dict] = {}

class DownloadRequest(BaseModel):
    url: str
    audio_only: bool = False
    highest_res: bool = False

# Ensure temp and base downloads directory exists
WEB_DOWNLOADS_ROOT = BASE_DIR / "downloads" / "web_tmp"
WEB_DOWNLOADS_ROOT.mkdir(parents=True, exist_ok=True)


# ── Task Worker Thread ──
def download_worker(task_id: str, url: str, audio_only: bool, highest_res: bool):
    thread_id = threading.get_ident()
    task_dir = WEB_DOWNLOADS_ROOT / task_id
    task_dir.mkdir(parents=True, exist_ok=True)

    # Apply configuration overrides to the proxy for this specific thread
    config_proxy.set_override(
        audio_only=audio_only,
        highest_res=highest_res,
        downloads_dir=task_dir
    )

    # Define progress callback
    def progress_callback(d: dict):
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
        
        # Start download using existing backend structure
        result = backend.download(url)
        
        if result.success and result.file_path:
            DOWNLOAD_TASKS[task_id].update({
                "status": "completed",
                "progress": 100.0,
                "title": result.title or "Downloaded File",
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
        DOWNLOAD_TASKS[task_id].update({
            "status": "failed",
            "error": str(e)
        })
    finally:
        # Clean up thread mappings
        THREAD_CALLBACKS.pop(thread_id, None)
        config_proxy.clear_override()


# ── Web Routes ──

@app.post("/api/download")
def start_download(req: DownloadRequest):
    if not req.url.strip():
        raise HTTPException(status_code=400, detail="URL cannot be empty")

    task_id = str(uuid.uuid4())
    DOWNLOAD_TASKS[task_id] = {
        "id": task_id,
        "status": "pending",
        "progress": 0.0,
        "speed": "0 B/s",
        "eta": "Pending...",
        "title": "Querying Metadata...",
        "filename": "",
        "file_path": "",
        "file_size": "",
        "error": ""
    }

    # Start download task in background thread
    t = threading.Thread(
        target=download_worker,
        args=(task_id, req.url.strip(), req.audio_only, req.highest_res),
        daemon=True
    )
    t.start()

    return {"task_id": task_id}


@app.get("/api/status/{task_id}")
def get_status(task_id: str):
    task = DOWNLOAD_TASKS.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return task


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
