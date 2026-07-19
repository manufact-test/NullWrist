#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "    private boolean redirectingToRear;\n"
        "    private boolean listenersRegistered;",
        "    private boolean redirectingToRear;\n"
        "    private boolean listenersRegistered;\n"
        "    private String renderedActiveStorageId;",
    ),
    (
        "    ) -> runOnUiThread(() -> {\n"
        "        updateRuntimeStatus(status, failure);\n"
        "        renderCatalog();\n"
        "    });",
        "    ) -> runOnUiThread(() -> {\n"
        "        updateRuntimeStatus(status, failure);\n"
        "        String activeId = PebbleRuntimeService.getActiveStorageId();\n"
        "        if (!sameStorageId(renderedActiveStorageId, activeId)) {\n"
        "            renderCatalog();\n"
        "        }\n"
        "    });",
    ),
    (
        "        String selectedId = preferences.getSelectedWatchfaceId();\n"
        "        String activeId = PebbleRuntimeService.getActiveStorageId();\n"
        "        WatchfaceMetadata selected = null;",
        "        String selectedId = preferences.getSelectedWatchfaceId();\n"
        "        String activeId = PebbleRuntimeService.getActiveStorageId();\n"
        "        renderedActiveStorageId = activeId;\n"
        "        WatchfaceMetadata selected = null;",
    ),
    (
        "    private static String shortStatus(String value) {",
        "    private static boolean sameStorageId(String first, String second) {\n"
        "        return first == null ? second == null : first.equals(second);\n"
        "    }\n\n"
        "    private static String shortStatus(String value) {",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:90]!r}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Patched MainActivity to re-render only when active runtime changes")
