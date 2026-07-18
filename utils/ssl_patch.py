"""
SSL compatibility patch — minimal safe version.

Only patches Python's default HTTPS context factory.
Does NOT touch ssl.SSLContext directly (it's a C extension type
and patching it breaks yt-dlp's urllib handler).

For sites with truly broken SSL, we rely on curl_cffi impersonation
configured in the yt-dlp backend instead.
"""

import ssl


def patch_ssl():
    """Apply a minimal SSL patch — safe for C extension types."""
    ssl._create_default_https_context = ssl._create_unverified_context
