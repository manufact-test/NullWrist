# NullWrist Time 2 — visual system

Visual direction: dark technical object, sharp retro-computing typography, confident **HOT ORANGE**. New brand variation: NOT a generic mock Pebble screen, and NOT a rebrand of the legacy APK.

## Palette
- Base: `#08090D` (almost-black graphite; OLED-like without crushing LCD readability).
- Primary: `#FF6D00` (hot orange, not a washed-out pastel).
- Highlight / active pixel: `#FF9C1F`.
- Main text: `#EBECE8`.
- Hairline / inactive: `#232428`.
- Error: `#FF4040`. Green reserved only for actual verified runtime / app confirmation.

## Pixel identity
- Wordmark: custom bitmap 5x7 glyphs in `PixelWordmarkView`, drawn with hard-edged square cells and no font dependency.
- Icon: `res/drawable/ic_nullwrist_time2.xml` pixelated N, orange and amber on black.
- Flat corners and square controls; dense monospace technical labels in diagnostics but simpler user-visible labels for release.
- Retro details should not obstruct the REAL 200x228 Pebble framebuffer: keep in-app chrome around PebbleOS, not drawn over OS content.
- Logo uses the existing NullWrist name. The suffix 'Time 2' identifies this separate Emery engine, not a new brand.

## Front vs rear
Main phone screen: library of apps + faces, device/service permissions with plain explanations, live state that only says connected on measured QEMU/OS handshake, install status and retry controls.

Rear screen: after working emulator is booted, original OS pixels (no anti-aliased scaling), touch forwarded 1:1, virtual Back / Up / Select / Down buttons in discreet, revealable overlay. Android mandatory gestures are not promised to be suppressible.

Until M1 native QEMU is integrated, label preview **NOT AN ACTIVE PEBBLEOS SESSION**.
