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
