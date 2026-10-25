# P0-C/P1-D: Pixel Miner and persistent storage regression (2026-10-26)

Status: investigation / open. Original private Pixel Miner PBW SHA256
`2b36f712b2716824c70f086076df497e8635477184532c38ab52562dea57400f`,
UUID `808903cb-fa37-4247-b012-7dfe5c9269e1`, platform Basalt SDK
5.56, watchapp, no JS / worker required.

## Verified Titan 2 log
- Android installs 14296-byte executable and 34091-byte resources (INSTALL ACKs);
  `RUNNING_STABLE` was logged; therefore transport is **not** the blocker.
- Guest: `game_load save data returned E_DOES_NOT_EXIST` at new game startup.
- Guest then prints `Pixel Miner Done initializing` and repeatedly faults:
  `Stack overflow [task: App <Pixel Mine]`, `App fault` for its UUID.
- An absent save at the **first** boot is expected; repeated absence after
  playing does not prove broken storage while the app crashes before a normal
  exit. Verify save-write and subsequent read in an independent minimal PBW.
- `signal 15` from QEMU is the Android Activity's termination, not evidence of
  a normal in-guest game exit. Current Activity tears down guest on UI close.
- Official PebbleOS 3b69e4b8 uses 2KiB legacy 3.x app stack even on Emery;
  modern 4.x on Emery gets 4KiB. Candidate source-level 4KiB legacy 3.x
  experiment is opt-in; same segment, unchanged MPU and stack guards.

## Acceptance additions
1. PERSIST-1: prove `persist_write_int`, `persist_write_data` and
   `persist_read_*` across stop/relaunch, QEMU restart and UI transitions with
   a known-key, independent test app and preserved SPI; compare exact values.
2. PERSIST-2: Pixel Miner should not fault when idle/playing for >=5 min,
   then continue with saved score after graceful in-guest exit and relaunch.
3. PERSIST-3: repeated launch of the same installed UUID must **not**
   rewrite AppDB/PutBytes or reset per-app PFS. Verify over protocol traces.
4. PERSIST-4: the Activity's destroy/rotate/front–rear switch must not kill
   QEMU. The service must own the guest; only an explicit stop or recoverable
   controlled shutdown ends it. Save and verify SPI before app removal/updates.
5. PERSIST-5: user deletion remains honored (no silent re-seeding). Pixel Miner
   is seventh named starter in private signed builds; public CI never bundles
   its PBW bytes. SHA256/UUID pin is public metadata only.
6. PERSIST-6: physical Titan 2 must independently verify all the above;
   Linux results alone never imply hardware acceptance.

No claim of Pixel Miner save correctness or a production stack fix before
the tests. Do not patch the user’s PBW binary or disable MPU.

## 2026-10-26 — фактическое испытание Linux, промежуточно

- Источник Pixel Miner: предоставленный владельцем PBW, проверен SHA256, платформа Basalt, app UUID совпадает.
- `coredevices/PebbleOS@3b69e4b8` имеет 2 KiB стек для legacy 3.x и 4 KiB для SDK 4.x на Emery. Опциональная проверочная прошивка 4KiB была собрана из официального source pin с единственным изменением layout legacy stack, сохранёнными MPU/guard и прежним RAM-segment.
- Linux CI кандидат: https://github.com/manufact-test/NullWrist/actions/runs/40244566798 — PASS, включая официальные QEMU smoke; это **экспериментальный**, не выпускаемый firmware.
- С этим кандидатом Pixel Miner в Linux QEMU показал реальную графику/счётчик 60s, оставался RUNNING, без `Stack overflow` или `App fault` в UART2. Однако 2KiB официальный baseline во время ограниченной локальной интерактивной проверки не воспроизвёл аварии, поэтому причинность патча не доказана. Не заменять действующую официальную Android прошивку без сравнения и физического теста.
- Linux QEMU эксперимент с кандидатом 4KiB: первая игра пишет `game_load ... E_DOES_NOT_EXIST`, после касаний и штатного `AppRunStateStop` SPI изменяется; последующий cold start с той же SPI и AppRunStateStart без PBW передачи успешно запускает игру без повторного `E_DOES_NOT_EXIST`. Это обоснованное свидетельство создания persist-записи, **но не численного сохранения score**. Сами PBW и личные файлы остаются вне публичного GitHub.
- Рабочая ветка теперь содержит Activity-независимый foreground emulator service, сериализованный guest protocol, быстрый запуск без BlobDB overwrite, ledger fingerprint и мягкую остановку приложения при Stop. Реальный Titan 2 ещё не проверял эту новую архитектуру. FGS/type specialUse и энергопотребление требуют тестов.
