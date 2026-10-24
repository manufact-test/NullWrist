# NullWrist Time 2 — third-party provenance and pre-release compliance

Status: 2026-10-21 / laboratory debug builds only.

**Native emulator:** Core Devices' QEMU fork, https://github.com/coredevices/qemu, pinned revision `8a0a7c646fd80bb13d92bb5e2320427e224a2f6b`. QEMU as a whole is licensed under **GNU GPL version 2** (see upstream LICENSE/COPYING). Its individual source files and linked dependencies may have different compatible licenses. All NullWrist's emulator-specific source modifications are available in this public repository at `time2/scripts/patch_qemu_emery_framebuffer.py`. The independent reproducible cross-build recipe is at `time2/scripts/build_emery_qemu_android.sh`. Distributing the native executable requires complying with the applicable GPL terms and providing appropriate corresponding source, build instructions and notices.

**Firmware:** PebbleOS, https://github.com/coredevices/PebbleOS, pinned revision `0f806feed7b4bf7a2358119c667a09a7c9b961ff`, top-level license Apache-2.0. Additional resources/fonts/assets within PebbleOS may have their own licenses. The build recipe `time2/scripts/m0_emery_desktop.sh` records SHA256 provenance. Audit the included firmware resources/licenses and package required copyright/attribution notices before a public release.

**Developer SDK (build time):** Core Devices PebbleOS-SDK 0.1.11 and toolchain, no claim that these licenses transfer to the resulting APK. Review SDK and third-party components separately.

**Pebble companion:** The Core Devices mobileapp is GPL-3.0. The Time 2 host does not incorporate that source; future bridge implementations must have an explicit licensing/design review before reuse.

**Watch apps and faces:** No unlicensed third-party PBW applications are pre-bundled in Time 2. The user may import compatible PBWs locally, and actual installation/execution is not yet verified.

This inventory is the start of a distribution review, **not** a legal clearance or a substitute for full LICENSE/NOTICE and dependency verification. Lab APKs are development evidence only, not a ready-for-store binary.
