package com.manufact.pixelouteronly;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal, deliberately explicit controls for a broken inner panel. */
public class MainActivity extends Activity {
    private TextView status;
    private TextView details;
    private Button enable;
    private Button disable;
    private final Runnable refreshListener = () -> runOnUiThread(this::refresh);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        details = findViewById(R.id.details);
        enable = findViewById(R.id.btn_enable);
        disable = findViewById(R.id.btn_disable);
        enable.setOnClickListener(v -> request(true));
        disable.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {})
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> request(false))
                .show());
        findViewById(R.id.btn_copy).setOnClickListener(v -> {
            String diagnostic = Fold.diagnostics(this);
            getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText("Pixel Outer Only", diagnostic));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_tile).setOnClickListener(v -> {
            getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, ToggleTile.class),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_fold),
                getMainExecutor(), result -> {});
        });
        findViewById(R.id.btn_notifications).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        Fold.watch(getApplicationContext(), refreshListener);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        HoldService.start(this);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    private void request(boolean outer) {
        startActivity(new Intent(this, ToggleActivity.class)
                .putExtra(ToggleActivity.EXTRA_WANT_OUTER, outer));
    }

    private void refresh() {
        boolean supported = Fold.supported(this);
        boolean active = Fold.forcedOuter;
        status.setText(!supported ? R.string.status_unsupported :
                active ? R.string.status_active : R.string.status_inactive);
        enable.setEnabled(supported && !active);
        disable.setEnabled(supported && active);
        disable.setVisibility(active ? View.VISIBLE : View.GONE);
        details.setText(getString(R.string.device_details,
                android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL,
                android.os.Build.VERSION.RELEASE,
                String.valueOf(Fold.target)));
        findViewById(R.id.btn_notifications).setVisibility(
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        ? View.GONE : View.VISIBLE);
    }
}
