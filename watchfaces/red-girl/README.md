# Red Girl

Pixel-art watchface for Pebble Time 2 / emery based on the approved red-orange design.

## Dynamic data
- Time and date: native Pebble clock
- Watch battery: native Pebble battery service
- Heart rate: Pebble Health API when available
- Phone battery: PebbleKit JS bridge
- Weather: message key `2` is reserved and already rendered; wire any preferred provider into `src/pkjs/index.js`

## Build

```bash
cd watchfaces/red-girl
pebble build
```

The `.pbw` will be created in the watchface build directory by the Pebble SDK.
