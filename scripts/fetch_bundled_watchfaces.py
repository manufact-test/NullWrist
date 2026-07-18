#!/usr/bin/env python3
"""Download and verify the PBW files bundled with the Android application."""

from __future__ import annotations

import hashlib
import json
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "bundled-watchfaces.json"
OUTPUT_DIRECTORY = ROOT / "app" / "src" / "main" / "assets" / "watchfaces"
MAX_BYTES = 20 * 1024 * 1024


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_pbw(path: Path, expected_name: str) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            appinfo = json.loads(archive.read("appinfo.json"))
    except (KeyError, json.JSONDecodeError, zipfile.BadZipFile) as error:
        raise RuntimeError(f"{expected_name}: downloaded file is not a valid PBW") from error

    if not appinfo.get("watchapp", {}).get("watchface", False):
        raise RuntimeError(f"{expected_name}: PBW is not marked as a watchface")


def download(entry: dict[str, str]) -> None:
    destination = OUTPUT_DIRECTORY / entry["file"]
    expected_hash = entry["sha256"].lower()

    if destination.exists() and sha256(destination) == expected_hash:
        print(f"Using cached {entry['name']} {entry['version']}")
        return

    temporary = destination.with_suffix(destination.suffix + ".part")
    temporary.unlink(missing_ok=True)

    request = urllib.request.Request(
        entry["download_url"],
        headers={"User-Agent": "PebbleRearDisplay/0.1 (+GitHub Actions)"},
    )

    print(f"Downloading {entry['name']} {entry['version']}")
    total = 0
    try:
        with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
            while True:
                chunk = response.read(64 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > MAX_BYTES:
                    raise RuntimeError(f"{entry['name']}: PBW exceeds {MAX_BYTES} bytes")
                output.write(chunk)

        actual_hash = sha256(temporary)
        if actual_hash != expected_hash:
            raise RuntimeError(
                f"{entry['name']}: SHA-256 mismatch; expected {expected_hash}, got {actual_hash}"
            )

        validate_pbw(temporary, entry["name"])
        shutil.move(str(temporary), str(destination))
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    entries = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)

    for entry in entries:
        download(entry)

    print(f"Bundled {len(entries)} watchfaces in {OUTPUT_DIRECTORY.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - command-line entrypoint reports a concise failure.
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
