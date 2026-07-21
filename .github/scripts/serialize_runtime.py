from pathlib import Path

path = Path("app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java")
text = path.read_text()


def once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one match, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


once(
    """    private final Runnable policyTick = new Runnable() {
        @Override
        public void run() {
            applyPowerPolicy();
            policyHandler.postDelayed(this, 60_000L);
        }
    };
""",
    """    private final Runnable policyTick = new Runnable() {
        @Override
        public void run() {
            schedulePowerPolicy();
            policyHandler.postDelayed(this, 60_000L);
        }
    };
""",
)

start = text.index("    private final Runnable lowBatteryPause = () -> {")
end = text.index("    private final BroadcastReceiver powerReceiver", start)
text = text[:start] + """    private final Runnable lowBatteryPause = () ->
            executor.execute(this::applyLowBatteryPause);

    private final Runnable lowBatteryPulse = () ->
            executor.execute(this::applyLowBatteryPulse);

    private void applyLowBatteryPause() {
        if (powerMode != RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE || runtimeBusy) {
            return;
        }
        PebbleQemuProcess current = runtime;
        if (current == null || !current.isRunning()) {
            return;
        }
        try {
            current.pause();
            notifyListeners();
        } catch (IOException error) {
            reportFailure(error);
        }
    }

    private void applyLowBatteryPulse() {
        if (powerMode != RuntimePowerPolicy.Mode.LOW_BATTERY_PULSE || runtimeBusy) {
            return;
        }
        PebbleQemuProcess current = runtime;
        if (current == null || !current.isRunning()) {
            return;
        }
        try {
            current.resume();
            policyHandler.removeCallbacks(lowBatteryPause);
            policyHandler.postDelayed(lowBatteryPause, LOW_BATTERY_AWAKE_MILLIS);
        } catch (IOException error) {
            reportFailure(error);
        }
        scheduleNextLowBatteryPulse();
    }

""" + text[end:]

once(
    """        if (ACTION_SELECT.equals(action)) {
            scheduleSelection();
        } else if (ACTION_REFRESH_POWER.equals(action)) {
            applyPowerPolicy();
        } else {
""",
    """        if (ACTION_SELECT.equals(action)) {
            scheduleSelection();
        } else if (ACTION_REFRESH_POWER.equals(action)) {
            schedulePowerPolicy();
        } else {
""",
)
once(
    """        if (!forceRestart && healthyRuntime) {
            applyPowerPolicy();
            notifyListeners();
            return;
        }
""",
    """        if (!forceRestart && healthyRuntime) {
            schedulePowerPolicy();
            notifyListeners();
            return;
        }
""",
)
once(
    """    private void scheduleSelection() {
        int requestedGeneration = generation.get();
        executor.execute(() -> applyLatestSelection(requestedGeneration));
    }
""",
    """    private void schedulePowerPolicy() {
        executor.execute(this::applyPowerPolicy);
    }

    private void scheduleSelection() {
        int requestedGeneration = generation.get();
        executor.execute(() -> applyLatestSelection(requestedGeneration));
    }
""",
)

policy_posts = "            policyHandler.post(this::applyPowerPolicy);"
count = text.count(policy_posts)
if count != 2:
    raise RuntimeError(f"Expected two direct policy posts, found {count}")
text = text.replace(policy_posts, "            schedulePowerPolicy();")

once(
    """            SelectedWatchface selected = selectedWatchface();
            if (selected.metadata.getStorageId().equals(activeStorageId)) {
                return;
            }
""",
    """            SelectedWatchface selected = selectedWatchface();
            if (selected.metadata.getStorageId().equals(activeStorageId)) {
                selectionQueuedForWake = false;
                notifyListeners();
                return;
            }
""",
)

path.write_text(text)
