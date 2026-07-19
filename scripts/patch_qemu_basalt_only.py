#!/usr/bin/env python3
"""Disable registration of every Pebble QEMU machine except Pebble Time/Basalt."""
from __future__ import annotations

import re
import sys
from pathlib import Path

BASALT_MACHINE = "pebble-snowy-bb"
TYPE_INIT = re.compile(r"(?m)^(?P<indent>\s*)type_init\((?P<initializer>[^)]+)\);\s*$")


def disable_machine_registration(path: Path, text: str) -> int:
    def replace(match: re.Match[str]) -> str:
        indent = match.group("indent")
        initializer = match.group("initializer")
        return (
            f"{indent}/* pebble-rear-display: non-Basalt machine removed */\n"
            f"{indent}#if 0\n"
            f"{indent}type_init({initializer});\n"
            f"{indent}#endif"
        )

    patched, count = TYPE_INIT.subn(replace, text)
    if count:
        path.write_text(patched, encoding="utf-8")
    return count


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_qemu_basalt_only.py QEMU_SOURCE_ROOT", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    if not root.is_dir():
        raise RuntimeError(f"QEMU source directory does not exist: {root}")

    disabled_files: list[tuple[Path, int, list[str]]] = []
    basalt_sources: list[Path] = []

    for path in sorted((root / "hw").rglob("*.c")):
        text = path.read_text(encoding="utf-8", errors="replace")
        if BASALT_MACHINE in text:
            basalt_sources.append(path)
            continue
        machine_names = sorted(set(re.findall(r'"(pebble-[a-z0-9-]+)"', text)))
        if not machine_names:
            continue
        count = disable_machine_registration(path, text)
        if count:
            disabled_files.append((path, count, machine_names))

    if not basalt_sources:
        raise RuntimeError(f"Could not find {BASALT_MACHINE} in QEMU sources")
    if not disabled_files:
        raise RuntimeError("No non-Basalt Pebble machine registrations were disabled")

    print("Basalt machine source retained:")
    for path in basalt_sources:
        print(f"  {path.relative_to(root)}")
    print("Disabled non-Basalt Pebble machine registrations:")
    for path, count, names in disabled_files:
        print(f"  {path.relative_to(root)}: {count} type_init; {', '.join(names)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
