import time
import concurrent.futures
from typing import List

from core.models import LinkEntry, DownloadResult
from core.parser import mark_done
from backends.ytdlp_backend import YtDlpBackend
from utils.config import config
from utils.logger import logger
from utils.helpers import format_duration, format_filesize


class DownloadEngine:
    """
    Orchestrates the download pipeline.
    
    Responsibilities:
    - Iterates through parsed links
    - Delegates to backend for actual downloads
    - Tracks results and marks completed links
    - Prints a final summary report
    """

    def __init__(self, backend: YtDlpBackend):
        self.backend = backend
        self.results: List[DownloadResult] = []

    def run(self, links: List[LinkEntry]):
        """Execute the download pipeline for all links."""
        if not links:
            logger.warning("No links to process.")
            return

        total = len(links)
        logger.info(f"Starting download of {total} link(s)")
        logger.info(f"Download directory: {config.downloads_dir}")
        logger.info(f"Resolution: {'HIGHEST' if config.highest_res else '1080p cap'}")
        if config.audio_only:
            logger.info("Mode: Audio-only (MP3 320kbps)")
        if config.dry_run:
            logger.info("DRY RUN — no files will be downloaded")

        if config.dry_run:
            self._dry_run(links)
            return

        pipeline_start = time.time()

        # ── Execute downloads ──
        workers = config.max_downloads
        if workers > 1:
            logger.info(f"Using {workers} parallel download workers")
            self._run_parallel(links, workers)
        else:
            self._run_sequential(links)

        pipeline_elapsed = time.time() - pipeline_start

        # ── Print summary ──
        self._print_summary(pipeline_elapsed)

    def _run_sequential(self, links: List[LinkEntry]):
        """Download links one by one."""
        for i, link in enumerate(links, start=1):
            logger.info(f"━━━ [{i}/{len(links)}] ━━━")
            result = self.backend.download(
                link.url, 
                line_number=link.line_number, 
                custom_name=link.custom_name
            )
            self.results.append(result)

            # Mark as done in links.txt if successful
            if result.success and not result.skipped:
                mark_done(config.links_file, link.line_number, link.url)
            elif result.skipped:
                # Also mark skipped (already downloaded) as done
                mark_done(config.links_file, link.line_number, link.url)

    def _run_parallel(self, links: List[LinkEntry], workers: int):
        """Download links using a thread pool."""
        def _worker(i_link):
            i, link = i_link
            logger.info(f"━━━ [{i}/{len(links)}] ━━━")
            result = self.backend.download(
                link.url, 
                line_number=link.line_number, 
                custom_name=link.custom_name
            )
            self.results.append(result)

            if result.success:
                mark_done(config.links_file, link.line_number, link.url)

            return result

        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
            list(executor.map(_worker, enumerate(links, start=1)))

    def _dry_run(self, links: List[LinkEntry]):
        """Show what would be downloaded without actually downloading."""
        logger.info("═" * 50)
        logger.info("DRY RUN — Links that would be downloaded:")
        logger.info("═" * 50)
        for i, link in enumerate(links, start=1):
            logger.info(f"  {i}. [Line {link.line_number}] {link.url}")
        logger.info("═" * 50)
        logger.info(f"Total: {len(links)} link(s)")

    def _print_summary(self, elapsed: float):
        """Print a final summary of all download results."""
        succeeded = [r for r in self.results if r.success and not r.skipped]
        failed = [r for r in self.results if not r.success]
        skipped = [r for r in self.results if r.skipped]

        total_size = sum(r.file_size_bytes for r in self.results if r.file_size_bytes)

        logger.info("")
        logger.info("═" * 55)
        logger.info("  DOWNLOAD SUMMARY")
        logger.info("═" * 55)
        logger.info(f"  ✅ {len(succeeded)} succeeded")
        logger.info(f"  ❌ {len(failed)} failed")
        logger.info(f"  ⏭️  {len(skipped)} skipped (already downloaded)")
        logger.info(f"  📦 Total size: {format_filesize(total_size)}")
        logger.info(f"  ⏱️  Total time: {format_duration(elapsed)}")

        if failed:
            logger.info("─" * 55)
            logger.info("  Failed downloads:")
            for r in failed:
                display = r.title or r.url
                if len(display) > 45:
                    display = display[:42] + "..."
                logger.error(f"    Line {r.line_number}: {display}")
                logger.error(f"      → {r.error}")

        logger.info("═" * 55)
