# NullWrist roadmap

This roadmap reflects the current project direction and community feedback. Version numbers describe the intended order of work, not guaranteed release dates.

## Product principles

1. Reliability comes before feature count.
2. The default experience should work without root access or manual recovery.
3. Imported PBWs should clearly explain what they need instead of failing silently.
4. Pebble compatibility work should improve many watchfaces at once rather than patching individual faces forever.
5. New phone models are supported only when a physical test device is available.

## Shipped

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

## Planned

### 0.9.0 — Personal Face

A native NullWrist mode for users who want a personal rear-screen clock without creating a PBW.

- Static image backgrounds.
- Animated GIF backgrounds.
- Digital time overlay.
- Clock position, size, alignment and color controls.
- Optional dimming or gradient behind the time.
- Live preview on the main screen.
- Save and switch between multiple personal presets.
- Power-aware animation limits for GIFs.

This mode will be separate from PebbleOS so it stays simple and does not depend on PebbleKit configuration support.

### 0.10.0 — PebbleKit Compatibility

This milestone targets the largest remaining compatibility gap for imported Pebble watchfaces.

- Phone-side JavaScript runtime for PBW packages.
- Pebble AppMessage bridge between Android and PebbleOS.
- Watchface configuration pages.
- Persistent configuration values.
- Controlled network requests.
- Initial weather-provider integration.
- Clear permissions and per-watchface network controls.

### 0.11.0 — Phone Data

- Health Connect step data.
- Heart-rate data when another compatible app or device writes it to Health Connect.
- Selected Android notifications.
- Music metadata and basic controls.
- Calendar and next-event data.
- User-controlled privacy switches for every data source.

## Later / exploratory

### Additional rear-screen phones

NullWrist can theoretically be adapted to other Android devices with a secondary display, but each device has different display routing, process management and firmware behavior.

A port starts only when:

- a physical device is available for repeated testing;
- the rear display can be addressed reliably;
- the owner or community can help test releases;
- ongoing maintenance is realistic.

### Broader Pebble platforms

Aplite, Chalk, Diorite and Emery are not currently planned for the near-term roadmap. Basalt remains the primary platform until the Titan 2 experience is stable.

## Not currently planned

- Root-only installation flows.
- Pretending unsupported PBWs are fully compatible.
- Shipping unverified third-party PBWs without provenance and pinned hashes.
- Supporting devices solely from screenshots or remote guesses.

## How priorities change

Priorities may move when community testing reveals a serious reliability problem, when a requested feature unlocks many watchfaces at once, or when a suitable test device becomes available. Feature requests should explain the user problem and provide a concrete example rather than only naming a technology.
