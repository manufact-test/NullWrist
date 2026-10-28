#!/usr/bin/env python3
"""Fail-closed checks for the official 2026-10-22 Cortex-M33 privilege fix.

Static source verification is a CI gate; disassembly is archived for human
inspection. It does NOT prove that any third-party PBW executes correctly.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

EXPECTED = {
    "fw/process_management/app_manager.c": {
        "function": "prv_app_task_main",
        "context": "s_app_task_context.app_md",
        "stack": "s_app_stack_top",
        "task": "PebbleTask_App",
    },
    "fw/process_management/worker_manager.c": {
        "function": "prv_worker_task_main",
        "context": "s_worker_task_context.app_md",
        "stack": "s_worker_stack_top",
        "task": "PebbleTask_Worker",
    },
}

def checked_wrapper(path: Path, config: dict) -> dict:
    source = path.read_text(encoding="utf-8")
    start = re.search(r"static\s+void\s+" + config["function"] + r"\s*\([^)]*\)\s*\{", source)
    if start is None:
        raise ValueError(f"{path}: task-entry function not found")
    body = source[start.end():]
    end = body.find("sys_exit();")
    if end < 0:
        raise ValueError(f"{path}: task-entry boundary not found")
    body = body[:end]
    # Retain exact data dependencies: both privileged globals must be read
    # before entering user mode, and never reread to start the process.
    statements = [
        r"const\s+PebbleProcessMd\s*\*\s*app_md\s*=\s*" + re.escape(config["context"]) + r"\s*;",
        r"void\s*\*\s*stack_top\s*=\s*" + re.escape(config["stack"]) + r"\s*;",
        r"if\s*\(\s*app_md\s*->\s*is_unprivileged\s*\)",
        r"pbl_user_mode_enter\s*\(\s*\)\s*;",
        r"process_runtime_run\s*\(\s*app_md\s*,\s*" + config["task"] +
          r"\s*,\s*entry_point\s*,\s*stack_top\s*\)\s*;",
    ]
    cursor = 0
    spans = []
    for pattern in statements:
        match = re.search(pattern, body[cursor:])
        if match is None:
            raise ValueError(f"{path}: unsafe or missing privilege-entry sequence at position {len(spans)}")
        spans.append([cursor + match.start(), cursor + match.end()])
        cursor += match.end()
    unprivileged_segment = body[spans[3][1]:spans[4][0]]
    if config["context"] in unprivileged_segment or config["stack"] in unprivileged_segment:
        raise ValueError(f"{path}: kernel global read after dropping privileges")
    # Ensure any new reads between the actual user-mode entry and run are caught.
    post_switch = body[spans[3][1]:spans[4][1]]
    for name in (config["context"], config["stack"]):
        if name in post_switch:
            raise ValueError(f"{path}: forbidden post-CONTROL.nPRIV global access: {name}")
    return {"path": str(path), "function": config["function"],
            "source_sha256": hashlib.sha256(source.encode()).hexdigest(),
            "sequence": "app_md + stack_top cached before pbl_user_mode_enter; run uses locals",
            "result": "pass"}

def checked_elf(elf: Path, target: Path) -> dict:
    if not elf.is_file() or elf.stat().st_size == 0:
        raise ValueError(f"missing firmware ELF: {elf}")
    data = elf.read_bytes()
    if not data.startswith(b"\x7fELF"):
        raise ValueError("pebbleos.elf is not an ELF file")
    target.mkdir(parents=True, exist_ok=True)
    result = {"elf_sha256": hashlib.sha256(data).hexdigest(),
              "elf_size": len(data), "disassembly": {}}
    for config in EXPECTED.values():
        func = config["function"]
        dump = subprocess.run(["arm-none-eabi-objdump", "-d", f"--disassemble={func}", str(elf)],
                              check=True, capture_output=True, text=True).stdout
        # Any wrapper missing from final ELF makes the build unverifiable.
        if not re.search(r"<" + re.escape(func) + r">:", dump):
            raise ValueError(f"missing {func} in linked ELF symbol table")
        (target / (func + ".asm")).write_text(dump, encoding="utf-8")
        result["disassembly"][func] = {
            "sha256": hashlib.sha256(dump.encode()).hexdigest(),
            "file": func + ".asm", "result": "available_for_review"}
    return result

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--elf", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--disassembly-dir", type=Path)
    args = parser.parse_args()
    evidence = {"checks": []}
    for relative, config in EXPECTED.items():
        evidence["checks"].append(checked_wrapper(args.source / relative, config))
    if args.elf is not None:
        if args.disassembly_dir is None:
            parser.error("--disassembly-dir is required with --elf")
        evidence["firmware"] = checked_elf(args.elf, args.disassembly_dir)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
    print(json.dumps(evidence, indent=2))
    return 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f"PebbleOS privilege check FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
