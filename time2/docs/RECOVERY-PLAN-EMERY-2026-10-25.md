# NullWrist Time 2 — техническое задание на восстановление и доведение эмулятора
**Дата исследования:** 2026-10-25  
**Статус:** аудит и план; **не реализовано**, не подтверждено на Titan 2.  
**Рабочая ветка:** \`feature/time2-standalone\`  
**Приоритет:** P0 — загрузка/стабильный запуск PBW, затем надёжность, затем функции телефона и UX.  
**Нельзя менять:** корневой \`app/\`, старый NullWrist 0.8.12, \`main\`, выпущенные APK, пользовательские данные старого эмулятора.

## 0. Ключевая находка и запрет на прежнюю стратегию

**Доказанный корень общего сбоя циферблатов** — старая сборка PebbleOS \`qemu_emery\`, закреплённая за upstream commit \`0f806feed7b4bf7a2358119c667a09a7c9b961ff\` от 21 октября 2026. В \`fw/process_management/app_manager.c\` и \`worker_manager.c\` ядро считывает закрытые переменные **после** перехода задачи ARM Cortex-M33 в unprivileged mode. Программа вызывает \`DACCVIOL\` до входа в код пользовательского PBW.

Сам PebbleOS уже исправил это **22 октября 2026**: \`3b69e4b894d299c98ace176f3a5e40cdb786e905\`, \`process_management: read what the runtime needs before dropping privileges\`. Код теперь заранее извлекает \`app_md\` и \`stack_top\` для обычного приложения **и** worker. Источник: https://github.com/coredevices/PebbleOS/commit/3b69e4b894d299c98ace176f3a5e40cdb786e905 .

Предыдущий локальный бинарный патч \`time2/scripts/patch_emery_guest_entry.py\` из 0.7.0 заменил одно чтение, но **не** последующее \`ldr r0,[r4]\`. Поэтому исходная ошибка \`PC=0x6ED62; MMFAR=0x20012D7C\` из 0.6.1 превратилась в \`PC=0x6ED64; MMFAR=0x20012D80\` из 0.7.0. Обе инструкции находятся внутри \`prv_app_task_main\`, а не в коде циферблатов. Сборка 0.7.0 **не исправляет** эту проблему; считать её стабильной запрещено. Никаких дальнейших правок произвольных байтов ELF и тем более отключения MPU ради маскировки ошибки.

**Непосредственное решение P0:** пересобрать upstream PebbleOS из зафиксированного ревизионного состояния **не раньше** \`3b69e4b8\`, использовать исходное исправление для **обоих** менеджеров, собрать и проверить upstream \`qemu_emery\` на Linux с PBW и только затем пересобрать/упаковать Android ARM64-хост. Совместимость ревизий QEMU и SDK должна быть явно проверена; не переходить на плавающий \`main\` без lock-файла.

## 1. Что установлено фактами

### 1.1. Матрица пользовательских версий

| Версия | Наблюдение | Вывод |
|---|---|---|
| 0.4–0.5 | настоящая PebbleOS выводит кадры 200×228; импорт/установка неполные | M1 (boot/frame) частично подтверждён; M3 — нет |
| 0.6.0 | INSTALL отвергает реальный cookie \`1264670356\` | ошибка проверки протокола на хосте |
| 0.6.1 | загрузка бинарника/ресурсов завершается, \`Comic Watchface\` отмечен как RUNNING, затем аварийный guest fault | короткий RUNNING не равен устойчивой работе |
| 0.7.0 | \`Crush 'Em\`, \`Comic Watchface\`, \`BeerClock\` показывают "not responding"; диагноз Cortex-M33 MPU остается | адрес первой аварии лишь сместился на 2 байта |
| Текущий UI | на главном экране отображается старый snapshot и статус "installed"/"session open" после guest crash | состояния экрана и фактического guest процесса не синхронизированы |

Пользовательский лог \`nullwrist-time2-emery-qemu.log (7).txt\` — Crush 'Em:
- QMP+frame online; BlobDB/AppFetch проходит.
- executable **4692/4692**, resources **9104/9104**, оба INSTALL ACK получены.
- "PBW bytes committed" → "launch NOT confirmed" **за 95 мс**, но текущий код должен ожидать до 12 секунд. В \`awaitRunning()\` имеются одновременно временной предел и счетчик попыток: 7 быстрых нерелевантных состояний могут преждевременно завершить ожидание.
- Декодированный raw UART2 Pulse содержит \`Memory Management Fault\`, \`CFSR=0x82\`, \`DACCVIOL\`, \`MMFAR=0x20012D80\`, \`PC=0x6ED64\`, \`Task: App <Crush 'Em>\`, \`Watchface crashed twice in 33ms\`.
- \`libnullwrist_emery_qemu.so: terminating on signal 15\` — ожидаемое выключение дочернего QEMU при закрытии Activity; не доказательство native host crash.

В предыдущем журнале Comic Watchface также полностью переносил бинарник и ресурсы, но падал при \`PC=0x6ED62\`, \`MMFAR=0x20012D7C\`. Ошибки локализованы машинным дизассемблированием реальных \`pebbleos.elf\`, извлечённых из APK 0.6.1/0.7.0. После устранения ошибки на входе возможны **другие** несовместимости: пока не доказано, что все шесть PBW полностью работают.

### 1.2. Шесть эталонных PBW пользователя

| PBW | UUID | Пакет / зависимости |
|---|---|---|
| Iridescency II | \`1e80e881-a883-45ed-8179-447f9e39668d\` | собственный \`emery/\`, отсутствует JS |
| Halcyon | \`c8e97ab8-7c12-45fb-975d-e85d5de61e8b\` | собственный \`emery/\`, есть \`pebble-js-app.js\` |
| Comic Watchface | \`2a854ad6-7690-4be1-a31d-9047ac7273e6\` | собственный \`emery/\`, есть JS, configurable/location/health |
| Crush 'Em | \`f1c75914-3529-4595-9584-74cb5dcbf1ee\` | только \`basalt/\` и \`chalk/\`, health |
| BeerClock | \`2aa4ecec-7f74-4e4c-abc0-aaf4b712f460\` | только Basalt/Chalk, configurable и JS |
| Essential | \`529d0607-d981-4496-b106-304d8ddcde71\` | Aplite/Basalt/Chalk/Diorite, health/location/configurable и JS |

Файлы сохранены пользователем и ранее проверены по SHA256 в \`time2/app/src/main/assets/starter-pbws/index.txt\`. Проверка ZIP/manifest/платформы означает **потенциальную совместимость**, а не готовность системных сервисов. Без JS/settings/weather/location циферблат должен хотя бы стартовать и показывать базовые часы, если его собственный код это допускает; для полной функциональности потребуются телефонные адаптеры. Старые Basalt лица могут работать в **Bezel Mode**, как описывает официальная документация Emery 2016.

### 1.3. Что перенести из старого NullWrist, не затрагивая его

Старый \`app/\` — **Basalt 144×168**, его успешные PBW **не доказывают** исправность Emery. Тем не менее взять за референс:
- \`PebbleAppInstaller\`: endpoint-ориентированное ожидание с фильтрацией UUID/cookie, busy-NACK retries, install→run, polling.
- \`PebbleProtocolLink\`: один serial owner, очереди по endpoint, предотвращение спутывания async/late ответов.
- \`InstalledWatchfaceRegistry\`: проверка содержимого (hash+UUID) и fast launch вместо реинсталляции.
- \`PebbleRuntimeService\` + \`PebbleQemuProcess\`: фоновый владелец процесса, frame observer, watchdog/backoff, восстановление, pause/resume независимые от экрана.
- \`WatchfaceRepository\` и UI: библиотека и корректное удаление, thumbnail, rollback на предыдущее лицо после неудачного выбора.

Стратегия: **портировать решения/тесты, а не слепо копировать Android-классы**; Basalt target, framebuffer 1-bit/цвет и legacy SPI/firmware останутся отдельно.

## 2. Источники и исходные ревизии

1. Official PebbleOS fix (оба менеджера): https://github.com/coredevices/PebbleOS/commit/3b69e4b894d299c98ace176f3a5e40cdb786e905
2. Official QEMU emulator documentation: https://developer.repebble.com/pebbleos/docs/development/qemu/
3. Official debugging: https://developer.repebble.com/pebbleos/docs/development/debugging/
4. Official platform/emulation coverage: https://developer.repebble.com/pebbleos/contributing/
5. Original Emery compatibility/bezel: https://developer.repebble.com/blog/2016/10/11/Emery-SDK-Beta/
6. Native simulator (as of 2026, Samsa can execute third-party ARM PBW in supported configurations; beware older stale docs): https://developer.repebble.com/pebbleos/docs/development/emulation/native_simulator/
7. Legacy NullWrist main: https://github.com/manufact-test/NullWrist/tree/main/app
8. Current isolated lab branch: https://github.com/manufact-test/NullWrist/tree/feature/time2-standalone/time2

Current \`time2/upstream.lock.json\` is obsolete for usable app execution: PebbleOS \`0f806feed...\` (Oct 21), QEMU \`8a0a7c6...\` branch \`pebble-10.1\`, SDK release \`0.1.11\`. Must lock a fixed matching new revision and SHA-256 native artifacts. Do **not** use \`latest\` dynamically inside reproducible CI.

## 3. Целевая архитектура

\`\`\`text
TITAN 2 (Android 16)
  MainActivity (companion: library, statuses, permissions, settings, logs)
  RearActivity (pure authentic emulator LCD; triple-tap -> SELECT)
            | callbacks / observation only
            v
  EmulatorService (foreground when required by Android; singleton runtime owner)
      |--- SessionManager (start/stop/recovery/watchdog/intent queue)
      |--- PackageManager (PBW metadata/install/launch/delete/rollback)
      |--- PebbleLink (UART1, single writer, endpoint queues, AppVersion)
      |--- CompanionAdapters (notifications/music/weather/calendar/phone JS later)
      |--- FrameTransport (native seq + 200x228 RGB frame, no stale render)
      |--- Diagnostics (host/QEMU/UART2, fault decoder, snapshots, version hashes)
            |
       app-private IPC --> pinned Android arm64 QEMU pebble-emery Cortex-M33
                              |
                        pinned upstream PebbleOS ELF (source rebuilt)
                              |
                    private persistent SPI flash (backups/versioned)
\`\`\`

**Invariant:** открытие, закрытие, переключение основного и заднего экранов **не должно** самовольно перезапускать гостевую PebbleOS или прерывать PBW. Как минимум один авторитетный источник состояния. No root, no public QMP port, private permission-limited sockets. Native PebbleOS renders native menus; do not emulate those menus on Android.

## 4. План работ и обязательные gates

### P0-A — пересборка исходной прошивки; устранить guest fault

**Задачи:**  
A1. Зафиксировать upstream commit, содержащий обе части \`3b69e4b8\`, SDK/QEMU и зависимости; обновить reproducible \`upstream.lock.json\`.  
A2. Пересобрать *из исходников* \`pbl configure --board qemu_emery; pbl build\` с source commit. Удалить из *рабочей сборки* вызов \`patch_emery_guest_entry.py\` после прохождения замены, но сохранить архив исторического эксперимента для аудита.  
A3. Проверить ELF \`prv_app_task_main\` и \`prv_worker_task_main\` дизассемблированием: **оба** глобальных указателя читаются до перехода в unprivileged. Verify ABI symbols, MAP file, toolchain, full input hashes.  
A4. Linux QEMU desktop integration: установить \`Iridescency II\` (без JS), \`Crush 'Em\` (Basalt compatibility), \`Comic Watchface\` (Emery+resources+JS). Проверить не менее **60 сек реальной отрисовки** каждого, app switch, restart with same persistent flash, no DACCVIOL/hardfault. Одновременно выгрузить console/UART2/stacktraces.  
A5. Проверить worker entry на минимальном тестовом PBW с worker.  
A6. Если upstream code crash остаётся, остановить Android UI работу, локализовать новый guest fault через GDB/dehash logs и открыть/проверить upstream issue. Не выпускать APK «почти исправлено».

**Gate A:** 3/3 эталонных PBW на Linux QEMU реально показывают содержимое и не падают при старте; оба privilege пути проверены; полные артефакты/логи/хэши сохраняются. Не путать с smoke tests launcher/touch.

### P0-B — независимая физическая проверка Android ARM64 QEMU

B1. Пересобрать APK в отдельной ветке с валидированной прошивкой и pinned native QEMU; firmware source provenance и лицензии в metadata.  
B2. На Android 16 Titan 2 пройти тот же тестовый набор 3/3 без падений и открыть/закрыть гостевое меню/Settings.  
B3. Запуск каждого PBW повторить с чистой тестовой SPI и с обновлённой SPI; проверить flash format/CRC, AppDB registry, сохранённые resources/UUID.  
B4. При любом guest fault сохранять сырые регистры, карту памяти, PC/LR, stack trace, символизацию, firmware build hash и fallback screenshot; не выводить просто "is not responding".  
B5. Не менять QEMU машину на Basalt ради видимости совместимости; Emery 200×228 остается отдельной системой.

**Gate B:** 3/3 PBW работоспособны **на физическом Titan 2**, скриншот/видео с реально изменяющимися часами, отсутствие guest faults. Без B нельзя объявлять устранённой проблему импорта/запуска.

### P0-C — протокол PBW и жизненный цикл приложения

C1. Единый installer state machine:
\`IMPORTED\` → \`QUEUED\` → \`APPDB_REGISTERED\` → \`FETCHED\` → \`BINARY_TRANSFERRED\` → \`RESOURCES_TRANSFERRED\` → \`INSTALL_ACKED\` → \`LAUNCHING\` → \`RUNNING_STABLE\` либо \`FAILED(reason)\`.  
C2. Перенести из legacy endpoint multiplexing, correlation tokens/UUID, busy retries, bounded total deadline + timeouts + explicit cancellation. Отдельные журналы ошибок \`metadata\`, \`transfer\`, \`resource\`, \`guest_launch\`, \`guest_crash\`. Никогда не принимать ACK другого transfer.
C3. Исправить \`awaitRunning()\`: ожидание должно быть привязано к настоящему elapsed time/deadline; семь быстрых нерелевантных AppRunState-событий **не** означают отсутствие запуска. RUNNING считается предварительным состоянием, затем минимум 10 секунд без crash/timeout, реальный изменяющийся framebuffer или отслеживаемый heartbeat. Confirmed \`UUID\` must match.  
C4. Устанавливать, переключать и удалять PBW на **живом guest**, не пересоздавая QEMU для каждой операции. Для уже установленной одинаковой версии — fast launch вместо полной установки.  
C5. Изменения UI/registry осуществлять **после** подтверждения из guest, а не после отправки пакета. Failed launch => preserve previous working face, rollback/guest default and explanatory front status.  
C6. Uninstall: удалять из AppDB и Android private catalogue с двухфазным учётом; отменённый/прерванный guest-delete не должен безвозвратно теряться из-за удаления \`pending_delete_uuid\` до ACK. Delete tombstone не должен переподкладывать starter PBW на старте.  
C7. Verify source bundle SDK version, arch, dependencies, resources and worker; explain unavailable phone JavaScript features without treating package as incompatible. Keep named tested collection of six faces.
C8. Отдельные отрицательные тесты: corrupted ZIP, duplicate UUID, wrong cookie, missing resources, NACK busy, dropped serial, interrupt midway, second install queued, flashed guest still stale.

**Gate C:** все шесть PBW успешно принимаются/удаляются; 3 основных лица работают стабильно, затем все шесть по совместимости; повторные установки не создают дублей, причины ошибок сохраняются.

### P1-D — постоянная работа и устройство

D1. Выделить \`EmulatorService\` из \`RearActivity\`, экран только подписывается на состояние/кадры. Учесть ограничения Android 16 на foreground service type, запуск по явному действию и уведомление/разрешения при необходимости.  
D2. Singleton QEMU; process cleanup, no zombie PID, stale local sockets, no second session. Foreground/start, cold launch, lock/unlock, orientation and rear/front handoff, low memory, task removal and reboot under allowed OS policies.  
D3. Bounded retry/backoff/watchdog + deliberate safe mode when repeated native/guest faults. No infinite boot loops.  
D4. Flash: atomic backup/restore, integrity validation, versioned migration for upstream firmware, optional repair action only after backup. Never reset or overwrite user SPI silently.  
D5. Metrics: 30 min continuous run without uncontrolled native/guest crash or runaway CPU; battery/thermal profile at idle and screen-off, measure real values rather than claim zero drain.  
D6. Distinguish emulator running, guest booted, launcher ready, face running, face stable, stalled and failed.

**Gate D:** no guest reboot when switching Android screens; persisted PBW after app restart and OS relaunch; backups round-trip.

### P1-E — аутентичный задний экран и удобный телефонный UI

E1. Rear: only native 200×228 PebbleOS pixels letterboxed on SubScreen, nearest-neighbour, no Android toolbar, logo or debug overlay. Screen dimensions calibrated to Titan 2 hardware; no cropped content.  
E2. Rear triple-tap sends **one** true Pebble SELECT; other tap/swipe gestures pass through. Test tap-delay edge cases; no stuck BTN-left after pause/multitouch/cancel. Access to Back/Up/Down in advanced fallback, not permanent toolbar.  
E3. Front minimal: orange pixel NULLWRIST, one clear status, genuine **live** face image where possible, library with thumbnails/name/last stable status, Import/Install/Activate/Delete, Settings with Diagnostics/Backups. Respect Android status-bar window insets to prevent logo overlap seen in screenshots.  
E4. No dead "OPEN PEBBLEOS" redirect to another session, no stale \`last_installed_uuid\` used as display of running face. Errors human-readable; details and raw UUID in Advanced.  
E5. No auto-installs of starter PBW without consent. Delete remains reversible via re-import, no forced reset.

**Gate E:** tested screen switching and native UI on actual Titan 2; screenshot matches real guest and status.

### P2-F — Pebble platform features (after P0 and P1)

F1. Simple PBW/watchapp lifecycle, configuration metadata, permissions; diagnostic phone link always connected.  
F2. Native guest settings/launcher/time synchronization; locale, timezone, 12/24h, date, and RTC under realistic conditions.  
F3. Optional Android notification bridge with explicit NotificationListener consent, filters/redaction and local-only security.  
F4. Optional MediaSession metadata/transport controls; optional weather/location/calendar using scoped permissions.  
F5. \`pebble-js-app.js\` execution in a sandboxed, permission-aware phone-companion environment (PebbleKit JS/AppMessage). Decide and document unsupported Node/browser/global APIs; no unrestricted file/network execution.  
F6. Controlled sensor/battery/vibration/microphone/speaker emulation or explicit fallback where reasonable. **Not** electrically exact SF32LB52/BT/HR sensor reproduction: \`qemu_emery\` uses simplified QEMU peripherals. Label unavailable dependencies clearly.

**Gate F:** independent feature tests and user permissions; no hidden data collection or blanket permission requests.

### P2-G — выпуск и контроль качества

G1. Unique, reproducible source+QEMU+SDK+firmware+PBW version manifests, third-party licenses including PBW redistribution permissions.  
G2. Isolated Android Gradle/CI, firmware CI, desktop QEMU PBW smoke+integration, Android device smoke, artefact checksums and stable signing process.  
G3. Test matrix: fresh install / upgraded app / imported PBW / starter deletion / full flash / corrupted PBW / no phone service / low memory / QEMU reconnect / screen switching / recovery.  
G4. Preserve existing release 0.8.12 and previous APKs unchanged. No merge into \`main\` without explicit request.  
G5. Users get APK only after **desktop PBW gate AND physical guest run proof**, otherwise clearly label candidate, report test failures and preserve rollback.

## 5. Test definitions and metrics

**Smoke — firmware & security**
- Compare SHA256 to source lock, verify segment+symbols, source fix app_manager & worker_manager, no insecure MPU bypass.
- Boot authentic guest to launcher; send BACK/SELECT/UP/DOWN/touch/swipe; QMP authenticated app-private only.

**PBW compatibility — minimum matrix**
- Native Emery (no JS): Iridescency II — displays dynamic face, 60s no crash, 2 cold starts.
- Native Emery (resources): Comic Watchface — correct resources, no abnormal crash, 60s and switch away/back.
- Basalt fallback: Crush 'Em — correct bezel compatibility and no fault, 60s.
- Halcyon, BeerClock, Essential: baseline rendering, then test optional config/health/location JS features as independently scoped features.
- Optional test PBW with a worker: process launch/stop and memory permission regression.

**Reliability**
- Install, switch, uninstall, reinstall; kill Android UI without killing active QEMU; restore flash to same versions.
- 30 min screen on with no uncontrolled guest crash, repeated cold boot, UI front-rear lock/unlock and permission boundary.
- Any crash MUST record host state, guest UART2 decode, instruction/source match, and latest good frame.

**Performance targets for investigation, not claims of achieved performance**
- Native frame updates perceptually smooth on real SubScreen; profile frame interval p50/p95 and missed frames.
- Measure idle CPU/RSS, energy and temperature over real 30 min; set acceptable thresholds from Titan2 baseline, not arbitrary numbers.
- Bounded PBW operations; show stage, deadline and recoverable errors, never infinite spinner.

## 6. Work order, decisions and explicit non-goals

**First deliverable is NOT another hopeful APK.** It is **upstream source-fixed Emery ELF + Linux QEMU proof of third-party PBW**. Second deliverable is physical-Titan2 proof. Third is a resilient manager/library/service implementation.

Priority: **A → B → C → D/E → F → G**. Refactor UI in parallel only where not delaying A/B. If phase A cannot pass, pause features and investigate firmware/QEMU with GDB.

Known blockers/risks:
- Upstream source-fix commit came after our pinned commit. New firmware may require refreshed QEMU/SDK submodule pins. Verify build reproducibility instead of mixing arbitrary binaries.
- QEMU emulator models the PebbleOS platform but not exact real Time2 SoC electronics. Never promise perfect hardware/HR/BLE parity.
- 2026 upstream docs evolved: native simulator may support ARM PBW through Samsa, while older docs say it cannot. It can be a secondary comparison oracle only after verified on the chosen source revision.
- Transient USB/network not required for local emulation, but phone JS/notifications/music/weather do need separately permissioned bridge.
- Existing \`time2/README.md\` and \`MASTER.md\` contain outdated historical status; when user requests final handoff, update their current summary from verified gates, not just lab commit counters.

**Done (v1.0 release gate) means:** verified physical Titan2 PebbleOS Emery running imported and installed watchfaces/apps stably, faithful watch UI, persistent data, guest-protocol lifecycle, diagnostics/recovery, consent-aware phone services, measurable performance, old releases untouched. Anything less must remain an explicitly labeled lab build.

## 7. Near-term execution checklist for next development chat

- [ ] Create frozen troubleshooting branch off current \`feature/time2-standalone\` (do not reset/delete historical 0.7 work).
- [ ] Update PebbleOS source pin >= \`3b69e4b8\`, re-validate SDK/QEMU compatibility, build desktop.
- [ ] Run 3 end-to-end PBW tests on Linux QEMU; capture screenshots, no DACCVIOL, GDB logs.
- [ ] Rebuild device-lab APK with rebuilt ELF **without** byte patch script.
- [ ] Verify 3 PBW on physical Titan2 before claiming issue fixed; one consolidated test APK.
- [ ] Only then migrate proven old NullWrist app installer/registry/service patterns into isolated Time2.
- [ ] Add stable running health state and trustworthy front UI diagnostics, not ephemeral \`RUNNING\`/static screenshot.
- [ ] Extend compatibility matrix to all six and guest workers, then pursue phone integrations.
