#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/manufacttest/pebblereardisplay/ui/MainActivity.java")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        ") -> runOnUiThread(() -> updateRuntimeStatus(status, failure));",
        ") -> runOnUiThread(() -> {\n"
        "        updateRuntimeStatus(status, failure);\n"
        "        renderCatalog();\n"
        "    });",
    ),
    (
        "        String selectedId = preferences.getSelectedWatchfaceId();\n"
        "        WatchfaceMetadata selected = null;",
        "        String selectedId = preferences.getSelectedWatchfaceId();\n"
        "        String activeId = PebbleRuntimeService.getActiveStorageId();\n"
        "        WatchfaceMetadata selected = null;",
    ),
    (
        "        for (WatchfaceMetadata watchface : watchfaces) {\n"
        "            boolean active = watchface.getStorageId().equals(selectedId);\n"
        "            if (active) {\n"
        "                selected = watchface;\n"
        "            }\n"
        "            catalogContainer.addView(\n"
        "                    watchfaceCard(watchface, active),\n"
        "                    matchWidthWrapHeight(dp(11))\n"
        "            );\n"
        "        }",
        "        for (WatchfaceMetadata watchface : watchfaces) {\n"
        "            boolean selectedInUi = watchface.getStorageId().equals(selectedId);\n"
        "            boolean active = watchface.getStorageId().equals(activeId);\n"
        "            if (selectedInUi) {\n"
        "                selected = watchface;\n"
        "            }\n"
        "            catalogContainer.addView(\n"
        "                    watchfaceCard(watchface, selectedInUi, active),\n"
        "                    matchWidthWrapHeight(dp(11))\n"
        "            );\n"
        "        }",
    ),
    (
        "    private View watchfaceCard(WatchfaceMetadata watchface, boolean active) {",
        "    private View watchfaceCard(\n"
        "            WatchfaceMetadata watchface,\n"
        "            boolean selected,\n"
        "            boolean active\n"
        "    ) {",
    ),
    (
        "                active ? getColor(R.color.surface_selected) : getColor(R.color.surface),\n"
        "                getColor(R.color.surface_pressed),\n"
        "                active ? getColor(R.color.accent_coral) : getColor(R.color.ink)",
        "                selected ? getColor(R.color.surface_selected) : getColor(R.color.surface),\n"
        "                getColor(R.color.surface_pressed),\n"
        "                selected ? getColor(R.color.accent_coral) : getColor(R.color.ink)",
    ),
    (
        "        preview.setWatchface(watchface, bitmap, active);",
        "        preview.setWatchface(watchface, bitmap, selected);",
    ),
    (
        "        if (active) {\n"
        "            TextView activeBadge = badge(\"ACTIVE\", getColor(R.color.accent_coral), Color.WHITE);",
        "        if (selected && !active) {\n"
        "            TextView queuedBadge = badge(\"QUEUED\", getColor(R.color.accent_yellow), getColor(R.color.ink));\n"
        "            LinearLayout.LayoutParams queuedParams = new LinearLayout.LayoutParams(\n"
        "                    LinearLayout.LayoutParams.WRAP_CONTENT,\n"
        "                    dp(26)\n"
        "            );\n"
        "            queuedParams.leftMargin = dp(6);\n"
        "            badges.addView(queuedBadge, queuedParams);\n"
        "        }\n"
        "        if (active) {\n"
        "            TextView activeBadge = badge(\"ACTIVE\", getColor(R.color.accent_coral), Color.WHITE);",
    ),
    (
        "        TextView action = pixelText(\n"
        "                active ? \"ON AIR\" : \"TAP TO APPLY >\",\n"
        "                11,\n"
        "                active ? getColor(R.color.accent_coral) : getColor(R.color.text_muted)\n"
        "        );",
        "        TextView action = pixelText(\n"
        "                active ? \"ON AIR\" : selected ? \"APPLYING...\" : \"TAP TO APPLY >\",\n"
        "                11,\n"
        "                active\n"
        "                        ? getColor(R.color.accent_coral)\n"
        "                        : selected\n"
        "                        ? getColor(R.color.accent_yellow)\n"
        "                        : getColor(R.color.text_muted)\n"
        "        );",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:80]!r}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Patched MainActivity runtime acknowledgement UI")
