# NullWrist Time 2 — implementation plan / 2026-10-21

**Product**: Run real Pebble Time 2-class Emery PebbleOS on Unihertz Titan 2 rear display (200 x 228 emulated LCD / 410 x 502 physical LCD), touch, four virtual buttons, watchfaces and watchapps. No root.

**Baseline**: `manufact-test/NullWrist` main 71fd6e5e... (v0.8.12). Never change `app/`, prior tags/releases, APK signing, Basalt firmware/SPI or existing user data. New Gradle application/module `time2/app` and new app ID. Existing app can co-exist on one phone; avoid simultaneously running both QEMU services at full CPU when runtime arrives.

## Definition of done — *not yet reached*
On real Android 16 Titan 2, launch Time2 from SubScreen; boot real PebbleOS Emery to authentic watchface; access built-in launcher/settings; run imported Emery watchapp plus 2 watchfaces; receive notification and current music metadata/control with consent; navigate touch and four virtual buttons; survive lock/unlock, screen flip, reboot, wireless loss and process recovery; no hangs or sustained overheating; old NullWrist stays unchanged.

## Architecture (new implementation, not a fork of old Java classes)
1. **Host Android shell** — MainActivity, RearActivity, 5x7 bitmap wordmark, privacy/diagnostics.
2. **ViewportMapper** — exact 200x228 frame letterboxing to arbitrary surface, bounds-check and reverse touch mapping. DONE: unit tests.
3. **Emery PBW library** — private files, UUID, platform/manifest verification, basic watchapp/watchface metadata; content-size cap. DONE: scan/import. TODO: per-file hashes, install queue, uninstall, SDK binary compatibility checks and sandbox.
4. **Emery QEMU backend** — full Cortex-M33 qemu_emery firmware; cross-compile qemu for Android arm64, pin hashes, support independent persistent flash and boot images; no bundled emulator yet. TODO.
5. **Display transport** — versioned frame shared memory or socket with sequence counter and renderer; no frames provided today. TODO.
6. **Input backend** — calibrate QMP touch down/move/up, drag, cancellation; virtual Back/Up/Select/Down; serial event queue. UI mapping/probe DONE, backend TODO.
7. **Package execution** — Pebble protocol install/app-start/stop (BlobDB/AppFetch/PutBytes), preserve UUID state, reboot-safe AppDB. TODO.
8. **Companion** — explicit consent, local Android NotificationListener, MediaSession, optional location/weather/calendar, PebbleKit/AppMessage, per-PBW grants and audits. TODO.
9. **Reliability** — one process, bounded watchdog, atomic flash persistence, CPU/thermal/battery tests, user-controlled always-on. TODO.
10. **Distribution** — proper independent launcher raster icon, signing/release CI, license audit for QEMU dependencies, PebbleOS firmware (Apache-2.0), mobileapp GPL-3.0 (don't copy blindly) and bundled third-party PBWs. TODO.

## Required milestone gates
- M0 (desktop): pin PebbleOS/SDK/QEMU SHA; reproduce `pbl configure --board qemu_emery; pbl build; pbl qemu`; real launcher; QMP button/touch; install at least one emulator-compatible PBW; logs, screenshots and hashes.
- M1 (Android): cross-compile official-compatible QEMU Cortex-M33/peripheral machine to NDK ARM64, build firmware, maintain separate image state; boot real OS and read genuine 200x228 frames; Android 16 proof.
- M2 (UX): connect SurfaceView with frame transport and real touch via QMP, virtual buttons; main/rear screen switching; session state.
- M3 (packages): install/launch/kill face+app via Pebble protocol; maintain AppDB; restart preservation.
- M4 (phone services): add opt-in notification, music, calendar, weather, PebbleKit JS one by one; test exact permission behavior.
- M5 (release): regression tests, repeated 30-minute sessions, thermal, battery, user docs, independent signed APK.

Do not claim a functional Emery runtime until M1-M3 are demonstrated; do not call the new app 1.0 stable before M5.

## Risk decisions
- The old Basalt QEMU is patched to DISABLE every other QEMU machine. It cannot simply be retargeted by changing dimensions. New emulator needs new executable/machine and often different framebuffer format and protocol.
- Pebble Time 2 hardware differs from the QEMU Emery board: this is **platform firmware emulation**, not electrically exact virtual hardware. Physical HRM, BLE controller, mic and other drivers require optional adapters.
- App or watchface PBW may require phone JS and permissions; native emulator alone does not guarantee all third-party apps work.
- Android 16 rear-screen navigation/gesture blocking must be tested on device; no assumption that Android gestures can always be intercepted.
- GPL-3.0 Android companion source is useful protocol reference but not automatically acceptable for reuse without correct license compliance.

## Established QEMU device names (source-verified 2026-10-21)
Core Devices QEMU `hw/arm/pebble_generic.c` defines `-machine pebble-emery` / `cortex-m33`, unlike the firmware *build-board name* `qemu_emery`. Touch is QMP `input-send-event` with ABS X/Y and BTN left; pebble_touch.c scales from QEMU absolute input range. `EmeryQmpCommands` builds serialized messages for the future transport; input is still diagnostic-only. Source pinning: `upstream.lock.json`.

## Acceptance evidence 
Store CI links, source hashes, signed APK hashes, physical-device logs and reproduction steps in this directory as milestones complete. No build success alone proves PebbleOS actually boots on Titan 2.

## Native-build experiment (2026-10-21)
Isolated script `time2/scripts/build_emery_qemu_android.sh` uses pinned QEMU `8a0a7c6`, Android NDK 28, ARM64 API35, compile-time framebuffer v2 exporting *final rendered ARGB8888* from generic Emery display. Adds Android shm shim without touching Basalt. CI: `.github/workflows/time2-emery-native.yml`. **Not ready for distribution until it passes and the binary boots real firmware on-device.**

## M0 desktop outcome — verified 2026-10-21
M0 firmware boot and controls sub-gate **PASS**: GitHub Actions run `39600331988` compiled pinned `qemu_emery` firmware and executed **four upstream integration cases**: launcher opens, back returns home, swipe scrolls, touch opens a menu item. Result: **4 passed in 87.36s**. This is native Linux QEMU, NOT Android. Full original M0 still requires installation and launch of a third-party Emery PBW, tracked separately. Do not mark M1 (Android native boot) as complete based on this result.

## M1 Android builder first-attempt notes
The initial experimental workflow `39601332647` failed before starting compilation because the generated shell script contained malformed duplicated trailing lines. The old Android project and its artifacts were not affected. Fixed in Time2 branch; future runs must pass `bash -n` before toolchain installation. Native proof is still pending.

## New M1 laboratory build checkpoint — 2026-10-21
- **PASS** native Android ARM64 cross-build of Core Devices QEMU `pebble-emery`: [GitHub Actions 39601756889](https://github.com/manufact-test/NullWrist/actions/runs/39601756889), SHA-256 artifact retained.
- **PASS** official Emery firmware image generation and four Linux QEMU navigation/touch tests: [GitHub Actions 39602123890](https://github.com/manufact-test/NullWrist/actions/runs/39602123890). Development ELF/SPI images and hashes archived separately.
- **PASS** experimental `0.2.0-lab` Android assembly from both SHA256-checked artifact groups: [GitHub Actions 39603808282](https://github.com/manufact-test/NullWrist/actions/runs/39603808282). New activity-scoped emulator launch/QMP/frame reader is included; lab image not demonstrated on real hardware.
- **NOT YET VERIFIED** startup on real Titan2 Android 15+, first framebuffer, touch/virtual-button QMP in the same APK, persistent SPI, process cleanup and thermal stability. They are mandatory before M1 is declared complete.
- Available on-phone diagnostic export is `EXPORT EMERY DIAGNOSTICS` in the app's front-screen interface.
- All old releases untouched. `main` remains `71fd6e5eba5b36b50168277be82e0eadbbc2a58c`. New project remains draft PR #21, do not merge.
- Third-party attribution and release gate tracked in [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).

### First physical Titan2 test / screen not painted
2026-10-21 user-supplied screenshot: orange NullWrist shell, QMP handshake established, no actual Emery pixels. Native QEMU log only SIGTERM from Android host. This is a **failed M1 frame/render verification**; do not call Emery firmware boot established. New code uses QMP headless screendump to force renderer while QEMU uses `-display none`; export now includes Android host diagnostic events and native QEMU log. Recheck on hardware: screen actual, QMP screenshot successful, framebuffer v2 sequence, virtual inputs. No older releases touched.

## 2026-10-22 — first physical boot verified and M2 follow-up

**User-confirmed on Titan2 Android:** QEMU starts, QMP handshake succeeds, headless screenshot is produced and the `200 x 228` ARGB framebuffer is delivered. Exported Android host log:
`Booting genuine PebbleOS / Emery` → `Emery QMP connected` → `QEMU headless screenshot generated` → `Real Emery framebuffer detected — interactive test mode`.
Native process terminates with SIGTERM when Activity closes: expected shutdown. This supports **M1 physical boot/render path PASS**. There is no separate evidence yet for gestures, four-button behavior, multiple launch persistence or app installation. Treat these as pending M2/M3.

**M2 implementation batch, branch only:**
- Native Core Devices QEMU adds private headless display refresh timer, 80 ms on QEMU virtual clock when `PEBBLE_EMERY_FB_PATH` set. Cross-build **SUCCESS**: [39699350838](https://github.com/manufact-test/NullWrist/actions/runs/39699350838).
- Android foreground loop now polls latest framebuffer each 75 ms; screendump remains fallback (every 4.5 s after a real frame) for protection against missing refresh. This optimization is not yet physically validated; regression check required.
- PBW import uses official Emery→Basalt→root compatibility hierarchy. For Basalt SDK minor below `0x54`, root below `0x16`; verifies executable header UUID and bounds ZIP entries. **This is library support only, NOT installation on PebbleOS.**
- User-owned Emery SPI flash backup/restore is available through SAF; restores size-checked image using atomic replace and blocks while an active emulator session exists. Intended to preserve device data between reinstallations of debug APKs (whose signatures may vary). Never touches legacy NullWrist private data.
- Version `0.3.0-lab`. Next physical check: true frame animations without disk refresh stall, buttons, touch, backup roundtrip, and app/face candidate import.
- Separate PR #21 remains draft, root legacy `main` untouched.

**Next M3 major block:** implement one authenticated local Pebble QEMU serial protocol transport over `pebble.sock`, then official PutBytes + BlobDB AppFetch app install/start and watchface change. No physical third-party PBW execution has been claimed.

## 0.4.0-lab — M3 first on-device PBW installer candidate

**Trigger:** Titan 2 user confirmed 0.3.0 runs Emery but importing a watchface does not make it appear. The uploaded file contained prior normal QEMU boot/shutdown messages; it does not contain a PBW-specific error. Prior "import" only stored bytes, not an OS install.

**Implemented in independent Time2 branch** (not yet verified on-device):
- Pure `EmeryPebbleWire` QEMU-serial framing (0xFEED/0xBEEF, protocol 1 SPP) plus nested Pebble protocol (big-endian length/endpoint), fragmentation handling and CRC32 STM32. JUnit tests.
- App-private Unix socket transport to actual `pebble.sock` (generic emulated UART1 intercepted by `pebble_control`), companion AppVersion response, WatchVersion query.
- `EmeryPbwInstaller`: official PebbleOS modern AppDB BlobDB 0xB1DB insert; AppRunStateStart 0x34; watch AppFetchRequest 0x1771; PutBytes 0xBEEF INIT / PUT / COMMIT / INSTALL with 2k chunks for executable plus optional resources and worker.
- Imported PBWs now show per-app **INSTALL / LAUNCH** button. Selecting queues one UUID, boots real Emery and attempts transfer on a dedicated background serial worker, reporting stages/errors in on-screen status + exported Android host diagnostics. No user-facing fake success.
- No external TCP or Root permissions; only app-private Unix socket.
- `0.4.0-lab` is a hardware diagnostic, **not proven M3**. Do not mark app/watchface execution complete until device log confirms successful BlobDB+AppFetch+PutBytes and user sees/operates app on actual PebbleOS.

**Next user evidence:** screenshot after selecting INSTALL and exported emery host diagnostics. If import itself is rejected, the exact PBW file (if user is willing to share) or full "Import rejected" message is needed to reproduce UUID/manifest compatibility; do not diagnose an unknown PBW from QEMU boot logs alone.
