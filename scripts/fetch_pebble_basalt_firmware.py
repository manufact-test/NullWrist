#!/usr/bin/env python3
"""Fetch the official Pebble SDK Basalt QEMU images for Android packaging."""
from __future__ import annotations

import bz2
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "pebble" / "basalt"
BUILD_CACHE = ROOT / ".pebble-firmware-cache" / "basalt"
SDK_VERSION = os.environ.get("PEBBLE_SDK_VERSION", "4.17")
API_URL = f"https://sdk.repebble.com/v1/files/sdk-core/{SDK_VERSION}?channel="
MAX_ARCHIVE_BYTES = 200 * 1024 * 1024
MAX_MEMBER_BYTES = 16 * 1024 * 1024
MAX_IMAGE_BYTES = 32 * 1024 * 1024

FILES = {
    "sdk-core/pebble/basalt/qemu/qemu_micro_flash.bin": (OUTPUT / "qemu_micro_flash.bin", False),
    "sdk-core/pebble/basalt/qemu/qemu_spi_flash.bin.bz2": (BUILD_CACHE / "qemu_spi_flash_base.bin", True),
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
    BUILD_CACHE.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:*") as bundle:
        members = {member.name: member for member in bundle.getmembers()}
        for source_name, (destination, compressed) in FILES.items():
            member = members.get(source_name)
            if member is None or not member.isfile():
                raise RuntimeError(f"SDK archive is missing {source_name}")
            if member.size <= 0 or member.size > MAX_MEMBER_BYTES:
                raise RuntimeError(f"Unexpected size for {source_name}: {member.size}")
            source = bundle.extractfile(member)
            if source is None:
                raise RuntimeError(f"Could not extract {source_name}")
            packed = source.read(MAX_MEMBER_BYTES + 1)
            if len(packed) != member.size:
                raise RuntimeError(f"Short read for {source_name}: {len(packed)} != {member.size}")

            data = bz2.decompress(packed) if compressed else packed
            if len(data) <= 0 or len(data) > MAX_IMAGE_BYTES:
                raise RuntimeError(f"Unexpected unpacked size for {source_name}: {len(data)}")

            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            digest = sha256(data)
            extracted[destination.name] = {
                "size": len(data),
                "sha256": digest,
                "sdk_member": source_name,
                "compressed_in_sdk": compressed,
                "packaged": destination.is_relative_to(OUTPUT),
            }
            print(f"Wrote {destination.relative_to(ROOT)} ({len(data)} bytes, sha256={digest})")

    base_spi = BUILD_CACHE / "qemu_spi_flash_base.bin"
    runtime_spi = OUTPUT / "qemu_spi_flash.bin"
    preseed_manifest = OUTPUT / "preseeded-watchfaces.json"
    if not runtime_spi.is_file():
        shutil.copyfile(base_spi, runtime_spi)
        print("Created unseeded runtime SPI fallback; preseed workflow can replace it")
    elif preseed_manifest.is_file():
        seed_info = json.loads(preseed_manifest.read_text(encoding="utf-8"))
        expected_base = seed_info.get("base_spi_sha256")
        actual_base = sha256(base_spi.read_bytes())
        if expected_base != actual_base:
            raise RuntimeError(
                "Preseeded SPI was built from a different base firmware; rerun preseed workflow"
            )
        print("Preserved verified preseeded runtime SPI image")
    else:
        print("Preserved existing runtime SPI image without preseed metadata")

    extracted["qemu_spi_flash.bin"] = {
        "size": runtime_spi.stat().st_size,
        "sha256": sha256(runtime_spi.read_bytes()),
        "preseeded": preseed_manifest.is_file(),
        "packaged": True,
    }

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
