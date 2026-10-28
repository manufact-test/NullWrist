# NullWrist Time 2 — standalone Android project

**Status: 0.1.0-dev, NOT yet a PebbleOS emulator.** Current deliverables are an independently installable Android shell, real 200x228 geometry and letterbox touch calibration, orange bitmap wordmark and raster launcher icon, QMP command encoding, button/touch probes and safe Emery PBW metadata import. No firmware or QEMU binary is bundled; no PBW actually executes. Do not market a preview APK as fully functional.

## Isolation invariant

- Old app: `app/`, package `com.manufacttest.pebblereardisplay`, releases up to 0.8.12. **UNCHANGED.**
- New app: `time2/app/`, package `com.manufacttest.nullwrist.time2`, independent internal storage, launch icon/signature and Gradle build.
- Build with `cd time2 && gradle :app:testDebugUnitTest :app:assembleDebug` using JDK 17, Android SDK 36, Gradle 9.5.0. Output: `time2/app/build/outputs/apk/debug/app-debug.apk`.
- Run code and emulator builds only on the isolated feature branch. Old binaries, old flash, previous tags and releases must not be modified.
- Do not request notifications, network, location or background access until a feature needs and justifies it.

## Technical spec and gates

See [MASTER.md](MASTER.md). Upstream Emery firmware is built as `pbl configure --board qemu_emery && pbl build` and booted using `pbl qemu`. Emulating a Pebble Time 2 on Android requires a separate cross-built QEMU with full Emery peripherals, a framebuffer transport, QMP input, PBW installation and explicit Android service adapters. No shortcuts using fake PNG watchfaces. A native simulator cannot execute PBWs.

## Design

Pixel mark (hand-coded 5x7, no external fonts), nearly-black graphite, hot orange `#FF6D00`; discreet white text. Preserve authentic Pebble LCD pixels rather than interpolating them. Rear preview verifies touch coordinate transforms and virtual button placement; **it is not a running OS**.

Desktop upstream Emery M0 smoke entrypoint: `bash time2/scripts/m0_emery_desktop.sh` from the repository root on a Linux host with documented dependencies. Dedicated workflow: `.github/workflows/time2-emery-desktop.yml`. This stage builds and tests genuine QEMU Emery upstream on Linux; it does not create an Android emulator yet. [Brand guide](BRAND.md).

For the future native renderer, `EmeryFrameProtocol` already implements validated 64-byte v2 snapshot reads, and `EmeryQmpClient` establishes a local, request/reply-checked QMP socket. See [frame transport](docs/FRAME-TRANSPORT-V2.md). Neither is presently attached to a running emulator.

### Verified milestone (21 October 2026)
The **real upstream Emery firmware** built and passed 4 Linux-QEMU integration tests for launcher/back/touch/swipe: [CI M0 run 39600331988](https://github.com/manufact-test/NullWrist/actions/runs/39600331988). PBW installation and native Android execution are not yet verified. The separate ARM64 native builder is currently an engineering experiment and not packaged in the Android app.

Experimental device-lab pipeline and staged on-phone QEMU entrypoint: [DEVICE-LAB.md](docs/DEVICE-LAB.md). This is not a release or proven Titan2 runtime; device tests are required.

Latest lab checkpoint: [Android emulator build (39601756889)](https://github.com/manufact-test/NullWrist/actions/runs/39601756889) and [signed-for-debug hardware lab APK (39603808282)](https://github.com/manufact-test/NullWrist/actions/runs/39603808282). The APK is **unverified on Titan2 hardware** and not a public release. [Third-party provenance](THIRD-PARTY-LICENSES.md).

### On-device confirmed (2026-10-22)
First Titan 2 Emery 0.2.0 lab run successfully displayed real firmware; user-exported host log shows QMP, screenshot and ARGB frame. Time2 0.3.0 experiments target native headless framebuffer 80ms timer, faster Android frame read, official Basalt compatibility and user-owned flash backup/restore. See [M1/M2 checkpoint](MASTER.md) and [new device-lab instructions](docs/DEVICE-LAB.md). Buttons, PBW execution and long-term stability still require verification.

### PBW installation experiment: 0.4.0-lab
The app previously only inspected and stored PBWs. The first actual guest installation implementation is now wired through QEMU UART1 / Pebble Protocol BlobDB / AppFetch / PutBytes. It remains **unverified on the user's real device** and shows explicit errors instead of pretending that importing a watchface automatically activates it. See [DEVICE-LAB](docs/DEVICE-LAB.md) for test steps.

### 0.6.0-lab PBW compatibility / 2026-10-25

The real PebbleOS Emery boot and UI are verified on Titan 2, but third-party
watchface execution still needs physical testing. The 0.5 installer incorrectly
required the prior PutBytes cookie for the INSTALL acknowledgment; genuine
PebbleOS returns ACK cookie **zero** after COMMIT. The 0.6 installer accepts
that proven protocol response, retries token-zero busy NACKs, uses conservative
1000-byte chunks/12ms pacing from legacy NullWrist, and asks the guest for
the actual running UUID instead of trusting transfer completion.

User-provided watchface inspection found: Iridescency II, Halcyon and Comic
Watchface have native Emery binaries; BeerClock, Crush 'Em and Essential
provide older Basalt binaries that should be eligible for documented Emery
fallback. These facts prove package compatibility metadata, not runtime health.
The user's uploaded UART2 log contains only PebbleOS boot output and **no
watchface execution fault** for Halcyon or Iridescency II.

**Optional personal starter pack:** `assets/starter-pbws/index.txt` contains six
SHA-256 pins; the 6 original PBW files are not published in this public
repository or CI artifact. A privately assembled APK may carry the matching
`assets/starter-pbws/<uuid>.pbw` entries. At startup, `LocalPbwLibrary`
imports these into app-private storage after hash and UUID checks, without
overwriting existing files. `×` in the library deletes a face, records a
tombstone so the starter won't silently return, and queues an individual
BlobDB AppDB DELETE on next guest session. No whole-AppDB CLEAR is issued.

The in-app PBW catalogue marks guest launch confirmed only after a matching
AppRunState response. A late crash, dependencies on phone-side PebbleKit JS,
or an unsupported external service may still cause guest "not responding".
Keep the debug UART2 log and report after trying each face.

The independent legacy app, all its signed releases and `main` are unchanged.
