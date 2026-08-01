from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TEXT_SUFFIXES = {
    ".c", ".cc", ".cmake", ".cpp", ".h", ".hpp", ".java", ".json", ".kt", ".kts",
    ".md", ".properties", ".py", ".sh", ".txt", ".xml", ".yaml", ".yml",
}
SKIP_DIRS = {".git", ".gradle", "build", ".idea"}
REPLACEMENTS = (
    ("PebbleHertz", "NullWrist"),
    ("Pebblehertz", "NullWrist"),
    ("PEBBLEHERTZ", "NULLWRIST"),
    ("pebblehertz", "nullwrist"),
)


def is_text_file(path: Path) -> bool:
    if any(part in SKIP_DIRS for part in path.parts):
        return False
    return path.suffix.lower() in TEXT_SUFFIXES or path.name in {
        "CMakeLists.txt", "gradlew", "gradlew.bat",
    }


def replace_brand_everywhere() -> None:
    for path in ROOT.rglob("*"):
        if not path.is_file() or not is_text_file(path):
            continue
        try:
            original = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        updated = original
        for old, new in REPLACEMENTS:
            updated = updated.replace(old, new)
        if updated != original:
            path.write_text(updated, encoding="utf-8")


def rename_application_class() -> None:
    package_dir = ROOT / "app/src/main/java/com/manufacttest/pebblereardisplay"
    old_path = package_dir / "PebblehertzApplication.java"
    new_path = package_dir / "NullWristApplication.java"
    if old_path.exists():
        content = old_path.read_text(encoding="utf-8").replace(
            "PebblehertzApplication", "NullWristApplication"
        )
        new_path.write_text(content, encoding="utf-8")
        old_path.unlink()


def remove_obsolete_release_workflow() -> None:
    path = ROOT / ".github/workflows/publish-pebblehertz-0.8.9.yml"
    if path.exists():
        path.unlink()


def update_version() -> None:
    path = ROOT / "app/build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    text, code_count = re.subn(r"versionCode\s*=\s*27\b", "versionCode = 28", text)
    text, name_count = re.subn(
        r'versionName\s*=\s*"0\.8\.10"', 'versionName = "0.8.11"', text
    )
    if code_count != 1 or name_count != 1:
        raise RuntimeError(
            f"Unexpected version source: versionCode replacements={code_count}, "
            f"versionName replacements={name_count}"
        )
    path.write_text(text, encoding="utf-8")


def write_icon_assets() -> None:
    vector = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path android:fillColor="#0E1720" android:pathData="M0,0h48v48h-48z" />
    <path android:fillColor="#52E0C4" android:pathData="M17,4h14v3h-14zM17,41h14v3h-14zM7,17h3v14h-3zM38,17h3v14h-3z" />
    <path android:fillColor="#1D2A36" android:pathData="M15,8h18v3h4v26h-4v3h-18v-3h-4v-26h4z" />
    <path android:fillColor="#FFF7DF" android:pathData="M15,12h18v24h-18z" />
    <path android:fillColor="#17202A" android:pathData="M24,16a8,8 0,1 0,0 16a8,8 0,1 0,0 -16" />
    <path android:fillColor="#FFF7DF" android:pathData="M24,20a4,4 0,1 0,0 8a4,4 0,1 0,0 0,-8" />
    <path android:fillColor="#FF665A" android:pathData="M17,30l12,-14h4l-12,14z" />
    <path android:fillColor="#17202A" android:pathData="M17,14h3v2h-3zM28,32h3v2h-3z" />
</vector>
'''
    (ROOT / "app/src/main/res/drawable/ic_launcher.xml").write_text(
        vector, encoding="utf-8"
    )

    svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-labelledby="title desc">
  <title id="title">NullWrist icon</title>
  <desc id="desc">A floating watchface display with a null symbol and disconnected wrist brackets.</desc>
  <rect width="512" height="512" rx="112" fill="#0E1720"/>
  <g fill="#52E0C4">
    <rect x="181" y="42" width="150" height="32" rx="8"/>
    <rect x="181" y="438" width="150" height="32" rx="8"/>
    <rect x="42" y="181" width="32" height="150" rx="8"/>
    <rect x="438" y="181" width="32" height="150" rx="8"/>
  </g>
  <path d="M160 86h192v32h42v276h-42v32H160v-32h-42V118h42z" fill="#1D2A36"/>
  <rect x="160" y="128" width="192" height="256" rx="8" fill="#FFF7DF"/>
  <circle cx="256" cy="256" r="88" fill="#17202A"/>
  <circle cx="256" cy="256" r="44" fill="#FFF7DF"/>
  <path d="M176 334 310 174h44L220 334z" fill="#FF665A"/>
  <rect x="181" y="149" width="32" height="20" rx="4" fill="#17202A"/>
  <rect x="299" y="343" width="32" height="20" rx="4" fill="#17202A"/>
</svg>
'''
    docs = ROOT / "docs"
    docs.mkdir(exist_ok=True)
    (docs / "nullwrist-icon.svg").write_text(svg, encoding="utf-8")


def write_readme() -> None:
    readme = '''<div align="center">

<img src="docs/nullwrist-icon.svg" width="112" alt="NullWrist app icon">

# NullWrist

**Pebble Time watchfaces on the rear display of the Unihertz Titan 2.**

`wrist = null; watchface != null;`

No root. Real PebbleOS. Native ARM64 QEMU.

[![Version](https://img.shields.io/badge/version-0.8.11_beta-f36f56)](../../releases/latest)
![Device](https://img.shields.io/badge/device-Unihertz_Titan_2-222222)
![Android](https://img.shields.io/badge/Android-9%2B-3ddc84)
![Root](https://img.shields.io/badge/root-not_required-6c9cff)

<img src="app/src/main/assets/watchface-thumbnails/13371337-d689-4a2b-a2fb-f602b46959a7.png" width="132" alt="Pebble watchface preview">
<img src="app/src/main/assets/watchface-thumbnails/a49c82fd-830e-48b4-a82e-9cf8da77f4c5.png" width="132" alt="Pebble watchface preview">
<img src="app/src/main/assets/watchface-thumbnails/84678888-13d8-41dc-ba56-47f88724dea5.png" width="132" alt="Pebble watchface preview">
<img src="app/src/main/assets/watchface-thumbnails/65c08138-7700-4c31-8ddf-3c56b67159e8.png" width="132" alt="Pebble watchface preview">

[Download the latest beta](../../releases/latest) · [Roadmap](ROADMAP.md) · [Compatibility](COMPATIBILITY.md) · [Report a bug](../../issues/new?template=bug_report.yml)

</div>

## What is NullWrist?

NullWrist turns the Titan 2 rear screen into a tiny Pebble Time. The selected `.pbw` watchface runs inside real PebbleOS firmware through a native ARM64 QEMU runtime, while Android handles the watchface library, imports, previews and rear-display routing.

This is not a visual imitation. NullWrist boots the Basalt firmware, installs Pebble packages through the original Pebble protocols and displays the real `144 × 168` framebuffer.

## Current beta: 0.8.11

- New NullWrist name, application icon and repository presentation.
- One continuous foreground runtime with watchdog recovery.
- Boot and application-update restoration.
- 16 bundled Pebble Time watchfaces with real QEMU-rendered previews.
- Import and retain additional `.pbw` watchfaces.
- Switch faces without rebooting PebbleOS.
- Confirm the exact active Pebble UUID before showing `ACTIVE / ON AIR`.
- Duplicate import protection by Pebble UUID.
- Optional Wise and PayPal project support links.

See [`CHANGELOG_0.8.11.md`](CHANGELOG_0.8.11.md) for release details.

## Install

1. Download `NullWrist-0.8.11.apk` from the [latest release](../../releases/latest).
2. Allow installation from the browser or file manager you used to download it.
3. Open NullWrist and grant the requested notification and background-operation access.
4. In the Titan 2 secondary-screen settings, keep the rear display enabled as needed.
5. Exclude NullWrist from battery optimization, DuraSpeed restrictions and the Titan 2 App blocker.

The package ID and signing identity remain unchanged, so 0.8.11 installs over the previous signed beta normally.

## Supported today

| Area | Current support |
|---|---|
| Phone | Unihertz Titan 2 |
| Pebble platform | Basalt / Pebble Time |
| Packages | Pebble Time watchfaces in `.pbw` format |
| Root access | Not required |
| Imported faces | Standalone faces work best |
| Other rear-screen phones | Not yet tested or supported |

Read [`COMPATIBILITY.md`](COMPATIBILITY.md) before reporting an imported face that needs phone-side JavaScript, configuration pages, weather or another online service.

## Known limitations

- Android Force stop and the Titan 2 App blocker remain terminal system actions.
- PebbleKit JS, watchface configuration pages, network/weather bridges and phone notifications are not implemented yet.
- Health data such as steps and heart rate is not available yet.
- Pebble apps and games that depend on physical Pebble buttons are outside the current watchface-focused scope.

See the full [`ROADMAP.md`](ROADMAP.md) for planned work.

## Roadmap at a glance

- **0.9.0 — Personal Face:** use a photo or GIF with configurable digital time overlays.
- **0.10.0 — PebbleKit Compatibility:** JavaScript runtime, AppMessage, configuration pages and network/weather support.
- **0.11.0 — Phone Data:** Health Connect, selected notifications, music and calendar data.

Roadmap versions describe priorities rather than guaranteed dates.

## Reporting bugs

Use the [bug report form](../../issues/new?template=bug_report.yml) and include:

- NullWrist version;
- Titan 2 firmware / Android build;
- watchface name, source and UUID when available;
- exact reproduction steps;
- screenshot or short video;
- whether the problem appears after clearing recent apps, locking the phone, switching, importing or deleting a PBW.

## Build from source

Requirements:

- JDK 17
- Android SDK 36
- Android NDK 28
- CMake 3.22.1
- Gradle 9.5.0
- ARM64 Android target

```bash
python3 scripts/fetch_bundled_watchfaces.py
PEBBLE_SDK_VERSION=4.17 python3 scripts/fetch_pebble_basalt_firmware.py
gradle :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Architecture notes are available in [`docs/architecture.md`](docs/architecture.md). Contributions are described in [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Reproducible third-party assets

Bundled PBW source pages and pinned SHA-256 hashes are recorded in [`bundled-watchfaces.json`](bundled-watchfaces.json) and [`THIRD_PARTY_WATCHFACES.md`](THIRD_PARTY_WATCHFACES.md). Firmware and watchface fetch scripts verify pinned inputs before they are packaged.

## Support the project

NullWrist remains free. Support is optional and helps fund testing devices and future rear-screen ports.

- [Wise](https://wise.com/pay/me/ilyas709)
- [PayPal](https://www.paypal.me/myarrogantfox)

## Disclaimer

NullWrist is an independent community project and is not affiliated with Unihertz, Pebble, Google or the original watchface authors. Third-party names and trademarks belong to their respective owners.
'''
    (ROOT / "README.md").write_text(readme, encoding="utf-8")


def update_roadmap() -> None:
    path = ROOT / "ROADMAP.md"
    text = path.read_text(encoding="utf-8")
    shipped = '''## Shipped

### 0.8.11 — Current beta

- NullWrist name and visual identity across the Android app, documentation and release pipeline.
- New floating null-watchface application icon.
- One continuous PebbleOS foreground runtime with watchdog recovery.
- Boot and application-update restoration.
- Native Pebble Time / Basalt firmware running through ARM64 QEMU.
- Persistent PebbleOS SPI storage.
- Real `144 × 168` framebuffer on the Titan 2 rear display.
- 16 bundled watchfaces and imported `.pbw` support.
- AppRunState UUID confirmation before a face becomes `ACTIVE / ON AIR`.
- Duplicate PBW protection by Pebble UUID.
- Optional Wise and PayPal support links.

'''
    text, count = re.subn(
        r"## Shipped\n.*?(?=## Planned\n)", shipped, text, flags=re.DOTALL
    )
    if count != 1:
        raise RuntimeError(f"Unable to update ROADMAP shipped section: {count}")
    path.write_text(text, encoding="utf-8")


def update_compatibility() -> None:
    path = ROOT / "COMPATIBILITY.md"
    text = path.read_text(encoding="utf-8")
    text = text.replace("not implemented in 0.8.9", "not implemented in 0.8.11")
    runtime = '''## Runtime

NullWrist 0.8.11 uses one continuous Android foreground service with watchdog recovery. It restores the selected runtime after phone reboot and application updates when always-on operation is enabled. The persistent notification is intentionally silent and non-interactive.

Android Force stop and the Titan 2 App blocker remain terminal system actions and require the user to open NullWrist again.

'''
    text, count = re.subn(
        r"## Runtime modes\n.*?(?=## Titan 2 setup\n)", runtime, text, flags=re.DOTALL
    )
    if count != 1:
        raise RuntimeError(f"Unable to update COMPATIBILITY runtime section: {count}")
    text = text.replace(
        "Diagnostic export is planned for 0.8.10.",
        "Use the latest release and include the exact reproduction sequence in the report.",
    )
    path.write_text(text, encoding="utf-8")


def update_contributing() -> None:
    path = ROOT / "CONTRIBUTING.md"
    text = path.read_text(encoding="utf-8")
    text = text.replace(
        "Changes touching power behavior should test normal battery, charging, scheduled sleep and below-15% behavior.",
        "Changes touching runtime survival should test normal use, charging, screen lock, clearing Recents, process recovery and phone reboot.",
    )
    path.write_text(text, encoding="utf-8")


def write_release_files() -> None:
    changelog = '''# NullWrist 0.8.11

## New identity

- Renamed the application and public project presentation to NullWrist.
- Added a new floating watchface icon built around the `wrist = null` idea.
- Updated Android labels, notifications, documentation, issue templates and release automation.
- Renamed the application class while preserving the existing Android package ID and signing identity.

## Runtime carried forward

- One continuous always-on PebbleOS runtime.
- Foreground-service watchdog recovery and boot/update restoration.
- Serialized QEMU protocol work and coalesced watchface switching.
- Active, selected and final remaining watchfaces remain protected from deletion.

## Upgrade

Version code 28; version name 0.8.11. The signed APK installs over the previous signed beta normally.
'''
    (ROOT / "CHANGELOG_0.8.11.md").write_text(changelog, encoding="utf-8")
    (ROOT / "apk-sha256.txt").write_text(
        "Pending signed NullWrist 0.8.11 release build.\n", encoding="utf-8"
    )


def validate() -> None:
    forbidden = ("Pebblehertz", "PebbleHertz", "PEBBLEHERTZ", "pebblehertz")
    hits: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or not is_text_file(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for token in forbidden:
            if token in text or token in path.as_posix():
                hits.append(f"{path.relative_to(ROOT)}: {token}")
    if hits:
        raise RuntimeError("Old public name remains:\n" + "\n".join(hits))

    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if 'android:name=".NullWristApplication"' not in manifest:
        raise RuntimeError("Manifest does not reference NullWristApplication")

    strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
    if '<string name="app_name">NullWrist</string>' not in strings:
        raise RuntimeError("Android app label was not updated")

    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if 'applicationId = "com.manufacttest.pebblereardisplay"' not in build:
        raise RuntimeError("Android applicationId changed unexpectedly")
    if 'versionCode = 28' not in build or 'versionName = "0.8.11"' not in build:
        raise RuntimeError("Release version was not updated")


def remove_temporary_files() -> None:
    for relative in (
        "scripts/rebrand_nullwrist.py",
        ".github/workflows/apply-nullwrist-rebrand.yml",
    ):
        path = ROOT / relative
        if path.exists():
            path.unlink()


def main() -> None:
    replace_brand_everywhere()
    rename_application_class()
    remove_obsolete_release_workflow()
    update_version()
    write_icon_assets()
    write_readme()
    update_roadmap()
    update_compatibility()
    update_contributing()
    write_release_files()
    validate()
    remove_temporary_files()


if __name__ == "__main__":
    main()
