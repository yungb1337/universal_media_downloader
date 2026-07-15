from pathlib import Path
from typing import List

from core.models import LinkEntry
from utils.logger import logger


def parse_links(links_file: Path) -> List[LinkEntry]:
    """
    Parses a links file and returns a list of LinkEntry objects.
    
    Rules:
    - One URL per line
    - Lines starting with # are comments (ignored)
    - Blank lines are ignored
    - Lines starting with [DONE] are already downloaded (skipped)
    - Validates that URLs start with http:// or https://
    """
    if not links_file.exists():
        logger.error(f"Links file not found: {links_file}")
        return []

    entries: List[LinkEntry] = []
    skipped_done = 0

    try:
        with open(links_file, "r", encoding="utf-8") as f:
            lines = f.readlines()
    except Exception as e:
        logger.error(f"Failed to read links file: {e}")
        return []

    for line_num, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()

        # Skip empty lines and comments
        if not line or line.startswith("#"):
            continue

        # Skip already-downloaded entries
        if line.startswith("[DONE]"):
            skipped_done += 1
            continue

        # Validate URL scheme
        parts = line.split(maxsplit=1)
        url = parts[0]
        custom_name = parts[1].strip() if len(parts) > 1 else None

        if not url.startswith("http://") and not url.startswith("https://"):
            logger.warning(
                f"Line {line_num}: Skipping invalid URL (no http/https scheme): {url}"
            )
            continue

        entries.append(LinkEntry(url=url, line_number=line_num, custom_name=custom_name))

    logger.info(
        f"Parsed {len(entries)} new link(s) from {links_file.name} "
        f"({skipped_done} already done)"
    )

    return entries


def mark_done(links_file: Path, line_number: int, url: str):
    """
    Marks a specific line in links.txt as [DONE] by prepending [DONE]-
    to the URL at the given line number.
    """
    try:
        with open(links_file, "r", encoding="utf-8") as f:
            lines = f.readlines()

        # line_number is 1-indexed
        idx = line_number - 1
        if 0 <= idx < len(lines):
            original = lines[idx].strip()
            # Only mark if not already marked
            if not original.startswith("[DONE]"):
                lines[idx] = f"[DONE]-{original}\n"

        with open(links_file, "w", encoding="utf-8") as f:
            f.writelines(lines)

    except Exception as e:
        logger.error(f"Failed to mark line {line_number} as done: {e}")
