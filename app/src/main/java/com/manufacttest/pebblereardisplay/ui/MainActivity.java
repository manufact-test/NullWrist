package com.manufacttest.pebblereardisplay.ui;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
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
import com.manufacttest.pebblereardisplay.runtime.PebbleRuntimeService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_PBW = 1001;
    private static final int REQUEST_NOTIFICATIONS = 1002;
    private static final String SETUP_PREFS = "background_setup";
    private static final String KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown";

    private WatchfaceRepository repository;
    private AppPreferences preferences;
    private LinearLayout catalogContainer;
    private TextView selectionLabel;
    private List<WatchfaceMetadata> watchfaces = new ArrayList<>();
    private boolean rearMode;
    private boolean redirectingToRear;

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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return rearMode || super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (!rearMode) {
            super.onBackPressed();
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
        if (!redirectingToRear) {
            redirectingToRear = true;
            Intent rear = new Intent(this, RearDisplayActivity.class)
                    .putExtra(DisplayUtils.EXTRA_FORCE_REAR_MODE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                if (getDisplay() != null) {
                    options.setLaunchDisplayId(getDisplay().getDisplayId());
                }
                startActivity(rear, options.toBundle());
                finish();
                return;
            } catch (RuntimeException ignored) {
                redirectingToRear = false;
            }
        }

        rearMode = true;
        setContentView(new PebbleOsSurfaceView(this));
        getWindow().getDecorView().post(() -> RearUi.enterImmersive(this));
    }

    private void showMainSurface() {
        rearMode = false;
        redirectingToRear = false;
        RearUi.leaveImmersive(this);
        repository = new WatchfaceRepository(this);
        preferences = new AppPreferences(this);
        setContentView(buildMainScreen());
        reloadCatalog();
        PebbleRuntimeService.start(this);
        maybeRequestBackgroundSetup();
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
                "Choose a Pebble watchface here. The runtime stays active in the background, while the rear display ignores all touches.",
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

        Button rearPreviewButton = button("Preview rear display");
        rearPreviewButton.setOnClickListener(view -> openRearPreview());
        root.addView(rearPreviewButton, matchWidthWrapHeight(dp(8)));

        Button backgroundButton = button("Background reliability settings");
        backgroundButton.setOnClickListener(view -> openBackgroundSettings());
        root.addView(backgroundButton, matchWidthWrapHeight(dp(8)));

        TextView backgroundHint = text(
                "Titan 2: also allow this app in DuraSpeed, disable it in App blocker, and set Battery to Unrestricted / Don't optimize.",
                13,
                getColor(R.color.text_secondary)
        );
        backgroundHint.setPadding(dp(2), 0, dp(2), dp(18));
        root.addView(backgroundHint);

        TextView listTitle = text("Watchfaces", 20, getColor(R.color.text_primary));
        listTitle.setTypeface(listTitle.getTypeface(), android.graphics.Typeface.BOLD);
        listTitle.setPadding(0, 0, 0, dp(10));
        root.addView(listTitle);

        catalogContainer = new LinearLayout(this);
        catalogContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(catalogContainer, matchWidthWrapHeight(0));
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
            PebbleRuntimeService.restart(this);
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
            PebbleRuntimeService.restart(this);
        } catch (IOException exception) {
            showError("Import failed: " + exception.getMessage());
        }
    }

    private void maybeRequestBackgroundSetup() {
        getWindow().getDecorView().post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS
                );
                return;
            }
            maybeShowBatteryPrompt();
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            maybeShowBatteryPrompt();
        }
    }

    private void maybeShowBatteryPrompt() {
        if (isIgnoringBatteryOptimizations()) {
            return;
        }
        SharedPreferences setup = getSharedPreferences(SETUP_PREFS, MODE_PRIVATE);
        if (setup.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)) {
            return;
        }
        setup.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Keep the watchface running?")
                .setMessage("Allow Pebble Rear Display to run without battery optimization. "
                        + "This keeps the rear watchface alive after the main window is closed.")
                .setPositiveButton("Allow", (dialog, which) -> requestBatteryExemption())
                .setNegativeButton("Later", null)
                .show();
    }

    private void openBackgroundSettings() {
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryExemption();
            return;
        }
        Toast.makeText(
                this,
                "Also enable the app in DuraSpeed and disable it in App blocker.",
                Toast.LENGTH_LONG
        ).show();
        openAppDetails();
    }

    private boolean isIgnoringBatteryOptimizations() {
        PowerManager manager = getSystemService(PowerManager.class);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryExemption() {
        try {
            Intent request = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(request);
        } catch (RuntimeException error) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (RuntimeException error) {
            showError("Cannot open application settings");
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
