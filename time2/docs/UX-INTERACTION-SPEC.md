# NullWrist Time 2 — Front/Rear UX contract

**Status:** Design decision / 2026-10-24. Describes target UI, not shipped implementation.
**Branch:** `feature/time2-standalone`. **Scope:** `time2/` only.
**Do not modify:** legacy `app/`, `main`, 0.8.12 release, signing, legacy storage. Do not merge PR #21 automatically.

## Product principle

The main Titan 2 screen is a convenient, minimal **companion/controller**.
The Titan 2 **rear screen is the watch**: a genuine, unmodified 200×228 PebbleOS Emery framebuffer, letterboxed to rear display, with touch preserved. Avoid fake watch screens and any always-visible Android header/footer/status/buttons on the rear. Orange branding belongs on the front screen and launcher icon, not over the emulated watch LCD.

### Visual identity

- Near-black graphite `#08090D`; hot orange `#FF6D00`; orange active accent `#FF9C1F`; soft white `#EBECE8`; muted hairline `#232428`.
- Pixelated `NULLWRIST` header/identity remains (5×7 wordmark and square glyph motif), with short readable labels for everyday controls. Avoid all-caps paragraphs, repeated version labels or enormous stack of lab widgets.
- Use clear contrast, large touch targets (minimum 48dp), scrollable main screen. Keep English strings localized later without changing layout.

## Front screen — single-screen concept

1. **Header**: `NULLWRIST` pixel mark, smaller `TIME 2`, single live engine indicator with truthful states: **Ready**, **Starting**, **Not running**, **Needs attention**, **Unavailable**. Never paint a green/connected success state just because a file was imported.
2. **Hero — active watchface**: a rectangular **live snapshot only when actual guest frames exist**; else placeholder that explicitly says preview unavailable. Short watchface name and status, e.g. `Royale • Installing`. Primary button `Open rear display` / `Start PebbleOS` depending on actual runtime availability. For Titan rear use official SubScreen assignment/instructions, not invented Android display APIs.
3. **Library actions**: visible `+ Import .pbw`, secondary `Apps` or tabs `Watchfaces | Apps` only if both are present. Simple vertically scrollable rows: visual thumbnail when verified, display name, `Installed/Ready/Installing/Failed/Imported`, action `Use` or `Install`, no raw UUID/platform displayed to normal users. A local import is NOT an OS install.
4. **Settings** accessible from a small gear in the header, not a long debug-heavy home screen: engine behavior, display orientation, optional companion permissions (introduced only when implemented and relevant), device instructions, virtual watch memory backup/restore.
5. **Advanced / Diagnostics** behind Settings: export QEMU + host + guest UART logs; runtime state; software version; errors; backup/restore. Keep every current diagnostic path available through the design change until Royale and M3 bugs are solved.

Empty states should be helpful and compact, not a wall of developer text. On installation failure show short actionable error and allow Export diagnostics and retry. No false progress/completion.

### Front wireframe (information hierarchy, not pixels)

```text
┌───────────────────────────────────┐
│  NULLWRIST             TIME 2   ⚙ │
│  ● PebbleOS ready                 │
│                                   │
│  ┌─────────────────────────────┐  │
│  │ [ACTUAL 200x228 FRAME]      │  │
│  │ Royale     • Active         │  │
│  └─────────────────────────────┘  │
│  [    Open rear display        ]  │
│                                   │
│  WATCHFACES          + Import PBW │
│  [ face preview ] Royale  Active  │
│  [ face preview ] Other   Install │
│                                   │
│  APPS                        View │
└───────────────────────────────────┘
```

The exact copy/state shown must reflect the runtime's measured facts. In lab mode, preserve explicit lab badge under Advanced. No forced persistent notification/network permissions merely for this UI.

## Rear screen — pure PebbleOS

- Default: show the **real QEMU framebuffer edge-to-edge within the emulator 200×228 aspect area**. No permanent NullWrist logo, `EMERY LAB`, `200×228`, diagnostic footer, orange outline, tap coordinate dot, or button row.
- Set immersive flags on rear Activity where Android permits; account for Titan 2 SubScreen/physical display bounds without assuming Android system gestures can always be blocked.
- Never redraw fake Pebble menu or synthetic watchface on top of real OS. Emulator itself renders the launcher, watchapps and built-in settings.
- Native touch/move/swipe from the virtual LCD continues to be forwarded via the current `ViewportMapper` and QMP touch bridge. Ignore touches outside mapped LCD, do not create unintended Android buttons.

### Triple tap = original Pebble center/Select button

**User gesture**: three short taps with one finger anywhere inside the rendered Pebble screen.
**Action**: inject exactly one `EmeryQmpCommands.Button.SELECT` command through the already connected, serialized runtime/QMP input path. PebbleOS should itself open its launcher on the watchface (and apply its normal Select behavior elsewhere). Do not inject a fake Android menu or three tap events.
**Timing (initial tuneable design)**: each tap <= 220ms, gaps <= 330ms; entire triple sequence <= 900ms; movement within tap-slop threshold (e.g. 12dp). Require each tap to complete with UP; multi-touch, swipes, long presses, out-of-bounds or cancellation reset the triple-tap candidate.

**Critical forwarding rule**: do not let the first two taps trigger arbitrary PebbleOS actions if they turn into a triple tap. Hold a short candidate tap sequence in a bounded recognition buffer. If it resolves to a triple tap, discard these synthetic touch taps and send **only SELECT**. If the sequence is 1 or 2 taps, replay them *in the correct order* to the guest after the recognition timeout. Swipes and drags must go through to QMP promptly, with DOWN/MOVE/UP consistency. Avoid indefinitely buffering gestures or delaying emulated swipes. Tune the 330ms delay based on physical Titan 2 usability, and test both one-/two-tap native interactions.

Handle rapid screen changes, Activity pause/destroy, QMP disconnect/reconnect, and cancellation by flushing/cancelling the buffer safely; never create a stuck guest BTN-left DOWN. Suppress duplicate center-button dispatch. Optional one brief Android haptic on successful gesture only after user consent/settings if needed; no overlay.

### Access to other virtual buttons

The rear has **no permanent 4-button toolbar**. Because genuine Pebble has Back/Up/Select/Down hardware buttons and some watchapps require them, retain a fallback via front-screen `Watch controls` in Settings (or a temporarily summonable on-rear accessibility layer **only if real-world testing proves necessary**). Do not invent competing rear gestures before testing touch navigation. The triple-tap center shortcut is the one guaranteed product gesture.

## Acceptance checks

- Front UI remains useful at Titan 2 resolution; single active-face hero, import, list, truthful install/runtime states, Settings; all existing diagnostics/backup recoverable under Advanced.
- Launch from Titan 2 SubScreen displays only the true Emery frame. Scaling does not filter/smear pixels and does not stretch the 200×228 aspect.
- From genuine Pebble watchface, three quick rear taps issue exactly one QMP SELECT and the **native Pebble launcher** appears.
- One/two tap sequences retain guest behavior, swipes remain responsive; outside/multi-touch/long-tap do not spuriously select.
- Triple tap inside guest launcher sends normal Select key behavior, not a hard-coded `home/menu` shortcut.
- No UI asserts successful install for a merely imported PBW. Royale/other package crashes remain diagnosable after UX work.
- Cold boot, reopen, lock/unlock and flip tests on actual Titan 2. No regression to 0.8.12, `main`, or legacy data.
- Do **not** call this UX implemented by adding this document; code and device verification are separate gates.

## Implementation sequence

1. Refactor front screen only (status/hero/library/settings and Advanced diagnostics), keep current services working; no data migration.
2. Extract rear production shell from current `RearActivity` lab controls; keep a developer-only diagnostic mode reachable from main screen.
3. Introduce a unit-testable `TripleTapSelectRecognizer` with bounded tap buffering and cancellation; wire SELECT only through existing `EmeryRuntime.press`, retain QMP serialization.
4. Host-side unit tests for gesture sequences, viewport, disconnect and app pause; then real Titan 2 testing; tune timing and provide diagnostic logs.
5. Resolve outstanding Royale watchface guest hang/M3 separately; no premature launch-stable claim.

**Reminder:** `time2/README.md` and earlier `time2/MASTER.md` sections contain historical lab checkpoint text and may be stale. Trust the code and physical log evidence for feature status.
