# P0-A — fixed-source PebbleOS Emery: Linux execution evidence (2026-10-25)

Status: **partial Gate A success, not final hardware acceptance**. All three *original, SHA256-matched* watchfaces executed in a private Linux QEMU trial for >=60 seconds. Dedicated background worker runtime test and Titan 2 hardware test remain **NOT RUN**.

## Source and toolchain

- Upstream official privilege fix: `coredevices/PebbleOS@3b69e4b894d299c98ace176f3a5e40cdb786e905`. Both `prv_app_task_main` and `prv_worker_task_main` cache `app_md` and `stack_top` before `pbl_user_mode_enter`. Neither wrapper re-reads kernel globals before `process_runtime_run`.
- SDK: `PebbleOS-SDK 0.1.11` / ARM GNU 15.2.1; Linux host QEMU `10.1.5-pebble19` / `pebble-emery` Cortex-M33.
- Firmware artifact SHA256: `82c040d2ec3fe98963289aca07c31493e56d9ee6a323d07f3179cd17d65b4295`.
- Micro flash SHA256: `3ea9fdcfebb552f5ae8aeb398ab2ce04bcf1f3a40baeb90e6a9d1481b11386b7`.
- Initial SPI flash SHA256: `94bd2ced51295050af0fd401863495680e0e7fcf10d0ff3c308611fb4cd86312`.
- Official SDK Linux QEMU executable SHA256: `9a44c46dcf45820b5896426f9c127ab31ad3980f4bb1f23392419e414a5ccc4b`.
- [Source build, assembly and four upstream integration tests](https://github.com/manufact-test/NullWrist/actions/runs/40229846534): 4/4 PASSED in 87.47 s. Actual task-wrapper ARM disassembly archived with this run.
- Linux QEMU binary reproducible from checksum-pinned official SDK: [tool artifact run](https://github.com/manufact-test/NullWrist/actions/runs/40230733616).

**Reproducibility qualification:** Two builds from the same source revision produced different full ELF SHA256 digests (`9217ef20...` versus `82c040d2...`) but identical task-wrapper assembly. The investigated differences are 20 build-id bytes and 4 non-functional debug-section bytes; byte-for-byte reproducibility is not claimed. Android candidate uses the *exact SHA-pinned* second artifact.

## Private PBW execution trial (Linux x86-64)

Only the six original files from the user's verified PBW pack were used locally. PBW binary contents **must not be published** to this repository. The local probe sent real Pebble Protocol frames to QEMU UART1; it used BlobDB AppDB insert, AppRunState start, AppFetch and PutBytes executable/resources with guest ACK checks. Screenshots were captured by real QEMU QMP `screendump` at 0, 10, 20, 30, 40, 50, 60 seconds after successful launch; the emulator ran under a normal OS process, not a mock.

| Watchface | SHA256 PBW prefix | Install ACK | UUID matches at start / after >=60s | Pixel changes t=0 -> t=60 | Re-launch after same-SPI QEMU restart |
|---|---|---|---|---:|---|
| Iridescency II (Emery) | `b253ec5f...` | yes | yes / yes | 1,156 | yes |
| Comic Watchface (Emery) | `e882eca1...` | yes | yes / yes | 2,616 | yes |
| Crush 'Em (Basalt) | `5047b8da...` | yes | yes / yes | 45,600 | yes |

Each app rendered a real, visually distinct watchface on 200×228. `AppRunState` returned the expected UUID after the 60-second observation period. The QEMU process remained alive throughout every trial. The harness deliberately terminated it afterward (expected SIGTERM).

All three installs survived process shutdown and a subsequent launch against their **existing** SPI flash image; explicit `AppRunStateStart` relaunched each without transferring a PBW again. Immediately after QEMU restart, the visible screen still showed native Settings: persisting the **selected active watchface** as the default UI state needs further P0-C handling even though AppDB/executable persistence works.

Local raw UART2 logs, protocol traces, screenshots and test scripts were retained in a private evidence archive for the user, **not** committed into this public repository. Raw UART2 has not yet undergone complete Pulse fault decoding; do not claim no hidden guest fault solely because the QEMU process is alive.

## Current gates and next work

- **A1-A4:** source fix, target build, assembly checks, and three 60-second Linux PBW samples demonstrated. App switching as a full sequence still needs a dedicated test.
- **A5:** runtime test for an authentic worker-enabled test PBW is pending. The worker source and machine-code paths were verified, but no worker payload was executed in this trial.
- **A6:** no app-entry DACCVIOL seen as a symptom in the 3 PBW runs; no general assertion that all guest crash bugs are resolved.
- **P0-B:** Android ARM64 lab assembly now consumes the exact fixed-source Linux firmware artifact, verifies source provenance and checksums, and **does not call** the obsolete `patch_emery_guest_entry.py`. New physical Titan 2 evidence is still required.

This report records test facts without changing `main`, legacy `app/`, old releases, user SPI backups or PBW binaries.
