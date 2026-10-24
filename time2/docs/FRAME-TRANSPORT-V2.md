# Emery v2 framebuffer / QMP host ABI (implementation milestone: partial)

This is the planned private Android-QEMU transport for `200 x 228` Emery screen output. **The native QEMU exporter is not yet integrated; the Android parser and command transport are implemented but not connected.**

## Frame format
- Path provided through `PEBBLE_EMERY_FB_PATH` environment variable; must be inside `time2`'s private files.
- Little-endian 64-byte header, followed by exactly 200 × 228 × 4 bytes of ARGB8888.
- Header 32-bit fields at byte offsets 0,4,8,12,16,20,24: magic `0x50424642` (PBFB), version `2`, width `200`, height `228`, stride `200` in **pixels**, format `2` (ARGB8888), sequence `N`; byte offsets 28..63 reserved zero.
- QEMU writer increments sequence to odd *before* pixel changes and to even after all writes, with release semantics. Android reader validates sequence before/after copying.
- QEMU should publish pixels already corrected for Pebble LCD colors, backlight and light changes; do NOT assume raw GColor8 is final UI.
- Optional FIFO `PEBBLE_EMERY_FB_EVENT_PATH` signals one byte per updated frame; reader must also cope with missed events.

## QMP
- Socket: Unix domain path entirely inside the app private directory; QEMU launcher should specify `-qmp unix:<path>,server=on,wait=off`.
- Only the emulator worker thread owns QMP, reads the greeting, issues `qmp_capabilities`, then serializes subsequent requests.
- Touch from Android maps to `input-send-event` `abs x/y` and `btn left` using 0..32767 QEMU absolute coordinates.
- Virtual Back/Up/Select/Down use `send-key` qcodes left/up/right/down respectively. Query/error handling is mandatory; do not silently treat a failed QMP command as a successful touch.
- Android touch probe still has NO BACKEND attached. No fake active/connected state.

## Testing
- Unit: forged v2 frame with pixel 0xFFFF6D00, rejected odd/torn/unknown format, out-of-bounds touch; CI `time2` Gradle tests.
- Integration to add: headless Ember QEMU actually exports frames while firmware boot + touch/launcher causes sequence/pixel changes, and clean restart preserves private flash.
- On-device: verify Titan2 own SubScreen touch routing and SDK frame/rotation after real native QEMU compiled.
