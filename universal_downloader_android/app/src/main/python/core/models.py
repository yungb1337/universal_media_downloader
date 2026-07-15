from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class LinkEntry:
    """A single URL parsed from the links file."""
    url: str
    line_number: int  # Original line number in links.txt (for error reporting)
    custom_name: str | None = None


@dataclass
class DownloadResult:
    """Result of a single download attempt."""
    url: str
    success: bool
    file_path: Path | None = None
    title: str | None = None
    error: str | None = None
    duration_seconds: float = 0.0
    file_size_bytes: int = 0
    skipped: bool = False       # True if already downloaded
    line_number: int = 0        # Line number from links.txt
