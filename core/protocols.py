"""
protocols.py — Abstract interfaces for pluggable backends.

Using typing.Protocol (structural subtyping) so backends don't need
to explicitly inherit — they just need to implement the right method
signatures.  This keeps the core layer fully decoupled from any
concrete third-party library.
"""

from typing import Protocol

from core.models import DownloadResult


class DownloadBackend(Protocol):
    """
    Any download backend must implement this interface.

    The DownloadEngine depends on this protocol, NOT on a concrete
    class like YtDlpBackend.  This means:
      - Unit tests can inject a mock backend
      - New backends (gallery-dl, aria2, etc.) are first-class citizens
      - The core/ package has zero knowledge of yt-dlp
    """

    def download(
        self,
        url: str,
        line_number: int = 0,
        custom_name: str | None = None,
    ) -> list[DownloadResult]:
        """
        Download a URL and return one or more DownloadResults.

        Most URLs produce a single result.  Playlist URLs produce
        one result per entry — the engine handles this transparently.

        Args:
            url:          The URL to download.
            line_number:  Original line number in links.txt (for reporting).
            custom_name:  Optional override for the output filename.

        Returns:
            A list of DownloadResult(s) capturing success/failure, path,
            timing, etc. for each individual item downloaded.
        """
        ...
