#!/usr/bin/env python3
"""Optional source-level QEMU Emery legacy app stack compatibility experiment.
Only 3.x app task stack changes: 2KiB -> 4KiB; never disable MPU.
The official PebbleOS source, .git and revision remain unchanged.
The experiment is opt-in. Never change the canonical firmware without
running the user's original PBWs + failure/worker regressions first.
"""
from pathlib import Path
import hashlib
import json
import sys

source = Path(sys.argv[1])
report = Path(sys.argv[2])
if not source.is_file():
    raise SystemExit("Missing upstream app_manager.c")
old = """    case ProcessAppSDKType_Legacy2x:
    case ProcessAppSDKType_Legacy3x:
      return 2 * 1024;
"""
new = """    case ProcessAppSDKType_Legacy2x:
      return 2 * 1024;
    case ProcessAppSDKType_Legacy3x:
#if defined(CONFIG_PLATFORM_EMERY)
      // Candidate compatibility change: allow older Basalt games a 4 KiB stack
      // without changing the total 3.x app RAM segment or any MPU protections.
      return 4 * 1024;
#else
      return 2 * 1024;
#endif
"""
data=source.read_text()
if data.count(old)!=1:
    raise SystemExit("Official legacy stack-size stanza changed: stop experiment")
before=hashlib.sha256(data.encode()).hexdigest()
patched=data.replace(old,new)
source.write_text(patched)
after=hashlib.sha256(patched.encode()).hexdigest()
report.parent.mkdir(parents=True,exist_ok=True)
report.write_text(json.dumps({
    "kind":"opt-in diagnostic candidate, NOT a validated production fix",
    "scope":"qemu_emery ProcessAppSDKType_Legacy3x only",
    "before_stack":2048,"candidate_stack":4096,
    "app_ram_segment_size_unchanged":True,"MPU_unchanged":True,
    "source_sha256_before":before,"source_sha256_after":after,
},indent=2)+"\n")
print("Optional Emery legacy 3.x stack experiment:",before,after)
