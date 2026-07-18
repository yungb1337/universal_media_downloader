import logging
import datetime
from .config import config


def setup_logger() -> logging.Logger:
    """
    Terminal-first logger for the testing phase.
    - Console: color-coded, concise timestamps
    - File: full debug output in logs/ directory
    """
    log = logging.getLogger("universal_downloader")

    if config.verbose:
        log.setLevel(logging.DEBUG)
    else:
        log.setLevel(logging.INFO)

    # Prevent duplicate handlers on re-import
    if log.handlers:
        return log

    # ── Console handler (primary during testing) ──
    console = logging.StreamHandler()
    console.setLevel(logging.DEBUG if config.verbose else logging.INFO)

    # Color-coded format using ANSI escape codes
    class ColorFormatter(logging.Formatter):
        COLORS = {
            logging.DEBUG:    "\033[36m",     # Cyan
            logging.INFO:     "\033[32m",     # Green
            logging.WARNING:  "\033[33m",     # Yellow
            logging.ERROR:    "\033[31m",     # Red
            logging.CRITICAL: "\033[1;31m",   # Bold Red
        }
        RESET = "\033[0m"

        def format(self, record):
            color = self.COLORS.get(record.levelno, self.RESET)
            timestamp = datetime.datetime.now().strftime("%H:%M:%S")
            level = record.levelname.ljust(8)
            msg = record.getMessage()
            return f"{color}[{timestamp}] [{level}]{self.RESET} {msg}"

    console.setFormatter(ColorFormatter())
    log.addHandler(console)

    # ── File handler (full debug for post-mortem) ──
    try:
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        log_file = config.logs_dir / f"download_{timestamp}.log"

        file_handler = logging.FileHandler(log_file, encoding="utf-8")
        file_handler.setLevel(logging.DEBUG)
        file_formatter = logging.Formatter(
            "%(asctime)s [%(levelname)-8s] %(name)s: %(message)s"
        )
        file_handler.setFormatter(file_formatter)
        log.addHandler(file_handler)
    except Exception as e:
        log.warning(f"Could not create log file: {e}")

    return log


# Singleton
logger = setup_logger()
