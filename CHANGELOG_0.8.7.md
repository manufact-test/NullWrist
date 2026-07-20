# Pebblehertz 0.8.7

## Imported PBW repair

- Keeps delayed AppRunState responses instead of clearing them between polls.
- Allows up to 30 seconds for PebbleOS AppFetch to finish and confirm the exact running UUID.
- Retries RUN only after the fetch UI has had time to complete.

## One-time recovery from 0.8.5

Version 0.8.5 could finish writing a PBW into PebbleOS SPI flash but time out before recording that installation in the Android registry. On the first 0.8.7 runtime start, Pebblehertz detects this specific mismatch and restores the known-good bundled SPI image once.

Imported PBW files remain in the watchface locker and install normally when selected. Android application data does not need to be cleared. A migration marker prevents repeated SPI resets on later launches.
