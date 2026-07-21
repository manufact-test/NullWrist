# Compatibility

Pebblehertz is currently a focused Unihertz Titan 2 beta. A PBW importing successfully does not always mean every phone-side feature used by that watchface is implemented.

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

Pebblehertz uses the official Pebble SDK 4.17 Basalt firmware and a Basalt-only QEMU machine.

## PBW compatibility

### Best compatibility

Standalone Pebble Time watchfaces that render entirely inside PebbleOS and do not need phone-side JavaScript, configuration pages or online services.

Typical examples:

- digital and analogue clocks;
- animated watchfaces whose resources are bundled inside the PBW;
- faces that only read time, date and Pebble battery state;
- faces with no companion-app configuration.

### Partial or missing compatibility

The following features are not implemented in 0.8.9:

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

Pebblehertz is currently watchface-focused. Apps and games that require the original Pebble hardware buttons, accelerometer gestures or interactive phone services are not considered supported even when their package can be parsed.

## Runtime modes

### 0.8.9 silent runtime

The current beta uses a notification-free Android service. It is visually clean, but aggressive Android or Titan 2 process management can still stop it after clearing recent apps or under memory pressure.

### Planned 0.8.10 runtime

- **Reliable Always-On** will become the default foreground-service mode.
- A minimal persistent Android notification will be the trade-off for stronger process survival.
- **Silent Mode** will remain available as an explicitly less reliable option.

## Titan 2 setup

For the best current result:

1. exclude Pebblehertz from Android battery optimization;
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

Use the repository's bug report form. Diagnostic export is planned for 0.8.10.
