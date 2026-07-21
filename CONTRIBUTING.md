# Contributing to Pebblehertz

Pebblehertz is a device-specific Android beta built around real PebbleOS firmware and a native QEMU runtime. Contributions are welcome, but reliability and reproducibility come before feature count.

## Before opening an issue

1. Check [`COMPATIBILITY.md`](COMPATIBILITY.md).
2. Search existing issues for the same watchface, UUID or error.
3. Confirm you are testing the latest beta.
4. Reproduce the problem at least once after restarting Pebblehertz.

## Bug reports

Use the GitHub bug report form and include:

- Pebblehertz version;
- Titan 2 Android / firmware build;
- selected runtime mode when applicable;
- watchface name, version, source and UUID;
- exact steps to reproduce;
- expected and actual behavior;
- screenshot or short video;
- whether the issue followed PBW import, screen lock, clearing recent apps, charging, low battery or network loss.

Do not publish private data, signing files, account tokens or full device dumps without reviewing them first.

## Feature requests

Describe the user problem first. Concrete examples are more useful than broad requests such as “add Pebble compatibility.”

Useful requests explain:

- who needs the feature;
- which watchface or workflow is blocked;
- what a successful result looks like;
- whether the feature would help one PBW or an entire compatibility class.

Check [`ROADMAP.md`](ROADMAP.md) before opening a request.

## Pull requests

For non-trivial work, open or reference an issue before implementation.

A good pull request should:

- stay focused on one problem;
- explain the user impact and technical approach;
- include reproduction steps for fixes;
- include tests where practical;
- preserve the current Basalt-only scope unless broader platform work was agreed first;
- avoid unrelated formatting changes;
- update documentation when behavior changes.

## Third-party assets

Do not add a bundled PBW without:

- a public source page;
- a pinned version;
- a verified SHA-256 hash;
- confirmation that it is a watchface package;
- an entry in `bundled-watchfaces.json` and `THIRD_PARTY_WATCHFACES.md`.

Do not commit:

- Android signing keystores;
- signing passwords;
- private API keys;
- proprietary firmware obtained from an unverified source;
- personal diagnostic logs;
- generated APK files.

## Development environment

- JDK 17
- Android SDK 36
- Android NDK 28
- CMake 3.22.1
- Gradle 9.5.0

```bash
python3 scripts/fetch_bundled_watchfaces.py
PEBBLE_SDK_VERSION=4.17 python3 scripts/fetch_pebble_basalt_firmware.py
gradle :app:testDebugUnitTest :app:assembleDebug
```

For QEMU, firmware and preseed details, read [`docs/architecture.md`](docs/architecture.md) and the scripts under `scripts/`.

## Validation expectations

Changes touching PBW installation or runtime switching should preserve:

- exact UUID confirmation through AppRunState;
- bounded PutBytes retries and pacing;
- persistent SPI state;
- rollback to the previous working face after a failed selection;
- cancellation of stale thumbnail work;
- correct rear-display routing on Titan 2.

Changes touching power behavior should test normal battery, charging, scheduled sleep and below-15% behavior.

## Communication

Be direct, specific and respectful. This is a small community project, so a clear reproduction case or tested patch is much more valuable than volume.
