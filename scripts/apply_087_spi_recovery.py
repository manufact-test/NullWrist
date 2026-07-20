#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
qemu_path = root / "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleQemuProcess.java"
build_path = root / "app/build.gradle.kts"
readme_path = root / "README.md"

text = qemu_path.read_text(encoding="utf-8")
text = text.replace(
    "import android.system.OsConstants;\n\n",
    "import android.system.OsConstants;\n\n"
    "import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;\n"
    "import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;\n\n"
)
text = text.replace(
    "import java.util.List;\n",
    "import java.util.List;\nimport java.util.UUID;\n"
)
old = '''        boolean existingSpiFlash = spiFlash.isFile() && spiFlash.length() > 0;
        copyAsset(ASSET_ROOT + "qemu_micro_flash.bin", microFlash, true);
        copyAsset(ASSET_ROOT + "qemu_spi_flash.bin", spiFlash, false);
        if (!existingSpiFlash) {
            new InstalledWatchfaceRegistry(context).clear();
        }
'''
new = '''        boolean existingSpiFlash = spiFlash.isFile() && spiFlash.length() > 0;
        boolean recoverStaleImportedApps = existingSpiFlash && hasImportedRegistryMismatch();
        copyAsset(ASSET_ROOT + "qemu_micro_flash.bin", microFlash, true);
        copyAsset(
                ASSET_ROOT + "qemu_spi_flash.bin",
                spiFlash,
                recoverStaleImportedApps
        );
        if (!existingSpiFlash || recoverStaleImportedApps) {
            // Preserve PBW files in the Android library, but rebuild PebbleOS AppDB/cache from the
            // known-good bundled image when 0.8.5 left imported bytes without a registry entry.
            new InstalledWatchfaceRegistry(context).clear();
        }
'''
if old not in text:
    raise SystemExit("prepareFiles marker not found")
text = text.replace(old, new)
marker = '''    private void prepareFrameEventPipe() throws IOException {
'''
method = '''    private boolean hasImportedRegistryMismatch() {
        WatchfaceRepository repository = new WatchfaceRepository(context);
        InstalledWatchfaceRegistry registry = new InstalledWatchfaceRegistry(context);
        try {
            for (WatchfaceMetadata metadata : repository.loadAll()) {
                if (metadata.isBundled()) {
                    continue;
                }
                File file = repository.fileFor(metadata);
                if (!file.isFile()) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(metadata.getUuid());
                    String fingerprint = InstalledWatchfaceRegistry.sha256(file);
                    if (!registry.isInstalled(uuid, fingerprint)) {
                        return true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUIDs are already excluded by normal PBW import validation.
                }
            }
        } catch (IOException ignored) {
            // A library read failure is reported by the UI; do not destroy a healthy SPI image.
        }
        return false;
    }

'''
if method not in text:
    text = text.replace(marker, method + marker)
qemu_path.write_text(text, encoding="utf-8")

build = build_path.read_text(encoding="utf-8")
build = build.replace('versionCode = 20', 'versionCode = 21')
build = build.replace('versionName = "0.8.6"', 'versionName = "0.8.7"')
build_path.write_text(build, encoding="utf-8")

readme = readme_path.read_text(encoding="utf-8")
readme = readme.replace(
    "Pebblehertz 0.8.6 repairs imported-PBW completion after the stricter 0.8.5 AppRunState validation.",
    "Pebblehertz 0.8.7 repairs imported-PBW completion and automatically recovers partial imports left by 0.8.5."
)
anchor = "### 0.8.6 imported PBW completion repair\n"
section = (
    "### 0.8.7 stale imported-app recovery\n\n"
    "On startup, Pebblehertz compares every stored imported PBW with the Android registry that "
    "mirrors PebbleOS SPI flash. If 0.8.5 transferred an app but timed out before recording it, "
    "the runtime restores the known-good bundled SPI image once. Imported PBW files remain in the "
    "locker and reinstall normally; clearing Android app data is not required.\n\n"
)
if section not in readme:
    readme = readme.replace(anchor, section + anchor)
readme_path.write_text(readme, encoding="utf-8")

print("Applied Pebblehertz 0.8.7 stale import recovery")
