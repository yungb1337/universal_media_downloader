import os
import sys
import glob
import importlib

_CURRENT_ENGINE = None

def init_engine(updates_dir: str = None) -> dict:
    """
    Initializes or dynamically updates the extraction engine path.
    Scans updates_dir for injected .whl packages (e.g. yt_dlp-*.whl)
    and prepends them to sys.path so new releases take immediate priority.
    """
    global _CURRENT_ENGINE

    if updates_dir and os.path.isdir(updates_dir):
        # Look for downloaded wheel packages
        wheels = glob.glob(os.path.join(updates_dir, "yt_dlp*.whl"))
        if wheels:
            # Sort newest first
            wheels.sort(key=os.path.getmtime, reverse=True)
            active_wheel = wheels[0]
            if active_wheel not in sys.path:
                sys.path.insert(0, active_wheel)
                # Purge any previously imported cached yt_dlp modules
                to_purge = [k for k in sys.modules.keys() if k == "yt_dlp" or k.startswith("yt_dlp.")]
                for mod in to_purge:
                    del sys.modules[mod]
                importlib.invalidate_caches()

    try:
        import yt_dlp
        import yt_dlp.version
        _CURRENT_ENGINE = {
            "version": yt_dlp.version.__version__,
            "path": getattr(yt_dlp, "__file__", "bundled"),
            "status": "READY"
        }
    except Exception as e:
        _CURRENT_ENGINE = {
            "version": "UNKNOWN",
            "path": "NONE",
            "status": f"ERROR: {str(e)}"
        }

    return _CURRENT_ENGINE

def get_engine_version() -> str:
    global _CURRENT_ENGINE
    if not _CURRENT_ENGINE:
        init_engine()
    return _CURRENT_ENGINE.get("version", "UNKNOWN")

def get_engine_path() -> str:
    global _CURRENT_ENGINE
    if not _CURRENT_ENGINE:
        init_engine()
    return _CURRENT_ENGINE.get("path", "UNKNOWN")
