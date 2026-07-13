"""
gui.py — Dual-mode entry point for Universal Downloader.

Normal mode (GUI):
    python gui.py
    → Launches the customtkinter GUI window

Engine mode (called by the GUI's subprocess runner when bundled as .exe):
    python gui.py --run-engine
    → Runs the download engine (main.py logic) directly

This dual-mode pattern lets PyInstaller bundle everything into a single
.exe that serves both roles without needing a separate python.exe or
loose main.py file on disk.
"""

import sys
from pathlib import Path

# Ensure the project root is on sys.path so all imports work
# both when run directly and when bundled with PyInstaller.
# When frozen, sys.executable dir IS the project root.
if getattr(sys, 'frozen', False):
    ROOT = Path(sys.executable).parent
else:
    ROOT = Path(__file__).parent

if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def run_gui():
    from gui.app import App
    app = App()
    app.mainloop()


def run_engine():
    """Execute the download engine (equivalent to running main.py)."""
    from main import main as engine_main
    engine_main()


if __name__ == "__main__":
    if "--run-engine" in sys.argv:
        run_engine()
    else:
        run_gui()
