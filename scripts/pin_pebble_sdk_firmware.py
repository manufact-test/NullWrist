#!/usr/bin/env python3
"""Copy the newest installed Pebble Basalt QEMU firmware into Android assets."""
from __future__ import annotations

import bz2
import hashlib
import json
import os
import pathlib
import shutil
import sys
from typing import Iterable


def version_key(value: str) -> tuple[int, ...]:
    parts: list[int] = []
    for token in value.replace("-", ".").split("."):
        digits = "".join(character for character in token if character.isdigit())
        parts.append(int(digits) if digits else 0)
    return tuple(parts)


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sdk_roots() -> Iterable[pathlib.Path]:
    explicit = os.environ.get("PEBBLE_SDK_ROOT")
    if explicit:
        yield pathlib.Path(explicit).expanduser()

    home = pathlib.Path.home()
    data_home = pathlib.Path(os.environ.get("XDG_DATA_HOME", home / ".local" / "share"))
    yield data_home / "pebble-sdk" / "SDKs"
    yield home / ".pebble-sdk" / "SDKs"


def installed_sdks() -> list[tuple[str, pathlib.Path]]:
    found: list[tuple[str, pathlib.Path]] = []
    for root in sdk_roots():
        if not root.is_dir():
            continue
        for manifest in root.glob("*/sdk-core/manifest.json"):
            try:
                metadata = json.loads(manifest.read_text(encoding="utf-8"))
                version = str(metadata["version"])
            except (OSError, ValueError, KeyError, TypeError):
                continue
            found.append((version, manifest.parent))
    return found


def find_latest_basalt_qemu() -> tuple[str, pathlib.Path]:
    candidates: list[tuple[str, pathlib.Path]] = []
    for version, sdk_core in installed_sdks():
        qemu = sdk_core / "pebble" / "basalt" / "qemu"
        if (qemu / "qemu_micro_flash.bin").is_file():
            candidates.append((version, qemu))
    if not candidates:
        searched = "\n".join(str(path) for path in sdk_roots())
        raise RuntimeError(f"No installed Basalt QEMU firmware found. Searched:\n{searched}")
    return max(candidates, key=lambda item: version_key(item[0]))


def copy_spi(source_directory: pathlib.Path, destination: pathlib.Path) -> None:
    plain = source_directory / "qemu_spi_flash.bin"
    compressed = source_directory / "qemu_spi_flash.bin.bz2"
    if plain.is_file():
        shutil.copyfile(plain, destination)
        return
    if compressed.is_file():
        with bz2.open(compressed, "rb") as source, destination.open("wb") as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
        return
    raise RuntimeError(f"No SPI flash image found in {source_directory}")


def main() -> int:
    repository = pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else pathlib.Path.cwd()
    version, source = find_latest_basalt_qemu()
    destination = repository / "app" / "src" / "main" / "assets" / "pebble" / "basalt"
    destination.mkdir(parents=True, exist_ok=True)

    micro_destination = destination / "qemu_micro_flash.bin"
    spi_destination = destination / "qemu_spi_flash.bin"
    shutil.copyfile(source / "qemu_micro_flash.bin", micro_destination)
    copy_spi(source, spi_destination)

    manifest = {
        "sdk_version": version,
        "platform": "basalt",
        "machine": "pebble-snowy-bb",
        "source_directory": str(source),
        "files": {
            micro_destination.name: {
                "size": micro_destination.stat().st_size,
                "sha256": sha256(micro_destination),
            },
            spi_destination.name: {
                "size": spi_destination.stat().st_size,
                "sha256": sha256(spi_destination),
            },
        },
    }
    (destination / "firmware-manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(manifest, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
