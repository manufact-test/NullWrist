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

## 2026-10-26 — Android UART2 log (10), confirmed regression

The device exported log shows `service_filesystem: Couldn't resolve last written pg` repeatedly, `Found 0 apps. Next ID: 1`, then full Comic Watchface and Pixel Miner installations. Pixel Miner launches and repeatedly faults `Stack overflow [task: App <Pixel Mine]`; its `game_load` returns `E_DOES_NOT_EXIST` each time. This is *not* a standalone proof of corruption: upstream PFS documents the missing last-written marker as possible immediately after fresh formatting. However `Found 0 apps` must be compared with the previous known SPI/AppDB and verified app-UUID registry.

Root code risks confirmed: (1) QEMU was killed by `Process.destroy()` after app STOP, without a QMP quit or host flash synchronization evidence; (2) `EmeryAssets.stage()` auto-created missing SPI even after prior installs; (3) guest apps crash after RUNNING_STABLE and never successfully persist score; (4) local ledger is not a guest AppDB oracle. Fix strategy: fail closed on missing previously staged SPI, preboot and post-clean-stop 32MiB checkpoints, QMP quit-before-SIGTERM, per-boot hashes, no silent restore/format, and targeted 3.x stack hardware A/B. Full score conservation/5m fault-free Titan hardware test remains REQUIRED.

## 0.8.1 SPI protection and targeted hardware A/B

Diagnostic source is real Titan 2 UART2 log (10): on initial boot AppDB=0 and PFS prints missing last-written page marker; repeated Pixel Miner stack guard faults follow. The PFS warning is not sufficient proof of flash corruption after a fresh format. No claim that the numeric pixel score is preserved.

Defensive source changes: QMP `quit` and wait for exit before SIGTERM fallback, preboot and clean-exit 32 MiB SPI snapshots in app-private `time2-emery/spi-checkpoints/`, explicit 32MiB size validation, persistent initialization marker refusing silent SPI reset after disappearance, per-boot flash SHA evidence, guest-fault monitor after initial RUNNING, unit tests for copy/restore invariants and fragmented fault messages. These are reliability mitigations, not a proof that Pixel Miner stack bug has disappeared.

Optional 4 KiB stack comparison is **experimental**, keeps MPU and 3.x segment limits unchanged. Exact candidate ELF SHA256 `cd096cc168aced92bd06da9bea656c0df0c6504a7f75cd3630bfdb3eec568de7` from CI run `40244566798`. The production official ELF remains `82c040d2ec3fe98963289aca07c31493e56d9ee6a323d07f3179cd17d65b4295`. `time2/scripts/prepare_emery_stack_candidate_apk.py` converts a checksum-verified official unsigned/debug Android lab APK into a separately unsigned candidate; private original starter PBWs and certificate are added later outside GitHub. Changing app firmware must never overwrite installed user's SPI.

Gate requires on same Titan 2 and same backed-up SPI (1) official 2KiB vs 4KiB candidate crash-frequency and 5-minute pixel count; (2) app-run-state after 60s, not just initial 10s; (3) guest stop with QMP clean exit, cold boot and exact pixel count; (4) file hashes and flash boot record; (5) recovery from backup after forcibly terminated QEMU. Failures: retain artifacts, do not erase flash or ship stable build.
