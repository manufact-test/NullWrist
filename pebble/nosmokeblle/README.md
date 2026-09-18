# nosmokeblle

Standalone quit-smoking tracker for Pebble.

Targets: aplite, basalt, chalk, diorite, emery.

Current branch contains the first functional watch-side implementation:
- EN/RU locale detection
- persistent quit timestamp
- cigarettes/day and money/day data model
- smoke-free timer
- avoided cigarettes
- money saved
- milestones
- relapse confirmation
- best streak / attempts / lifetime accumulation foundation

Build:
```bash
pebble sdk install 4.17
pebble build
```
