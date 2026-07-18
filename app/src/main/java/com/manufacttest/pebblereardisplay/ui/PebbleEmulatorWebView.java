package com.manufacttest.pebblereardisplay.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class PebbleEmulatorWebView extends FrameLayout {
    public interface Listener {
        void onStatus(String status);
        void onFatalError(String message);
    }

    private static final String ALLOWED_HOST = "ericmigi.github.io";
    private static final String EMULATOR_URL =
            "https://ericmigi.github.io/pebble-qemu-wasm/?fw=sdk&auto";

    private final WebView webView;
    private final TextView statusOverlay;
    private final Listener listener;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public PebbleEmulatorWebView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(Color.BLACK);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
                settings.getUserAgentString() + " PebbleRearDisplay/0.2-runtime-probe"
        );

        webView.addJavascriptInterface(new RuntimeBridge(), "PebbleRearDisplay");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new EmulatorClient());

        addView(webView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        ));

        statusOverlay = new TextView(context);
        statusOverlay.setText("Loading real PebbleOS emulator…");
        statusOverlay.setTextColor(Color.WHITE);
        statusOverlay.setTextSize(12);
        statusOverlay.setGravity(Gravity.CENTER);
        statusOverlay.setPadding(dp(8), dp(6), dp(8), dp(6));
        statusOverlay.setBackgroundColor(Color.argb(205, 0, 0, 0));

        LayoutParams overlayParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        addView(statusOverlay, overlayParams);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.startSafeBrowsing(context.getApplicationContext(), value -> {
                // A failed Safe Browsing initialization must not block the local probe.
            });
        }

        webView.loadUrl(EMULATOR_URL);
    }

    public void reloadRuntime() {
        updateStatus("Reloading emulator…");
        webView.reload();
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    public void goBack() {
        webView.goBack();
    }

    @Override
    protected void onDetachedFromWindow() {
        webView.stopLoading();
        webView.removeJavascriptInterface("PebbleRearDisplay");
        webView.destroy();
        super.onDetachedFromWindow();
    }

    private void injectRearDisplayLayout() {
        String script = "(function(){"
                + "var hide=['h1','body>p','#controls','#progress-bar','#fps-counter','#buttons','.key-hint','#console-wrapper','body>div:last-child'];"
                + "hide.forEach(function(s){document.querySelectorAll(s).forEach(function(e){e.style.display='none';});});"
                + "document.documentElement.style='margin:0;background:#000;width:100%;height:100%;overflow:hidden';"
                + "document.body.style='margin:0;padding:0;background:#000;width:100%;height:100%;min-height:100%;overflow:hidden;display:flex;align-items:center;justify-content:center';"
                + "var wrapper=document.getElementById('display-wrapper');"
                + "if(wrapper){wrapper.style='margin:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#000';}"
                + "var canvas=document.getElementById('canvas');"
                + "if(canvas){canvas.style='display:block;border:0;border-radius:0;image-rendering:pixelated;max-width:100vw;max-height:100vh;width:auto;height:100vh;aspect-ratio:200/228;background:#000';}"
                + "var status=document.getElementById('status');"
                + "function report(){var text=status?status.textContent:'Page loaded';"
                + "PebbleRearDisplay.onStatus(text+' | isolated='+String(self.crossOriginIsolated));}"
                + "if(status){new MutationObserver(report).observe(status,{childList:true,subtree:true,characterData:true});}"
                + "report();"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void updateStatus(String status) {
        post(() -> {
            statusOverlay.setText(status);
            if (listener != null) {
                listener.onStatus(status);
            }
        });
    }

    private void fatal(String message) {
        post(() -> {
            statusOverlay.setText(message);
            statusOverlay.setTextColor(Color.rgb(255, 150, 150));
            if (listener != null) {
                listener.onFatalError(message);
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class RuntimeBridge {
        @JavascriptInterface
        public void onStatus(String status) {
            updateStatus(status == null || status.trim().isEmpty()
                    ? "PebbleOS emulator is running"
                    : status.trim());
        }
    }

    private final class EmulatorClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return uri == null
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || !ALLOWED_HOST.equalsIgnoreCase(uri.getHost());
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            updateStatus("Emulator page loaded; QEMU and PebbleOS are starting…");
            injectRearDisplayLayout();
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                fatal("Cannot load emulator: " + error.getDescription());
            }
        }

        @Override
        public void onSafeBrowsingHit(
                WebView view,
                WebResourceRequest request,
                int threatType,
                SafeBrowsingResponse callback
        ) {
            callback.backToSafety(true);
            fatal("WebView blocked the emulator page for safety");
        }
    }
}
