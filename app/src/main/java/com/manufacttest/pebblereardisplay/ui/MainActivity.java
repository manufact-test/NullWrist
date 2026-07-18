package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.manufacttest.pebblereardisplay.R;
import com.manufacttest.pebblereardisplay.data.AppPreferences;
import com.manufacttest.pebblereardisplay.data.WatchfaceRepository;
import com.manufacttest.pebblereardisplay.model.WatchfaceMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_PBW = 1001;

    private WatchfaceRepository repository;
    private AppPreferences preferences;
    private LinearLayout catalogContainer;
    private TextView selectionLabel;
    private List<WatchfaceMetadata> watchfaces = new ArrayList<>();
    private boolean rearMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderForCurrentSurface();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderForCurrentSurface();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        renderForCurrentSurface();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            return;
        }
        if (rearMode) {
            RearUi.enterImmersive(this);
        } else {
            scheduleRearModeRecheck();
        }
    }

    private void renderForCurrentSurface() {
        if (DisplayUtils.shouldUseRearMode(this, getIntent())) {
            showRearSurface();
        } else {
            showMainSurface();
        }
    }

    private void showRearSurface() {
        rearMode = true;
        repository = new WatchfaceRepository(this);
        preferences = new AppPreferences(this);
        setContentView(new RearClockView(this, repository, preferences));
        getWindow().getDecorView().post(() -> RearUi.enterImmersive(this));
    }

    private void showMainSurface() {
        rearMode = false;
        RearUi.leaveImmersive(this);
        repository = new WatchfaceRepository(this);
        preferences = new AppPreferences(this);
        setContentView(buildMainScreen());
        reloadCatalog();
        scheduleRearModeRecheck();
    }

    private void scheduleRearModeRecheck() {
        View decor = getWindow().getDecorView();
        decor.post(() -> {
            if (!rearMode && DisplayUtils.isCompactRearBounds(decor.getWidth(), decor.getHeight())) {
                showRearSurface();
            }
        });
    }

    private View buildMainScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Pebble Rear Display", 28, getColor(R.color.text_primary));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Choose a Pebble watchface here. On the Titan 2 rear display the app shows only the selected face and ignores all touches.",
                15,
                getColor(R.color.text_secondary)
        );
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        selectionLabel = text("Selected: none", 16, getColor(R.color.accent));
        selectionLabel.setPadding(0, 0, 0, dp(14));
        root.addView(selectionLabel);

        Button importButton = button("Import .pbw file");
        importButton.setOnClickListener(view -> openPbwPicker());
        root.addView(importButton, matchWidthWrapHeight(dp(8)));

        Button rearPreviewButton = button("Open rear-display preview");
        rearPreviewButton.setOnClickListener(view -> openRearPreview());
        root.addView(rearPreviewButton, matchWidthWrapHeight(dp(8)));

        Button nativeProbeButton = button("Test native ARM64 framebuffer");
        nativeProbeButton.setOnClickListener(view -> openNativeRuntimeProbe());
        root.addView(nativeProbeButton, matchWidthWrapHeight(dp(8)));

        Button qemuProbeButton = button("Test native Pebble QEMU");
        qemuProbeButton.setOnClickListener(view -> openQemuBinaryProbe());
        root.addView(qemuProbeButton, matchWidthWrapHeight(dp(18)));

        TextView listTitle = text("Watchfaces", 20, getColor(R.color.text_primary));
        listTitle.setTypeface(listTitle.getTypeface(), android.graphics.Typeface.BOLD);
        listTitle.setPadding(0, 0, 0, dp(10));
        root.addView(listTitle);

        catalogContainer = new LinearLayout(this);
        catalogContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(catalogContainer, matchWidthWrapHeight(0));

        root.addView(buildDisplayInfo());
        return scroll;
    }

    private void openRearPreview() {
        Intent preview = new Intent(this, RearDisplayActivity.class);
        preview.putExtra(DisplayUtils.EXTRA_FORCE_REAR_MODE, true);
        preview.putExtra(DisplayUtils.EXTRA_PREVIEW_MODE, true);
        try {
            startActivity(preview);
        } catch (RuntimeException exception) {
            showError("Cannot open rear preview: " + exception.getClass().getSimpleName());
        }
    }

    private void openNativeRuntimeProbe() {
        try {
            startActivity(new Intent(this, NativeRuntimeProbeActivity.class));
        } catch (RuntimeException exception) {
            showError("Cannot open native runtime test: " + exception.getClass().getSimpleName());
        }
    }

    private void openQemuBinaryProbe() {
        try {
            startActivity(new Intent(this, QemuBinaryProbeActivity.class));
        } catch (RuntimeException exception) {
            showError("Cannot open QEMU test: " + exception.getClass().getSimpleName());
        }
    }

    private View buildDisplayInfo() {
        Display display = DisplayUtils.currentDisplay(this);
        android.graphics.Point windowSize = DisplayUtils.currentWindowSize(this);
        String details = display == null
                ? "Display information unavailable"
                : "Current display: ID " + display.getDisplayId()
                + " · window " + windowSize.x + "×" + windowSize.y
                + " · mode " + display.getMode().getPhysicalWidth()
                + "×" + display.getMode().getPhysicalHeight()
                + " · " + Math.round(display.getRefreshRate()) + " Hz";

        TextView info = text(details, 12, getColor(R.color.text_secondary));
        info.setPadding(0, dp(24), 0, 0);
        return info;
    }

    private void reloadCatalog() {
        try {
            watchfaces = repository.loadAll();
            ensureSelection();
            renderCatalog();
        } catch (IOException exception) {
            showError("Cannot load watchfaces: " + exception.getMessage());
        }
    }

    private void ensureSelection() {
        String selectedId = preferences.getSelectedWatchfaceId();
        if (selectedId == null && !watchfaces.isEmpty()) {
            preferences.setSelectedWatchfaceId(watchfaces.get(0).getStorageId());
        }
    }

    private void renderCatalog() {
        catalogContainer.removeAllViews();
        String selectedId = preferences.getSelectedWatchfaceId();
        WatchfaceMetadata selected = null;

        for (WatchfaceMetadata watchface : watchfaces) {
            if (watchface.getStorageId().equals(selectedId)) {
                selected = watchface;
            }
            catalogContainer.addView(
                    watchfaceCard(watchface, watchface.getStorageId().equals(selectedId)),
                    matchWidthWrapHeight(dp(10))
            );
        }

        if (watchfaces.isEmpty()) {
            TextView empty = text(
                    "No watchfaces found. Import a .pbw file to begin.",
                    15,
                    getColor(R.color.text_secondary)
            );
            empty.setPadding(dp(4), dp(16), dp(4), dp(16));
            catalogContainer.addView(empty);
        }

        selectionLabel.setText(selected == null
                ? "Selected: none"
                : "Selected: " + selected.getName());
    }

    private View watchfaceCard(WatchfaceMetadata watchface, boolean selected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(getColor(selected
                ? R.color.surface_selected
                : R.color.surface));
        card.setClickable(true);
        card.setFocusable(true);

        TextView name = text(watchface.getName(), 18, getColor(R.color.text_primary));
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(name);

        String source = watchface.isBundled() ? "Bundled" : "Imported";
        TextView meta = text(
                watchface.getAuthor() + " · v" + watchface.getVersion()
                        + "\n" + watchface.platformLabel()
                        + " · " + source
                        + (watchface.hasPhoneJavaScript() ? " · phone JS" : ""),
                13,
                getColor(R.color.text_secondary)
        );
        meta.setPadding(0, dp(5), 0, 0);
        card.addView(meta);

        if (selected) {
            TextView marker = text("ACTIVE", 12, getColor(R.color.accent));
            marker.setGravity(Gravity.END);
            marker.setTypeface(marker.getTypeface(), android.graphics.Typeface.BOLD);
            marker.setPadding(0, dp(8), 0, 0);
            card.addView(marker);
        }

        card.setOnClickListener(view -> {
            preferences.setSelectedWatchfaceId(watchface.getStorageId());
            renderCatalog();
        });
        return card;
    }

    private void openPbwPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream",
                "application/zip"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_PBW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_PBW || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // The import copies the file immediately, so persisted access is optional.
        }

        try {
            WatchfaceMetadata imported = repository.importFromUri(uri);
            preferences.setSelectedWatchfaceId(imported.getStorageId());
            Toast.makeText(this, "Imported " + imported.getName(), Toast.LENGTH_SHORT).show();
            reloadCatalog();
        } catch (IOException exception) {
            showError("Import failed: " + exception.getMessage());
        }
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setLineSpacing(0, 1.08f);
        return text;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.BLACK);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackgroundColor(getColor(R.color.accent));
        return button;
    }

    private LinearLayout.LayoutParams matchWidthWrapHeight(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
