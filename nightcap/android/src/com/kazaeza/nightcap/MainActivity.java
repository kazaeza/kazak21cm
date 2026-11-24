package com.kazaeza.nightcap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {

    private WebView webView;
    private float insetTopDp = 0f;
    private float insetBottomDp = 0f;
    private boolean pageLoaded = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupEdgeToEdge();

        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(Color.parseColor("#07070c"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        webView.addJavascriptInterface(new NightcapBridge(), "NightcapNative");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                pushInsetsToWeb();
            }
        });

        webView.setOnApplyWindowInsetsListener((v, insets) -> {
            float density = getResources().getDisplayMetrics().density;
            if (density <= 0f) density = 1f;

            int topPx = 0;
            int bottomPx = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                topPx = bars.top;
                bottomPx = Math.max(bars.bottom, ime.bottom);
            } else {
                topPx = insets.getSystemWindowInsetTop();
                bottomPx = insets.getSystemWindowInsetBottom();
            }

            insetTopDp = topPx / density;
            insetBottomDp = bottomPx / density;
            pushInsetsToWeb();
            return insets;
        });

        setContentView(webView);
        webView.requestApplyInsets();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        handleHardwareBack();
                    }
                }
            );
        }

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void setupEdgeToEdge() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void pushInsetsToWeb() {
        if (!pageLoaded || webView == null) return;
        final String js = "window.__nightcapApplyInsets && window.__nightcapApplyInsets("
            + Math.round(insetTopDp) + "," + Math.round(insetBottomDp) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void handleHardwareBack() {
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(
            "(function(){ return window.__nightcapBack ? Boolean(window.__nightcapBack()) : false; })()",
            result -> {
                if (!"true".equals(result)) {
                    finish();
                }
            }
        );
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleHardwareBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private class NightcapBridge {
        @JavascriptInterface
        public void haptic(String style) {
            try {
                Vibrator vibrator;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    vibrator = vm != null ? vm.getDefaultVibrator() : null;
                } else {
                    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                }
                if (vibrator == null || !vibrator.hasVibrator()) return;

                if ("blast".equals(style)) {
                    long[] timings = new long[]{0L, 14L, 28L, 42L, 24L, 16L};
                    int[] amplitudes = new int[]{0, 145, 0, 255, 0, 120};
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                    return;
                }

                long ms = 12L;
                int amp = 75;
                if ("medium".equals(style)) {
                    ms = 20L;
                    amp = 130;
                } else if ("success".equals(style)) {
                    ms = 26L;
                    amp = 180;
                }
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public String getInsets() {
            return "{\"top\":" + Math.round(insetTopDp) + ",\"bottom\":" + Math.round(insetBottomDp) + "}";
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> finish());
        }
    }
}
