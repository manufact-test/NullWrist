#!/usr/bin/env python3
"""Fetch the official Pebble SDK Basalt QEMU images for Android packaging."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "pebble" / "basalt"
SDK_VERSION = os.environ.get("PEBBLE_SDK_VERSION", "latest")
API_URL = f"https://sdk.repebble.com/v1/files/sdk-core/{SDK_VERSION}?channel="
MAX_ARCHIVE_BYTES = 200 * 1024 * 1024
MAX_IMAGE_BYTES = 16 * 1024 * 1024

FILES = {
    "sdk-core/pebble/basalt/qemu/qemu_micro_flash.bin": "qemu_micro_flash.bin",
    "sdk-core/pebble/basalt/qemu/qemu_spi_flash.bin": "qemu_spi_flash.bin",
}


def download(url: str, limit: int) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "pebble-rear-display-build/1"})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read(limit + 1)
    if len(data) > limit:
        raise RuntimeError(f"Download exceeded {limit} bytes: {url}")
    return data


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    print(f"Resolving Pebble SDK {SDK_VERSION} from {API_URL}")
    sdk_info = json.loads(download(API_URL, 2 * 1024 * 1024).decode("utf-8"))
    resolved_version = sdk_info.get("version")
    archive_url = sdk_info.get("url")
    if not resolved_version or not archive_url:
        raise RuntimeError(f"Unexpected SDK response: {sdk_info}")
    if SDK_VERSION != "latest" and resolved_version != SDK_VERSION:
        raise RuntimeError(f"Requested {SDK_VERSION}, server resolved {resolved_version}")
    print(f"Resolved SDK version: {resolved_version}")

    print(f"Downloading SDK core: {archive_url}")
    archive = download(archive_url, MAX_ARCHIVE_BYTES)
    print(f"SDK archive: {len(archive)} bytes, sha256={sha256(archive)}")

    extracted: dict[str, dict[str, object]] = {}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:*") as bundle:
        members = {member.name: member for member in bundle.getmembers()}
        for source_name, output_name in FILES.items():
            member = members.get(source_name)
            if member is None or not member.isfile():
                raise RuntimeError(f"SDK archive is missing {source_name}")
            if member.size <= 0 or member.size > MAX_IMAGE_BYTES:
                raise RuntimeError(f"Unexpected size for {source_name}: {member.size}")
            source = bundle.extractfile(member)
            if source is None:
                raise RuntimeError(f"Could not extract {source_name}")
            data = source.read(MAX_IMAGE_BYTES + 1)
            if len(data) != member.size:
                raise RuntimeError(f"Short read for {source_name}: {len(data)} != {member.size}")

            destination = OUTPUT / output_name
            destination.write_bytes(data)
            digest = sha256(data)
            extracted[output_name] = {"size": len(data), "sha256": digest}
            print(f"Wrote {destination.relative_to(ROOT)} ({len(data)} bytes, sha256={digest})")

    metadata = {
        "requested_sdk_version": SDK_VERSION,
        "sdk_version": resolved_version,
        "sdk_api": API_URL,
        "sdk_archive_url": archive_url,
        "sdk_archive_sha256": sha256(archive),
        "files": extracted,
    }
    metadata_path = OUTPUT / "firmware.json"
    metadata_path.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote {metadata_path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
