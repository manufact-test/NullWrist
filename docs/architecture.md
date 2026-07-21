# Architecture

## Activities

### `MainActivity`

The launcher activity serves two roles:

- on Android's default display it shows the library and import interface;
- on any non-default display it immediately opens `RearDisplayActivity` in the same display context.

This lets the Titan 2 SubDisplay launcher open the normal app icon without requiring a private Unihertz API.

### `RearDisplayActivity`

A fullscreen passive surface that:

- hides system bars where Android permits;
- consumes all touch events;
- ignores the back button;
- contains no selector or settings;
- will eventually host the Pebble framebuffer view.

## Data layer

`WatchfaceRepository` copies debug-bundled watchfaces into app-private storage and stores imported `.pbw` files in the same directory. `PbwParser` reads only the exact `appinfo.json` entry and never extracts archive paths.

Current import protections:

- 20 MB package limit;
- 1 MB `appinfo.json` limit;
- ZIP content is streamed rather than extracted;
- package must declare `watchapp.watchface=true`;
- files remain in app-private storage.

## Runtime boundary

The intended native boundary is:

```text
PBW package
  -> PebbleOS/QEMU runtime (NDK process or native library)
  -> dirty framebuffer callback
  -> direct byte buffer
  -> Android SurfaceView / OpenGL texture
  -> Titan 2 rear display
```

The Android UI must never expose arbitrary JNI access to Pebble code. Network, location and PebbleKit JS support will be separate capability adapters and disabled by default.

## MVP runtime target

The first executable target is Basalt:

- 144 × 168 framebuffer;
- 64-color graphics;
- native C watchfaces;
- system clock;
- packaged resources;
- no phone JavaScript, weather, health or timeline.
