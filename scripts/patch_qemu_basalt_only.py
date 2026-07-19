#!/usr/bin/env python3
"""Disable every Pebble QEMU machine registration except Pebble Time/Basalt.

Touching this pinned source patch intentionally triggers the native AArch64 TCG rebuild.
"""
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
TYPE_INFO = re.compile(
    r'(?s)static\s+const\s+TypeInfo\s+(?P<variable>[A-Za-z0-9_]+)\s*=\s*\{'
    r'(?P<body>.*?)\n\};'
)
TYPE_INFO_MACHINE_NAME = re.compile(
    r'\.name\s*=\s*MACHINE_TYPE_NAME\(\s*"(?P<name>pebble-[a-z0-9-]+)"\s*\)'
)


def disabled_block(indent: str, statement: str, name: str) -> str:
    return (
        f"{indent}/* pebble-rear-display: {name} excluded from Basalt-only build */\n"
        f"{indent}#if 0\n"
        f"{indent}{statement}\n"
        f"{indent}#endif"
    )


def patch_file(path: Path) -> tuple[list[str], bool, set[str]]:
    text = path.read_text(encoding="utf-8", errors="strict")
    disabled: list[str] = []
    retained = False
    discovered: set[str] = set()

    def replace_macro(match: re.Match[str]) -> str:
        nonlocal retained
        name = match.group("name")
        discovered.add(name)
        if name == BASALT_MACHINE:
            retained = True
            return match.group(0)

        disabled.append(name)
        suffix = ";" if ";" in match.group("suffix") else ""
        statement = (
            f'{match.group("macro")}("{name}", '
            f'{match.group("initializer").strip()}){suffix}'
        )
        return disabled_block(match.group("indent"), statement, name)

    patched = MACHINE_DEFINE.sub(replace_macro, text)

    type_variables: dict[str, str] = {}
    for match in TYPE_INFO.finditer(patched):
        name_match = TYPE_INFO_MACHINE_NAME.search(match.group("body"))
        if name_match is None:
            continue
        name = name_match.group("name")
        discovered.add(name)
        type_variables[match.group("variable")] = name
        if name == BASALT_MACHINE:
            retained = True

    for variable, name in type_variables.items():
        if name == BASALT_MACHINE:
            continue
        registration = re.compile(
            rf'(?m)^(?P<indent>\s*)type_register_static\(\s*&{re.escape(variable)}\s*\);\s*$'
        )

        def replace_type_register(match: re.Match[str], machine_name: str = name) -> str:
            disabled.append(machine_name)
            return disabled_block(
                match.group("indent"),
                f"type_register_static(&{variable});",
                machine_name,
            )

        patched, count = registration.subn(replace_type_register, patched)
        if count != 1:
            raise RuntimeError(
                f"Expected one registration for {name} ({variable}) in {path}, found {count}"
            )

    if patched != text:
        path.write_text(patched, encoding="utf-8")
    return disabled, retained, discovered


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
        removed, retained, discovered = patch_file(path)
        if not discovered:
            continue
        all_pebble_machines.update(discovered)
        if retained:
            retained_sources.append(path)
        disabled.extend((path, name) for name in removed)

    if BASALT_MACHINE not in all_pebble_machines or not retained_sources:
        raise RuntimeError(f"Could not find retained machine {BASALT_MACHINE}")
    if not disabled:
        raise RuntimeError("No non-Basalt Pebble machine registrations were disabled")

    disabled_names = {name for _, name in disabled}
    remaining = all_pebble_machines - disabled_names
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
