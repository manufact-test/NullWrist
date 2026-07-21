#!/usr/bin/env python3
"""Render settled QEMU framebuffer previews for every preinstalled Basalt watchface."""
from __future__ import annotations

import argparse
import binascii
import json
import os
import shutil
import socket
import struct
import subprocess
import tempfile
import time
import uuid
import zlib
from pathlib import Path

from preseed_basalt_flash import (
    ENDPOINT_APP_RUN_STATE,
    ProtocolLink,
    unused_port,
    wait_for_firmware,
)

FB_MAGIC = 0x50424642
FB_VERSION = 1
FB_FORMAT_COLOR_2BIT = 1
FB_HEADER_BYTES = 64
FB_WIDTH = 144
FB_HEIGHT = 168
PREVIEW_MIN_SETTLE_SECONDS = 5.0
PREVIEW_QUIET_SECONDS = 0.65


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    body = kind + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", binascii.crc32(body) & 0xFFFFFFFF)


def write_png(path: Path, width: int, height: int, raw_pixels: bytes) -> None:
    rows = bytearray()
    for y in range(height):
        rows.append(0)  # PNG filter: None
        offset = y * width
        for value in raw_pixels[offset:offset + width]:
            rows.extend((
                ((value >> 6) & 0x03) * 85,
                ((value >> 4) & 0x03) * 85,
                ((value >> 2) & 0x03) * 85,
            ))
    data = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(bytes(rows), level=9))
        + png_chunk(b"IEND", b"")
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def read_frame(path: Path) -> tuple[int, int, int, bytes] | None:
    try:
        data = path.read_bytes()
    except FileNotFoundError:
        return None
    if len(data) < FB_HEADER_BYTES + FB_WIDTH * FB_HEIGHT:
        return None
    magic, version, width, height, stride, pixel_format, sequence = struct.unpack_from("<7I", data, 0)
    if (
        magic != FB_MAGIC
        or version != FB_VERSION
        or width != FB_WIDTH
        or height != FB_HEIGHT
        or stride != FB_WIDTH
        or pixel_format != FB_FORMAT_COLOR_2BIT
        or sequence <= 0
    ):
        return None
    pixels = data[FB_HEADER_BYTES:FB_HEADER_BYTES + width * height]
    return sequence, width, height, pixels


def wait_for_preview(path: Path, previous_sequence: int, timeout: float = 12.0) -> tuple[int, int, bytes]:
    deadline = time.monotonic() + timeout
    first_changed_at: float | None = None
    last_changed_at: float | None = None
    latest_sequence = previous_sequence
    latest: tuple[int, int, int, bytes] | None = None

    while time.monotonic() < deadline:
        now = time.monotonic()
        frame = read_frame(path)
        if frame is not None and frame[0] > previous_sequence:
            if first_changed_at is None:
                first_changed_at = now
                last_changed_at = now
            if frame[0] != latest_sequence:
                latest_sequence = frame[0]
                latest = frame
                last_changed_at = now
            if (
                latest is not None
                and first_changed_at is not None
                and last_changed_at is not None
                and now - first_changed_at >= PREVIEW_MIN_SETTLE_SECONDS
                and now - last_changed_at >= PREVIEW_QUIET_SECONDS
            ):
                return latest[1], latest[2], latest[3]
        time.sleep(0.05)

    if latest is None:
        raise RuntimeError("Watchface did not produce a new framebuffer frame")
    return latest[1], latest[2], latest[3]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--qemu", type=Path, required=True)
    parser.add_argument("--micro", type=Path, required=True)
    parser.add_argument("--spi", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    for required in (args.qemu, args.micro, args.spi, args.manifest):
        if not required.exists():
            raise RuntimeError(f"Required input does not exist: {required}")

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    watchfaces = manifest.get("watchfaces", [])
    if not watchfaces:
        raise RuntimeError("Preseed manifest contains no watchfaces")

    args.output.mkdir(parents=True, exist_ok=True)
    for old in args.output.glob("*.png"):
        old.unlink()
    args.log.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="pebblehertz-thumbnails-") as temporary:
        temporary_dir = Path(temporary)
        working_spi = temporary_dir / "qemu_spi_flash.bin"
        framebuffer = temporary_dir / "framebuffer.bin"
        shutil.copyfile(args.spi, working_spi)

        protocol_port = unused_port()
        console_port = unused_port()
        command = [
            str(args.qemu),
            "-rtc", "base=localtime",
            "-serial", "null",
            "-serial", f"tcp::{protocol_port},server=on,wait=off,nodelay=on",
            "-serial", f"tcp::{console_port},server=on,wait=on,nodelay=on",
            "-kernel", str(args.micro),
            "-monitor", "none",
            "-display", "none",
            "-machine", "pebble-snowy-bb",
            "-cpu", "cortex-m4",
            "-drive", f"if=none,id=spi-flash,file={working_spi},format=raw",
            "-no-reboot",
        ]
        environment = os.environ.copy()
        environment["PEBBLE_FB_PATH"] = str(framebuffer)

        with args.log.open("wb") as log:
            process = subprocess.Popen(
                command,
                stdout=log,
                stderr=subprocess.STDOUT,
                env=environment,
            )
            link: ProtocolLink | None = None
            try:
                wait_for_firmware(console_port, process)
                link = ProtocolLink("127.0.0.1", protocol_port)
                previous_sequence = 0
                initial = read_frame(framebuffer)
                if initial is not None:
                    previous_sequence = initial[0]

                for item in watchfaces:
                    app_uuid = uuid.UUID(item["uuid"])
                    name = item["name"]
                    print(f"Rendering {name} ({app_uuid})")
                    link.send_pebble(ENDPOINT_APP_RUN_STATE, b"\x01" + app_uuid.bytes)
                    width, height, pixels = wait_for_preview(framebuffer, previous_sequence)
                    current = read_frame(framebuffer)
                    if current is not None:
                        previous_sequence = current[0]
                    output = args.output / f"{str(app_uuid).lower()}.png"
                    write_png(output, width, height, pixels)
                    print(f"  wrote {output} ({output.stat().st_size} bytes)")
            finally:
                if link is not None:
                    link.close()
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

    generated = sorted(args.output.glob("*.png"))
    if len(generated) != len(watchfaces):
        raise RuntimeError(f"Generated {len(generated)} thumbnails for {len(watchfaces)} watchfaces")
    print(f"Generated {len(generated)} real Pebble Time thumbnails")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001
        print(f"error: {error}", file=os.sys.stderr)
        raise SystemExit(1)
