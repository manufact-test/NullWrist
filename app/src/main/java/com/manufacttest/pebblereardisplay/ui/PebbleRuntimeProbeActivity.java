package com.manufacttest.pebblereardisplay.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public final class PebbleRuntimeProbeActivity extends Activity {
    private PebbleEmulatorWebView emulatorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        emulatorView = new PebbleEmulatorWebView(this, new PebbleEmulatorWebView.Listener() {
            @Override
            public void onStatus(String status) {
                // Status is already visible as a small overlay above the emulated display.
            }

            @Override
            public void onFatalError(String message) {
                Toast.makeText(PebbleRuntimeProbeActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        setContentView(emulatorView);
    }

    @Override
    public void onBackPressed() {
        if (emulatorView != null && emulatorView.canGoBack()) {
            emulatorView.goBack();
        } else {
            finish();
        }
    }
}
