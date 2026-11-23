package com.kamera.curing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String STATE_FILENAME = "kamera_batches_v1.json";
    private WebView webView;
    private int statusInsetDp = 0;
    private int navInsetDp = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FAF8F1"));
        window.setNavigationBarColor(Color.parseColor("#FAF8F1"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#FAF8F1"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(100);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pushInsetsToWeb();
            }
        });

        setContentView(webView);

        // Handle edge-to-edge insets cleanly on Android 15+ (targetSdk 35)
        View rootView = webView;
        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
            float density = getResources().getDisplayMetrics().density;
            int topPx;
            int bottomPx;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                topPx = sys.top;
                bottomPx = sys.bottom;
            } else {
                topPx = insets.getSystemWindowInsetTop();
                bottomPx = insets.getSystemWindowInsetBottom();
            }
            statusInsetDp = Math.round(topPx / density);
            navInsetDp = Math.round(bottomPx / density);
            pushInsetsToWeb();
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackAction
            );
        }

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void pushInsetsToWeb() {
        if (webView == null) return;
        final String js = "if(window.__setAndroidInsets){window.__setAndroidInsets(" + statusInsetDp + "," + navInsetDp + ");}";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void handleBackAction() {
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(
                "(function(){ return window.handleAndroidBack ? window.handleAndroidBack() : false; })()",
                value -> {
                    if (!"true".equals(value)) {
                        finish();
                    }
                }
        );
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleBackAction();
        } else {
            super.onBackPressed();
        }
    }

    private class AndroidBridge {

        @JavascriptInterface
        public String loadState() {
            File file = new File(getFilesDir(), STATE_FILENAME);
            if (!file.exists()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveState(String json) {
            File file = new File(getFilesDir(), STATE_FILENAME);
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void setStatusBar(final String hexColor, final boolean darkIcons) {
            runOnUiThread(() -> {
                try {
                    Window window = getWindow();
                    int color = Color.parseColor(hexColor);
                    window.setStatusBarColor(color);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowInsetsController ctrl = window.getInsetsController();
                        if (ctrl != null) {
                            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                            ctrl.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
                        }
                    } else {
                        View decor = window.getDecorView();
                        int flags = decor.getSystemUiVisibility();
                        if (darkIcons) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        } else {
                            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        }
                        decor.setSystemUiVisibility(flags);
                    }
                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public void haptic(final String kind) {
            try {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator == null || !vibrator.hasVibrator()) return;
                long ms = "success".equals(kind) ? 28 : ("medium".equals(kind) ? 18 : 10);
                int amp = "success".equals(kind) ? 160 : ("medium".equals(kind) ? 110 : 70);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
                } else {
                    vibrator.vibrate(ms);
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void shareText(final String title, final String text) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_SUBJECT, title);
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(intent, title));
                } catch (Exception ignored) {
                }
            });
        }
    }
}
