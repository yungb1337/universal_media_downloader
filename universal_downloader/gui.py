"""
gui.py — Entry point for the Universal Downloader GUI.

Run with:
    python gui.py
"""

import sys
from pathlib import Path

# Ensure the project root is on sys.path so imports work
# both when run directly and when bundled with PyInstaller
ROOT = Path(__file__).parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from gui.app import App


def main():
    app = App()
    app.mainloop()


if __name__ == "__main__":
    main()
