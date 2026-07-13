
import sys
from pathlib import Path
from utils.config import config
from utils.logger import logger
from core.parser import parse_links
from core.engine import DownloadEngine
from backends.ytdlp_backend import YtDlpBackend


def main():
    logger.info("═" * 55)
    logger.info("  universal_downloader — Universal Link Downloader")
    logger.info("═" * 55)

    # ── Validate config ──
    if not config.links_file.exists():
        logger.error(f"Links file not found: {config.links_file}")
        logger.error("Create it and add URLs (one per line), then try again.")
        sys.exit(1)

    if config.cookies_file and not Path(config.cookies_file).exists():
        logger.warning(f"Cookies file not found: {config.cookies_file}")
        logger.warning("Continuing without cookies — some sites may block downloads.")

    # ── Parse links ──
    links = parse_links(config.links_file)

    if not links:
        logger.info("No new links to download. Exiting.")
        sys.exit(0)

    # ── Run pipeline ──
    backend = YtDlpBackend()
    engine = DownloadEngine(backend=backend)

    try:
        engine.run(links)
    except KeyboardInterrupt:
        logger.warning("\n⚠️  Interrupted by user (Ctrl+C)")
        logger.info("Printing partial results...")
        engine._print_summary(0)
        sys.exit(130)


if __name__ == "__main__":
    main()
