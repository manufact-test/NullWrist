#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleQemuProcess.java"
text = path.read_text(encoding="utf-8")
text = text.replace(
    '    private static final String ASSET_ROOT = "pebble/basalt/";\n',
    '    private static final String ASSET_ROOT = "pebble/basalt/";\n'
    '    private static final String SPI_RECOVERY_PREFERENCES = "pebble_spi_recovery";\n'
    '    private static final String KEY_085_RECOVERY_COMPLETED = "recovery_085_completed";\n'
)
old = '''        boolean existingSpiFlash = spiFlash.isFile() && spiFlash.length() > 0;
        boolean recoverStaleImportedApps = existingSpiFlash && hasImportedRegistryMismatch();
        copyAsset(ASSET_ROOT + "qemu_micro_flash.bin", microFlash, true);
'''
new = '''        boolean existingSpiFlash = spiFlash.isFile() && spiFlash.length() > 0;
        boolean recoveryCompleted = context.getSharedPreferences(
                SPI_RECOVERY_PREFERENCES,
                Context.MODE_PRIVATE
        ).getBoolean(KEY_085_RECOVERY_COMPLETED, false);
        boolean recoverStaleImportedApps = existingSpiFlash
                && !recoveryCompleted
                && hasImportedRegistryMismatch();
        copyAsset(ASSET_ROOT + "qemu_micro_flash.bin", microFlash, true);
'''
if old not in text:
    raise SystemExit("recovery start marker not found")
text = text.replace(old, new)
old_end = '''        if (!existingSpiFlash || recoverStaleImportedApps) {
            // Preserve PBW files in the Android library, but rebuild PebbleOS AppDB/cache from the
            // known-good bundled image when 0.8.5 left imported bytes without a registry entry.
            new InstalledWatchfaceRegistry(context).clear();
        }

        closeFramebufferReader();
'''
new_end = '''        if (!existingSpiFlash || recoverStaleImportedApps) {
            // Preserve PBW files in the Android library, but rebuild PebbleOS AppDB/cache from the
            // known-good bundled image when 0.8.5 left imported bytes without a registry entry.
            new InstalledWatchfaceRegistry(context).clear();
        }
        context.getSharedPreferences(SPI_RECOVERY_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_085_RECOVERY_COMPLETED, true)
                .commit();

        closeFramebufferReader();
'''
if old_end not in text:
    raise SystemExit("recovery end marker not found")
text = text.replace(old_end, new_end)
path.write_text(text, encoding="utf-8")
print("Guarded 0.8.7 SPI recovery as a one-time migration")
