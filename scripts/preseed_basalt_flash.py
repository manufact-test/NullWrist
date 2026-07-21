#!/usr/bin/env python3
"""Boot Pebble Time QEMU and install every bundled PBW into a persistent SPI image."""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import shutil
import socket
import struct
import subprocess
import sys
import time
import uuid
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

QEMU_HEADER = 0xFEED
QEMU_FOOTER = 0xBEEF
QEMU_PROTOCOL_SPP = 1
QEMU_PROTOCOL_BLUETOOTH = 3
MAX_QEMU_PAYLOAD = 2048

ENDPOINT_WATCH_VERSION = 0x0010
ENDPOINT_PHONE_APP_VERSION = 0x0011
ENDPOINT_APP_RUN_STATE = 0x0034
ENDPOINT_APP_FETCH = 0x1771
ENDPOINT_BLOB_DB = 0xB1DB
ENDPOINT_PUT_BYTES = 0xBEEF

BLOB_DATABASE_APP = 2
BLOB_STATUS_SUCCESS = 1
BLOB_STATUS_TRY_LATER = 0x0B
PUT_BYTES_ACK = 1
PART_RESOURCES = 4
PART_BINARY = 5
PART_WORKER = 7
APP_INSTALL_FLAG = 0x80
TRANSFER_CHUNK_BYTES = 1000


@dataclass(frozen=True)
class AppHeader:
    sdk_major: int
    sdk_minor: int
    app_major: int
    app_minor: int
    icon_resource_id: int
    flags: int
    app_name: str
    app_uuid: uuid.UUID


@dataclass(frozen=True)
class PbwBundle:
    path: Path
    header: AppHeader
    application: bytes
    resources: bytes | None
    worker: bytes | None
    sha256: str


class ProtocolLink:
    def __init__(self, host: str, port: int, timeout: float = 20.0) -> None:
        deadline = time.monotonic() + timeout
        last_error: OSError | None = None
        self.socket: socket.socket | None = None
        while time.monotonic() < deadline:
            candidate = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            candidate.settimeout(0.8)
            try:
                candidate.connect((host, port))
                candidate.settimeout(0.5)
                candidate.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                self.socket = candidate
                break
            except OSError as error:
                last_error = error
                candidate.close()
                time.sleep(0.12)
        if self.socket is None:
            raise RuntimeError("Could not connect to Pebble QEMU protocol port") from last_error

        self.qemu_bytes = bytearray()
        self.pebble_bytes = bytearray()
        self.endpoint_queues: dict[int, collections.deque[bytes]] = collections.defaultdict(collections.deque)
        self.initialise(timeout)

    def close(self) -> None:
        if self.socket is not None:
            self.socket.close()
            self.socket = None

    def send_qemu(self, protocol: int, payload: bytes) -> None:
        assert self.socket is not None
        frame = struct.pack(">HHH", QEMU_HEADER, protocol, len(payload)) + payload + struct.pack(">H", QEMU_FOOTER)
        self.socket.sendall(frame)

    def send_pebble(self, endpoint: int, payload: bytes) -> None:
        message = struct.pack(">HH", len(payload), endpoint) + payload
        for offset in range(0, len(message), MAX_QEMU_PAYLOAD):
            self.send_qemu(QEMU_PROTOCOL_SPP, message[offset:offset + MAX_QEMU_PAYLOAD])

    def await_endpoint(
        self,
        endpoint: int,
        predicate: Callable[[bytes], bool] | None,
        timeout: float,
    ) -> bytes:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            queue = self.endpoint_queues[endpoint]
            while queue:
                payload = queue.popleft()
                if predicate is None or predicate(payload):
                    return payload
            self.read_once()
        raise TimeoutError(f"Timed out waiting for Pebble endpoint 0x{endpoint:04X}")

    def initialise(self, timeout: float) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            self.send_qemu(QEMU_PROTOCOL_BLUETOOTH, b"\x01")
            time.sleep(0.18)
            self.send_pebble(ENDPOINT_WATCH_VERSION, b"\x00")
            try:
                self.await_endpoint(
                    ENDPOINT_WATCH_VERSION,
                    lambda payload: bool(payload) and payload[0] == 1,
                    min(2.0, max(0.5, deadline - time.monotonic())),
                )
                return
            except TimeoutError:
                time.sleep(0.18)
        raise RuntimeError("Pebble phone protocol did not initialise")

    def read_once(self) -> None:
        assert self.socket is not None
        try:
            chunk = self.socket.recv(4096)
        except socket.timeout:
            return
        if not chunk:
            raise RuntimeError("Pebble QEMU protocol socket closed")
        self.qemu_bytes.extend(chunk)
        self.parse_qemu_frames()

    def parse_qemu_frames(self) -> None:
        while len(self.qemu_bytes) >= 8:
            header, protocol, length = struct.unpack_from(">HHH", self.qemu_bytes, 0)
            if header != QEMU_HEADER:
                del self.qemu_bytes[0]
                continue
            total = length + 8
            if len(self.qemu_bytes) < total:
                return
            footer = struct.unpack_from(">H", self.qemu_bytes, 6 + length)[0]
            if footer != QEMU_FOOTER:
                del self.qemu_bytes[0]
                continue
            payload = bytes(self.qemu_bytes[6:6 + length])
            del self.qemu_bytes[:total]
            if protocol == QEMU_PROTOCOL_SPP:
                self.pebble_bytes.extend(payload)
                self.parse_pebble_frames()

    def parse_pebble_frames(self) -> None:
        while len(self.pebble_bytes) >= 4:
            length, endpoint = struct.unpack_from(">HH", self.pebble_bytes, 0)
            total = length + 4
            if len(self.pebble_bytes) < total:
                return
            payload = bytes(self.pebble_bytes[4:total])
            del self.pebble_bytes[:total]
            if endpoint == ENDPOINT_PHONE_APP_VERSION and payload and payload[0] == 0:
                response = (
                    b"\x01"
                    + struct.pack(">III", 0xFFFFFFFF, 0x80000000, 50)
                    + bytes((2, 3, 0, 0))
                    + struct.pack(">Q", 0xFFFFFFFFFFFFFFFF)
                )
                self.send_pebble(ENDPOINT_PHONE_APP_VERSION, response)
            self.endpoint_queues[endpoint].append(payload)


def decode_fixed(value: bytes) -> str:
    return value.split(b"\x00", 1)[0].decode("utf-8", errors="replace")


def read_part(archive: zipfile.ZipFile, prefix: str, info: dict | None) -> bytes | None:
    if not info:
        return None
    name = str(info.get("name", "")).strip()
    if not name:
        raise RuntimeError("PBW manifest part has no filename")
    return archive.read(prefix + name)


def load_pbw(path: Path) -> PbwBundle:
    with zipfile.ZipFile(path) as archive:
        manifest_path = "basalt/manifest.json" if "basalt/manifest.json" in archive.namelist() else "manifest.json"
        if manifest_path not in archive.namelist():
            raise RuntimeError(f"{path.name}: no Basalt-compatible manifest")
        prefix = manifest_path[:-len("manifest.json")]
        manifest = json.loads(archive.read(manifest_path))
        application = read_part(archive, prefix, manifest.get("application"))
        if application is None or len(application) < 120:
            raise RuntimeError(f"{path.name}: invalid application binary")
        resources = read_part(archive, prefix, manifest.get("resources"))
        worker = read_part(archive, prefix, manifest.get("worker"))

    sdk_major = application[10]
    sdk_minor = application[11]
    app_major = application[12]
    app_minor = application[13]
    app_name = decode_fixed(application[24:56])
    icon_resource_id = struct.unpack_from("<I", application, 88)[0]
    flags = struct.unpack_from("<I", application, 96)[0]
    app_uuid = uuid.UUID(bytes=application[104:120])
    header = AppHeader(
        sdk_major=sdk_major,
        sdk_minor=sdk_minor,
        app_major=app_major,
        app_minor=app_minor,
        icon_resource_id=icon_resource_id,
        flags=flags,
        app_name=app_name,
        app_uuid=app_uuid,
    )
    return PbwBundle(
        path=path,
        header=header,
        application=application,
        resources=resources,
        worker=worker,
        sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
    )


def uuid_bytes(value: uuid.UUID) -> bytes:
    return value.bytes


def app_metadata(header: AppHeader) -> bytes:
    name = header.app_name.encode("utf-8")[:96]
    return (
        uuid_bytes(header.app_uuid)
        + struct.pack("<II", header.flags, header.icon_resource_id)
        + bytes((header.app_major, header.app_minor, header.sdk_major, header.sdk_minor, 0, 0))
        + name.ljust(96, b"\x00")
    )


def stm32_crc32(data: bytes) -> int:
    crc = 0xFFFFFFFF
    for offset in range(0, len(data), 4):
        chunk = data[offset:offset + 4]
        if len(chunk) == 4:
            word = int.from_bytes(chunk, "little")
        else:
            word = 0
            for item in chunk:
                word = ((word << 8) | item) & 0xFFFFFFFF
        crc ^= word
        for _ in range(32):
            crc = (((crc << 1) ^ 0x04C11DB7) if crc & 0x80000000 else (crc << 1)) & 0xFFFFFFFF
    return crc


class Installer:
    def __init__(self, link: ProtocolLink) -> None:
        self.link = link
        self.token = 0x4100

    def next_token(self) -> int:
        self.token = 1 if self.token >= 0xFFFE else self.token + 1
        return self.token

    def insert_metadata(self, header: AppHeader) -> None:
        key = uuid_bytes(header.app_uuid)
        metadata = app_metadata(header)
        for attempt in range(8):
            token = self.next_token()
            payload = (
                b"\x01"
                + struct.pack("<H", token)
                + bytes((BLOB_DATABASE_APP, len(key)))
                + key
                + struct.pack("<H", len(metadata))
                + metadata
            )
            self.link.send_pebble(ENDPOINT_BLOB_DB, payload)
            response = self.link.await_endpoint(
                ENDPOINT_BLOB_DB,
                lambda value: len(value) >= 3 and struct.unpack_from("<H", value, 0)[0] == token,
                8.0,
            )
            status = response[2]
            if status == BLOB_STATUS_SUCCESS:
                return
            if status == BLOB_STATUS_TRY_LATER:
                time.sleep(0.25 * (attempt + 1))
                continue
            raise RuntimeError(f"Pebble AppDB rejected metadata with status {status}")
        raise RuntimeError("Pebble AppDB remained busy")

    def request_install_id(self, app_uuid: uuid.UUID) -> int:
        raw_uuid = uuid_bytes(app_uuid)
        self.link.send_pebble(ENDPOINT_APP_RUN_STATE, b"\x01" + raw_uuid)
        response = self.link.await_endpoint(
            ENDPOINT_APP_FETCH,
            lambda value: len(value) >= 21 and value[0] == 1 and value[1:17] == raw_uuid,
            15.0,
        )
        return struct.unpack_from("<I", response, 17)[0]

    def put_bytes(self, payload: bytes) -> int:
        self.link.send_pebble(ENDPOINT_PUT_BYTES, payload)
        response = self.link.await_endpoint(ENDPOINT_PUT_BYTES, lambda value: len(value) >= 5, 30.0)
        result = response[0]
        cookie = struct.unpack_from(">I", response, 1)[0]
        if result != PUT_BYTES_ACK:
            raise RuntimeError(f"Pebble NACKed PutBytes for token {cookie}")
        return cookie

    def send_part(self, part_type: int, data: bytes, install_id: int) -> None:
        cookie = self.put_bytes(
            b"\x01" + struct.pack(">I", len(data)) + bytes((part_type | APP_INSTALL_FLAG,)) + struct.pack(">I", install_id)
        )
        for offset in range(0, len(data), TRANSFER_CHUNK_BYTES):
            chunk = data[offset:offset + TRANSFER_CHUNK_BYTES]
            self.put_bytes(b"\x02" + struct.pack(">II", cookie, len(chunk)) + chunk)
            time.sleep(0.004)
        self.put_bytes(b"\x03" + struct.pack(">II", cookie, stm32_crc32(data)))
        self.put_bytes(b"\x05" + struct.pack(">I", cookie))

    def install(self, bundle: PbwBundle) -> None:
        print(f"Installing {bundle.header.app_name} ({bundle.path.name})")
        self.insert_metadata(bundle.header)
        install_id = self.request_install_id(bundle.header.app_uuid)
        self.send_part(PART_BINARY, bundle.application, install_id)
        if bundle.resources is not None:
            self.send_part(PART_RESOURCES, bundle.resources, install_id)
        if bundle.worker is not None:
            self.send_part(PART_WORKER, bundle.worker, install_id)
        time.sleep(0.15)


def unused_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as candidate:
        candidate.bind(("127.0.0.1", 0))
        return candidate.getsockname()[1]


def wait_for_firmware(console_port: int, process: subprocess.Popen[bytes], timeout: float = 40.0) -> None:
    deadline = time.monotonic() + timeout
    console: socket.socket | None = None
    while time.monotonic() < deadline and console is None:
        if process.poll() is not None:
            raise RuntimeError(f"QEMU stopped during boot with code {process.returncode}")
        candidate = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        candidate.settimeout(0.8)
        try:
            candidate.connect(("127.0.0.1", console_port))
            console = candidate
        except OSError:
            candidate.close()
            time.sleep(0.08)
    if console is None:
        raise RuntimeError("Could not connect to PebbleOS console")

    received = bytearray()
    console.settimeout(0.75)
    with console:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError(f"QEMU stopped during boot with code {process.returncode}")
            try:
                chunk = console.recv(4096)
            except socket.timeout:
                continue
            if not chunk:
                break
            received.extend(chunk)
            if len(received) > 65536:
                del received[:-65536]
            text = received.decode("utf-8", errors="replace")
            if "Ready for communication" in text or "<Launcher>" in text or "<SDK Home>" in text:
                return
    raise RuntimeError("PebbleOS did not report communication readiness")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--qemu", type=Path, required=True)
    parser.add_argument("--micro", type=Path, required=True)
    parser.add_argument("--base-spi", type=Path, required=True)
    parser.add_argument("--output-spi", type=Path, required=True)
    parser.add_argument("--watchfaces", type=Path, required=True)
    parser.add_argument("--manifest-output", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    for required in (args.qemu, args.micro, args.base_spi, args.watchfaces):
        if not required.exists():
            raise RuntimeError(f"Required input does not exist: {required}")

    bundles = [load_pbw(path) for path in sorted(args.watchfaces.glob("*.pbw"))]
    if not bundles:
        raise RuntimeError("No PBW files found for pre-seeding")

    args.output_spi.parent.mkdir(parents=True, exist_ok=True)
    args.manifest_output.parent.mkdir(parents=True, exist_ok=True)
    args.log.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.base_spi, args.output_spi)

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
        "-drive", f"if=none,id=spi-flash,file={args.output_spi},format=raw",
        "-no-reboot",
    ]
    print("Starting host Pebble Time QEMU")
    with args.log.open("wb") as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
        link: ProtocolLink | None = None
        try:
            wait_for_firmware(console_port, process)
            link = ProtocolLink("127.0.0.1", protocol_port)
            installer = Installer(link)
            for bundle in bundles:
                installer.install(bundle)
        finally:
            if link is not None:
                link.close()
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

    if args.output_spi.stat().st_size != args.base_spi.stat().st_size:
        raise RuntimeError("Seeded SPI image changed size")

    manifest = {
        "format": 1,
        "platform": "basalt",
        "base_spi_sha256": hashlib.sha256(args.base_spi.read_bytes()).hexdigest(),
        "seeded_spi_sha256": hashlib.sha256(args.output_spi.read_bytes()).hexdigest(),
        "watchfaces": [
            {
                "file": bundle.path.name,
                "name": bundle.header.app_name,
                "uuid": str(bundle.header.app_uuid),
                "sha256": bundle.sha256,
                "version": f"{bundle.header.app_major}.{bundle.header.app_minor}",
            }
            for bundle in bundles
        ],
    }
    args.manifest_output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Seeded {len(bundles)} watchfaces")
    print(f"SPI SHA-256: {manifest['seeded_spi_sha256']}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
