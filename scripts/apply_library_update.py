#!/usr/bin/env python3
"""One-shot source migration for the Pebblehertz removable watchface library."""

from __future__ import annotations

import importlib.util
import json
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:80]!r}")
    write(path, text.replace(old, new, 1))


def insert_before(path: str, marker: str, insertion: str) -> None:
    text = read(path)
    if insertion.strip() in text:
        return
    index = text.find(marker)
    if index < 0:
        raise RuntimeError(f"{path}: marker not found: {marker!r}")
    write(path, text[:index] + insertion + text[index:])


def replace_range(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    text = read(path)
    start = text.find(start_marker)
    if start < 0:
        raise RuntimeError(f"{path}: start marker not found: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"{path}: end marker not found: {end_marker!r}")
    write(path, text[:start] + replacement + text[end:])


def finalize_manifest() -> None:
    fetch_path = ROOT / "scripts" / "fetch_bundled_watchfaces.py"
    spec = importlib.util.spec_from_file_location("fetch_bundled_watchfaces", fetch_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)

    manifest_path = ROOT / "bundled-watchfaces.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    by_name = {entry["name"]: entry for entry in manifest}
    by_name["Code CMD"]["download_url"] = (
        "https://appstore-api.repebble.com/api/assets/pbw/55de277d785abd44a4000073.pbw"
    )

    studio = by_name["Studio Clock"]
    studio_url = module.resolve_download_url(studio)
    temporary = ROOT / ".studio-clock.pbw"
    request = urllib.request.Request(studio_url, headers={"User-Agent": module.USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
        shutil.copyfileobj(response, output)
    module.validate_pbw(temporary, studio["name"])
    studio["download_url"] = studio_url
    studio["sha256"] = module.sha256(temporary)
    temporary.unlink()
    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    subprocess.run([sys.executable, str(fetch_path)], check=True)
    print(f"Studio Clock pinned as {studio['sha256']} from {studio_url}")


def patch_preferences() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/data/AppPreferences.java"
    text = read(path)
    if "clearSelectedWatchfaceId" in text:
        return
    index = text.rfind("\n}")
    if index < 0:
        raise RuntimeError(f"{path}: class closing brace not found")
    method = '''

    public void clearSelectedWatchfaceId() {
        preferences.edit().remove(KEY_SELECTED_WATCHFACE).apply();
    }
'''
    write(path, text[:index] + method + text[index:])


def patch_repository() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/data/WatchfaceRepository.java"
    text = read(path)
    if "import android.content.SharedPreferences;" not in text:
        text = text.replace(
            "import android.content.Context;\n",
            "import android.content.Context;\nimport android.content.SharedPreferences;\n",
            1,
        )
    if "KEY_HIDDEN_BUNDLED" not in text:
        text = text.replace(
            '    private static final String ASSET_DIRECTORY = "watchfaces";\n',
            '    private static final String ASSET_DIRECTORY = "watchfaces";\n'
            '    private static final String LIBRARY_PREFERENCES = "watchface_library";\n'
            '    private static final String KEY_HIDDEN_BUNDLED = "hidden_bundled";\n',
            1,
        )
    write(path, text)

    insert_before(
        path,
        "    private void copyAndReadBundled(\n",
        '''    public void delete(WatchfaceMetadata metadata) throws IOException {
        if (metadata == null) {
            throw new IOException("No watchface was selected for deletion");
        }
        ensureDirectory();
        File stored = fileFor(metadata);
        if (stored.isFile() && !stored.delete()) {
            throw new IOException("Cannot delete " + metadata.getName());
        }
        if (metadata.isBundled()) {
            Set<String> hidden = hiddenBundledStorageIds();
            hidden.add(metadata.getStorageId());
            context.getSharedPreferences(LIBRARY_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet(KEY_HIDDEN_BUNDLED, hidden)
                    .apply();
        }
    }

''',
    )

    text = read(path)
    if "Set<String> hiddenBundled = hiddenBundledStorageIds();\n\n        for (String name" not in text:
        text = text.replace(
            '''        if (names == null) {
            return;
        }

        for (String name : names) {
''',
            '''        if (names == null) {
            return;
        }
        Set<String> hiddenBundled = hiddenBundledStorageIds();

        for (String name : names) {
''',
            1,
        )
    text = text.replace(
        '''            if (!name.toLowerCase(Locale.ROOT).endsWith(".pbw")) {
''',
        '''            if (!name.toLowerCase(Locale.ROOT).endsWith(".pbw")
                    || hiddenBundled.contains(name)) {
''',
        1,
    )
    if "Set<String> hiddenBundled = hiddenBundledStorageIds();\n        for (File file" not in text:
        text = text.replace(
            '''        if (files == null) {
            return;
        }
        for (File file : files) {
''',
            '''        if (files == null) {
            return;
        }
        Set<String> hiddenBundled = hiddenBundledStorageIds();
        for (File file : files) {
''',
            1,
        )
    text = text.replace(
        '''                    || seenStorageIds.contains(file.getName())) {
''',
        '''                    || seenStorageIds.contains(file.getName())
                    || hiddenBundled.contains(file.getName())) {
''',
        1,
    )
    write(path, text)

    insert_before(
        path,
        "    private void removeRetiredBundledFiles() {\n",
        '''    private Set<String> hiddenBundledStorageIds() {
        SharedPreferences preferences = context.getSharedPreferences(
                LIBRARY_PREFERENCES,
                Context.MODE_PRIVATE
        );
        Set<String> stored = preferences.getStringSet(KEY_HIDDEN_BUNDLED, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

''',
    )


def patch_thumbnails() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/data/WatchfaceThumbnailRepository.java"
    text = read(path)
    if "CAPTURE_SCHEMA_VERSION" not in text:
        text = text.replace(
            '    private static final String FILE_DIRECTORY = "watchface-thumbnails";\n',
            '    private static final String FILE_DIRECTORY = "watchface-thumbnails";\n'
            '    private static final String CAPTURE_PREFERENCES = "watchface_thumbnail_captures";\n'
            '    private static final int CAPTURE_SCHEMA_VERSION = 2;\n',
            1,
        )
        write(path, text)

    insert_before(
        path,
        "    public Bitmap load(WatchfaceMetadata metadata) {\n",
        '''    public boolean hasCurrentThumbnail(WatchfaceMetadata metadata) {
        if (metadata.isBundled()) {
            return hasThumbnail(metadata);
        }
        File captured = new File(directory, fileName(metadata));
        int version = context.getSharedPreferences(
                CAPTURE_PREFERENCES,
                Context.MODE_PRIVATE
        ).getInt(captureKey(metadata), 0);
        return captured.isFile() && version >= CAPTURE_SCHEMA_VERSION;
    }

''',
    )

    text = read(path)
    old_success = '''        if (!temporary.renameTo(destination)) {
            temporary.delete();
            return false;
        }
        return true;
    }
'''
    new_success = '''        if (!temporary.renameTo(destination)) {
            temporary.delete();
            return false;
        }
        context.getSharedPreferences(CAPTURE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(captureKey(metadata), CAPTURE_SCHEMA_VERSION)
                .apply();
        return true;
    }
'''
    if ".putInt(captureKey(metadata), CAPTURE_SCHEMA_VERSION)" not in text:
        if old_success not in text:
            raise RuntimeError(f"{path}: capture success block not found")
        text = text.replace(old_success, new_success, 1)
        write(path, text)

    insert_before(
        path,
        "    private Bitmap decodeFile(File file) {\n",
        '''    public void delete(WatchfaceMetadata metadata) {
        File captured = new File(directory, fileName(metadata));
        if (captured.isFile()) {
            captured.delete();
        }
        context.getSharedPreferences(CAPTURE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(captureKey(metadata))
                .apply();
    }

''',
    )
    insert_before(
        path,
        "    private static String fileName(WatchfaceMetadata metadata) {\n",
        '''    private static String captureKey(WatchfaceMetadata metadata) {
        return "capture:" + fileName(metadata);
    }

''',
    )


def patch_runtime() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/runtime/PebbleRuntimeService.java"
    text = read(path)
    text = text.replace(
        "    private static final long THUMBNAIL_SETTLE_MILLIS = 2_500L;\n",
        '''    private static final long THUMBNAIL_MIN_SETTLE_MILLIS = 4_500L;
    private static final long THUMBNAIL_MAX_SETTLE_MILLIS = 8_000L;
    private static final long THUMBNAIL_QUIET_MILLIS = 650L;
''',
        1,
    )
    text = text.replace(
        '''        WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);
        if (thumbnails.hasThumbnail(metadata)) {
            return;
        }
        waitForThumbnailSettle(current, THUMBNAIL_SETTLE_MILLIS);
''',
        '''        WatchfaceThumbnailRepository thumbnails = new WatchfaceThumbnailRepository(this);
        if (thumbnails.hasCurrentThumbnail(metadata)) {
            return;
        }
        waitForThumbnailReady(current);
''',
        1,
    )
    write(path, text)

    replace_range(
        path,
        "    private static void waitForThumbnailSettle(\n",
        "    private void ensureCurrent(int requestedGeneration) {\n",
        '''    private static void waitForThumbnailReady(PebbleQemuProcess current)
            throws InterruptedException {
        long started = System.nanoTime();
        long minimumDeadline = started + TimeUnit.MILLISECONDS.toNanos(
                THUMBNAIL_MIN_SETTLE_MILLIS
        );
        long maximumDeadline = started + TimeUnit.MILLISECONDS.toNanos(
                THUMBNAIL_MAX_SETTLE_MILLIS
        );
        while (current.isRunning() && System.nanoTime() < minimumDeadline) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(
                    minimumDeadline - System.nanoTime()
            );
            Thread.sleep(Math.max(1L, Math.min(100L, remaining)));
        }

        int lastSequence = current.readFrameSequence();
        long lastFrameChange = System.nanoTime();
        long quietNanos = TimeUnit.MILLISECONDS.toNanos(THUMBNAIL_QUIET_MILLIS);
        while (current.isRunning() && System.nanoTime() < maximumDeadline) {
            int sequence = current.readFrameSequence();
            long now = System.nanoTime();
            if (sequence != lastSequence) {
                lastSequence = sequence;
                lastFrameChange = now;
            } else if (now - lastFrameChange >= quietNanos) {
                return;
            }
            Thread.sleep(80L);
        }
    }

''',
    )


def patch_main_activity() -> None:
    path = "app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java"
    replace_range(
        path,
        "    private void ensureSelection() {\n",
        "    private void renderCatalog() {\n",
        '''    private void ensureSelection() {
        if (watchfaces.isEmpty()) {
            preferences.clearSelectedWatchfaceId();
            return;
        }
        String selectedId = preferences.getSelectedWatchfaceId();
        for (WatchfaceMetadata watchface : watchfaces) {
            if (watchface.getStorageId().equals(selectedId)) {
                return;
            }
        }
        preferences.setSelectedWatchfaceId(watchfaces.get(0).getStorageId());
    }

''',
    )

    text = read(path)
    card_start = text.find("    private View watchfaceCard(\n")
    action_start = text.find("        TextView action = pixelText(\n", card_start)
    action_end = text.find("        card.setOnClickListener(view -> applyWatchface(watchface));\n", action_start)
    if card_start < 0 or action_start < 0 or action_end < 0:
        raise RuntimeError(f"{path}: watchface card action block not found")
    controls = '''        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(8), 0, 0);

        TextView action = pixelText(
                active ? "ON AIR" : selected ? "APPLYING..." : "TAP TO APPLY >",
                11,
                active
                        ? getColor(R.color.accent_coral)
                        : selected
                        ? getColor(R.color.accent_yellow)
                        : getColor(R.color.text_muted)
        );
        controls.addView(action, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView delete = pixelText("DELETE", 10, getColor(R.color.error));
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(9), 0, dp(9), 0);
        delete.setMinHeight(dp(34));
        delete.setClickable(true);
        delete.setFocusable(true);
        delete.setBackground(interactivePanelBackground(
                getColor(R.color.surface_warm),
                getColor(R.color.surface_pressed),
                getColor(R.color.error)
        ));
        delete.setOnClickListener(view -> confirmDeleteWatchface(watchface));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
        );
        deleteParams.leftMargin = dp(8);
        controls.addView(delete, deleteParams);
        copy.addView(controls);

'''
    text = text[:action_start] + controls + text[action_end:]
    write(path, text)

    insert_before(
        path,
        "    private void updateRuntimeStatus(String status, String failure) {\n",
        '''    private void confirmDeleteWatchface(WatchfaceMetadata watchface) {
        String message = watchface.isBundled()
                ? "Remove this preinstalled watchface from your locker? "
                + "It can be restored by clearing Pebblehertz app data or reinstalling the app."
                : "Permanently remove this imported PBW from Pebblehertz?";
        new AlertDialog.Builder(this)
                .setTitle("Delete " + watchface.getName() + "?")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> deleteWatchface(watchface))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteWatchface(WatchfaceMetadata watchface) {
        boolean wasSelected = sameStorageId(
                preferences.getSelectedWatchfaceId(),
                watchface.getStorageId()
        );
        boolean wasActive = sameStorageId(
                PebbleRuntimeService.getActiveStorageId(),
                watchface.getStorageId()
        );
        try {
            repository.delete(watchface);
            thumbnails.delete(watchface);
            reloadCatalog();
            Toast.makeText(
                    this,
                    "Deleted " + watchface.getName(),
                    Toast.LENGTH_SHORT
            ).show();
            if (watchfaces.isEmpty()) {
                PebbleRuntimeService.stop(this);
            } else if (wasSelected || wasActive) {
                WatchfaceMetadata replacement = watchfaces.get(0);
                String selectedId = preferences.getSelectedWatchfaceId();
                for (WatchfaceMetadata candidate : watchfaces) {
                    if (candidate.getStorageId().equals(selectedId)) {
                        replacement = candidate;
                        break;
                    }
                }
                updateRuntimeStatus("Launching " + replacement.getName(), null);
                PebbleRuntimeService.select(this);
            }
        } catch (IOException exception) {
            showError("Delete failed: " + exception.getMessage());
        }
    }

''',
    )


def restore_android_workflow() -> None:
    path = ".github/workflows/android.yml"
    text = read(path)
    start = text.find("      - name: Apply pending Pebblehertz library update\n")
    end = text.find("      - name: Fetch bundled watchfaces\n", start)
    if start >= 0 and end >= 0:
        text = text[:start] + text[end:]

    commit_start = text.find("      - name: Commit pinned runtime assets and library update\n")
    if commit_start >= 0:
        original = '''      - name: Commit pinned runtime assets
        if: github.event_name == 'push' && startsWith(github.ref, 'refs/heads/feature/')
        run: |
          if [ -z "$(git status --porcelain -- app/src/main/assets/watchfaces app/src/main/assets/pebble)" ]; then
            echo "Bundled PBW and Pebble firmware assets are already committed."
            exit 0
          fi
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add app/src/main/assets/watchfaces app/src/main/assets/pebble
          git commit -m "Bundle watchfaces and Basalt emulator firmware"
          git push origin "HEAD:${GITHUB_REF_NAME}"
'''
        text = text[:commit_start] + original
    write(path, text)


def cleanup() -> None:
    old_workflow = ROOT / ".github" / "workflows" / "apply-library-update.yml"
    if old_workflow.exists():
        old_workflow.unlink()
    restore_android_workflow()
    Path(__file__).unlink()


def main() -> int:
    finalize_manifest()
    patch_preferences()
    patch_repository()
    patch_thumbnails()
    patch_runtime()
    patch_main_activity()
    cleanup()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
