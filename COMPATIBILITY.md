# Compatibility

NullWrist is currently a focused Unihertz Titan 2 beta. A PBW importing successfully does not always mean every phone-side feature used by that watchface is implemented.

## Supported device

| Device | Status |
|---|---|
| Unihertz Titan 2 | Primary and physically tested target |
| Other Android phones with rear displays | Not currently supported |
| Ordinary single-display Android phones | Development/testing only; no intended rear-display experience |

## Supported Pebble platform

| Pebble platform | Status |
|---|---|
| Basalt / Pebble Time | Supported target |
| Aplite / original Pebble | Not supported |
| Chalk / Pebble Time Round | Not supported |
| Diorite / Pebble 2 | Not supported |
| Emery / Pebble Time 2 | Not supported |

NullWrist uses the official Pebble SDK 4.17 Basalt firmware and a Basalt-only QEMU machine.

## PBW compatibility

### Best compatibility

Standalone Pebble Time watchfaces that render entirely inside PebbleOS and do not need phone-side JavaScript, configuration pages or online services.

Typical examples:

- digital and analogue clocks;
- animated watchfaces whose resources are bundled inside the PBW;
- faces that only read time, date and Pebble battery state;
- faces with no companion-app configuration.

### Partial or missing compatibility

The following features are not implemented in 0.8.11:

- PebbleKit JS;
- AppMessage communication with Android;
- watchface configuration webpages;
- weather and other network data;
- phone notification forwarding;
- Health Connect, steps or heart-rate data;
- location data;
- phone-side background workers.

A watchface that depends on one of these may still install and render its basic layout, then show a connection error, stale data, placeholders or a configuration warning.

### Pebble apps and games

NullWrist is currently watchface-focused. Apps and games that require the original Pebble hardware buttons, accelerometer gestures or interactive phone services are not considered supported even when their package can be parsed.

## Runtime

NullWrist 0.8.11 uses one continuous Android foreground service with watchdog recovery. It restores the selected runtime after phone reboot and application updates when always-on operation is enabled. The persistent notification is intentionally silent and non-interactive.

Android Force stop and the Titan 2 App blocker remain terminal system actions and require the user to open NullWrist again.

## Titan 2 setup

For the best current result:

1. exclude NullWrist from Android battery optimization;
2. allow background operation;
3. allow it through DuraSpeed;
4. exclude it from the Titan 2 App blocker;
5. configure the secondary display so it remains available for the watchface;
6. avoid force-stopping the application.

## Reporting a watchface problem

Include as much of the following as possible:

- watchface name and version;
- where the PBW came from;
- Pebble UUID if visible;
- whether the import completed;
- whether the face initially rendered;
- exact error text;
- how long it worked before failing;
- whether the problem appeared after locking the phone, clearing recent apps, charging or losing network access;
- screenshot or short video.

Use the repository's bug report form and include the exact reproduction sequence.
