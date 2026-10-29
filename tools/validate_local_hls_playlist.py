#!/usr/bin/env python3
"""Validate a downloaded local HLS playlist and its referenced files.

Usage:
    python3 tools/validate_local_hls_playlist.py /path/to/download.m3u8

The validator resolves every relative URI from the playlist directory, checks
that each referenced file exists and is non-empty, and verifies EXTINF/segment
counts. It is intentionally dependency-free so it can run without an Android
SDK or Gradle.
"""

from __future__ import annotations

import pathlib
import sys


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__.strip())
        return 2

    playlist_path = pathlib.Path(sys.argv[1]).expanduser().resolve()
    if not playlist_path.is_file():
        fail(f"playlist does not exist: {playlist_path}")

    text = playlist_path.read_text(encoding="utf-8")
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if not lines or lines[0] != "#EXTM3U":
        fail("playlist does not start with #EXTM3U")
    if "#EXT-X-ENDLIST" not in lines:
        fail("playlist is not marked as a completed VOD playlist")

    expected_segments = sum(line.startswith("#EXTINF:") for line in lines)
    referenced_files: list[pathlib.Path] = []
    for line in lines:
        if line.startswith("#"):
            continue
        path = (playlist_path.parent / line).resolve()
        if playlist_path.parent not in path.parents:
            fail(f"playlist reference escapes download directory: {line}")
        if not path.is_file():
            fail(f"referenced file is missing: {path}")
        if path.stat().st_size == 0:
            fail(f"referenced file is empty: {path}")
        referenced_files.append(path)

    if len(referenced_files) != expected_segments:
        fail(f"EXTINF count={expected_segments}, segment URI count={len(referenced_files)}")

    print(
        f"PASS: {playlist_path} | segments={expected_segments} | "
        f"bytes={sum(path.stat().st_size for path in referenced_files)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
