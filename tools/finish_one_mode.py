from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one {label} match, found {count}")
    return text.replace(old, new, 1)


service_path = Path(
    "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java"
)
service = service_path.read_text()
service = service.replace(
    "    private static final long WATCHDOG_INTERVAL_MILLIS = 30_000L;",
    "    private static final long WATCHDOG_INTERVAL_MILLIS = 5_000L;",
)
watchdog_block = '''    private final Runnable watchdogTick = new Runnable() {
        @Override
        public void run() {
            if (shouldHaveRuntime()) {
                PebbleQemuProcess current = runtime;
                if (!starting
                        && (current == null || !current.isRunning())
                        && SystemClock.elapsedRealtime() >= restartNotBeforeElapsed) {
                    scheduleRuntime(false);
                }
            }
            serviceHandler.postDelayed(this, WATCHDOG_INTERVAL_MILLIS);
        }
    };
'''
if "private final Runnable selectionDebounce" not in service:
    service = replace_once(
        service,
        watchdog_block,
        watchdog_block
        + "\n    private final Runnable selectionDebounce = this::dispatchLatestSelection;\n",
        "watchdog block",
    )
pattern = re.compile(
    r"    private void scheduleSelection\(\) \{.*?\n"
    r"    private void applyLatestSelection\(int requestedGeneration, int request\) \{",
    re.S,
)
replacement = '''    private void scheduleSelection() {
        selectionRequest.incrementAndGet();
        serviceHandler.removeCallbacks(selectionDebounce);
        serviceHandler.postDelayed(selectionDebounce, SELECTION_DEBOUNCE_MILLIS);
    }

    private void dispatchLatestSelection() {
        int request = selectionRequest.get();
        int requestedGeneration = generation.get();
        try {
            executor.execute(() -> applyLatestSelection(requestedGeneration, request));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void applyLatestSelection(int requestedGeneration, int request) {'''
service, count = pattern.subn(replacement, service, count=1)
if count != 1:
    raise SystemExit(f"Expected one selection scheduling block, found {count}")
service_path.write_text(service)

backoff_path = Path(
    "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/RuntimeRestartBackoff.java"
)
backoff = backoff_path.read_text()
backoff = backoff.replace(
    "    private static final long BASE_DELAY_MILLIS = 15_000L;",
    "    private static final long BASE_DELAY_MILLIS = 2_000L;",
)
backoff = backoff.replace(
    "    private static final long MAX_DELAY_MILLIS = 5L * 60L * 1_000L;",
    "    private static final long MAX_DELAY_MILLIS = 60_000L;",
)
backoff_path.write_text(backoff)

test_path = Path(
    "app/src/test/java/com/manufacttest/pebblereardisplay/runtime/RuntimeRestartBackoffTest.java"
)
test = test_path.read_text()
test = re.sub(
    r"        assertEquals\(15_000L, RuntimeRestartBackoff.delayMillis\(1\)\);.*?"
    r"        assertEquals\(300_000L, RuntimeRestartBackoff.delayMillis\(20\)\);",
    '''        assertEquals(2_000L, RuntimeRestartBackoff.delayMillis(1));
        assertEquals(4_000L, RuntimeRestartBackoff.delayMillis(2));
        assertEquals(8_000L, RuntimeRestartBackoff.delayMillis(3));
        assertEquals(16_000L, RuntimeRestartBackoff.delayMillis(4));
        assertEquals(32_000L, RuntimeRestartBackoff.delayMillis(5));
        assertEquals(60_000L, RuntimeRestartBackoff.delayMillis(6));
        assertEquals(60_000L, RuntimeRestartBackoff.delayMillis(20));''',
    test,
    count=1,
    flags=re.S,
)
test = test.replace(
    "assertEquals(15_000L, RuntimeRestartBackoff.delayMillis(0));",
    "assertEquals(2_000L, RuntimeRestartBackoff.delayMillis(0));",
)
test = test.replace(
    "assertEquals(15_000L, RuntimeRestartBackoff.delayMillis(-4));",
    "assertEquals(2_000L, RuntimeRestartBackoff.delayMillis(-4));",
)
test_path.write_text(test)

for obsolete in [
    ".github/scripts/simplify-runtime.py.gz.b64",
    ".simplify-runtime-result.txt",
    ".simplify-runtime-log.txt",
]:
    Path(obsolete).unlink(missing_ok=True)

for file_name in [
    "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java",
    "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleQemuProcess.java",
    "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java",
]:
    value = Path(file_name).read_text()
    for token in [
        "SCHEDULE SLEEP",
        "LOW_BATTERY_PULSE",
        "updateBatteryState(",
        ".pause()",
        ".resume()",
        "Thread.sleep(SELECTION_DEBOUNCE_MILLIS)",
    ]:
        if token in value:
            raise SystemExit(f"Obsolete or unstable token {token!r} remains in {file_name}")

print("final runtime hardening applied")
