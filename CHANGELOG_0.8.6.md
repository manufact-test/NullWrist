# NullWrist 0.8.6

## Imported PBW completion repair

- AppRunState responses are no longer cleared between short polling windows.
- Imported AppFetch completion can take up to 30 seconds without a false rollback.
- Status polling waits two seconds per response so delayed PebbleOS callbacks remain observable.
- A RUN command is retried only after AppFetch has had five seconds to complete its own launch transition.
- Exact running UUID validation remains enabled, so old-face framebuffer ticks cannot produce a false ACTIVE state or a wrong thumbnail.

## Regression addressed

NullWrist 0.8.5 could successfully transfer an imported PBW but delete the delayed AppRunState confirmation from its own endpoint queue. The 12-second timeout then restored the previous watchface even though the PBW transfer had completed.
