"""
runner.py — Subprocess wrapper for main.py with live log streaming.

Runs the download engine in a daemon thread and pushes each log line
to a thread-safe queue, which the main Tk thread drains via after().
"""

import queue
import subprocess
import sys
import threading
from pathlib import Path

from utils.helpers import find_base_dir


BASE_DIR = find_base_dir()


class DownloadRunner:
    """
    Manages a single main.py subprocess.

    Usage:
        runner = DownloadRunner(log_queue)
        runner.start()   # non-blocking
        runner.stop()    # terminates subprocess
    """

    def __init__(self, log_queue: queue.Queue):
        self._queue = log_queue
        self._proc: subprocess.Popen | None = None
        self._thread: threading.Thread | None = None
        self.running = False

    def start(self):
        """Spawn main.py and begin streaming output."""
        if self.running:
            return

        self.running = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        """Terminate the subprocess and mark as stopped."""
        self.running = False
        if self._proc and self._proc.poll() is None:
            self._proc.terminate()
            try:
                self._proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._proc.kill()
        self._put_line("⏹  Download stopped by user.", tag="warn")

    def _run(self):
        """Worker thread: spawn process, stream lines."""
        if getattr(sys, 'frozen', False):
            # Running as PyInstaller bundle — call ourselves with --run-engine flag
            cmd = [sys.executable, "--run-engine"]
        else:
            # Normal Python — call main.py directly
            cmd = [sys.executable, str(BASE_DIR / "main.py")]
        try:
            self._proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,   # merge stderr into stdout
                text=True,
                bufsize=1,                  # line-buffered
                encoding="utf-8",
                errors="replace",
                cwd=str(BASE_DIR),
            )

            # Stream line by line
            for line in self._proc.stdout:
                line = line.rstrip()
                if not line:
                    continue
                tag = self._classify(line)
                self._put_line(line, tag=tag)

            self._proc.wait()
            rc = self._proc.returncode

            if rc == 0:
                self._put_line("✅ Download session complete.", tag="success")
            elif rc == 130:
                self._put_line("⚠️  Session interrupted (Ctrl+C).", tag="warn")
            else:
                self._put_line(f"⚠️  Process exited with code {rc}.", tag="warn")

        except Exception as e:
            self._put_line(f"❌ Runner error: {e}", tag="error")
        finally:
            self.running = False

    def _put_line(self, line: str, tag: str = "info"):
        """Push a tagged log line to the queue."""
        self._queue.put((line, tag))

    @staticmethod
    def _classify(line: str) -> str:
        """Map a log line to a color tag based on content."""
        upper = line.upper()
        if "[ERROR" in upper or "❌" in upper:
            return "error"
        if "[WARNING" in upper or "⚠" in upper:
            return "warn"
        if "✅" in upper or "succeeded" in upper.lower():
            return "success"
        if "⬇" in upper or "downloading" in upper.lower():
            return "download"
        return "info"
