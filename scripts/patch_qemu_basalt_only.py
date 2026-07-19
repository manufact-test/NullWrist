#!/usr/bin/env python3
"""Disable every Pebble QEMU machine registration except Pebble Time/Basalt."""
from __future__ import annotations

import re
import sys
from pathlib import Path

BASALT_MACHINE = "pebble-snowy-bb"
MACHINE_DEFINE = re.compile(
    r'(?m)^(?P<indent>\s*)'
    r'(?P<macro>DEFINE_MACHINE(?:_ARM)?)'
    r'\(\s*"(?P<name>pebble-[a-z0-9-]+)"\s*,\s*(?P<initializer>[^)]+)\)'
    r'(?P<suffix>\s*;?\s*)$'
)


def patch_file(path: Path) -> tuple[list[str], bool]:
    text = path.read_text(encoding="utf-8", errors="strict")
    disabled: list[str] = []
    retained = False

    def replace(match: re.Match[str]) -> str:
        nonlocal retained
        name = match.group("name")
        if name == BASALT_MACHINE:
            retained = True
            return match.group(0)

        disabled.append(name)
        indent = match.group("indent")
        macro = match.group("macro")
        initializer = match.group("initializer").strip()
        suffix = ";" if ";" in match.group("suffix") else ""
        return (
            f"{indent}/* pebble-rear-display: {name} excluded from Basalt-only build */\n"
            f"{indent}#if 0\n"
            f'{indent}{macro}("{name}", {initializer}){suffix}\n'
            f"{indent}#endif"
        )

    patched = MACHINE_DEFINE.sub(replace, text)
    if patched != text:
        path.write_text(patched, encoding="utf-8")
    return disabled, retained


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_qemu_basalt_only.py QEMU_SOURCE_ROOT", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    if not root.is_dir():
        raise RuntimeError(f"QEMU source directory does not exist: {root}")

    retained_sources: list[Path] = []
    disabled: list[tuple[Path, str]] = []
    all_pebble_machines: set[str] = set()

    for path in sorted((root / "hw").rglob("*.c")):
        text = path.read_text(encoding="utf-8", errors="replace")
        names = {match.group("name") for match in MACHINE_DEFINE.finditer(text)}
        if not names:
            continue
        all_pebble_machines.update(names)
        removed, retained = patch_file(path)
        if retained:
            retained_sources.append(path)
        disabled.extend((path, name) for name in removed)

    if BASALT_MACHINE not in all_pebble_machines or not retained_sources:
        raise RuntimeError(f"Could not find retained machine {BASALT_MACHINE}")
    if not disabled:
        raise RuntimeError("No non-Basalt Pebble machine registrations were disabled")

    remaining = all_pebble_machines - {name for _, name in disabled}
    if remaining != {BASALT_MACHINE}:
        raise RuntimeError(f"Unexpected Pebble machines remain enabled: {sorted(remaining)}")

    print("Retained Pebble machine:")
    for path in retained_sources:
        print(f"  {BASALT_MACHINE} in {path.relative_to(root)}")
    print("Disabled Pebble machines:")
    for path, name in disabled:
        print(f"  {name} in {path.relative_to(root)}")
    print("Shared Pebble devices and controllers were left untouched.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
