package com.kamera.curing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
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
    private FrameLayout rootContainer;
    private WebView webView;
    private int statusInsetDp = 0;
    private int navInsetDp = 0;
    private int imeInsetDp = 0;

    private Object backCallbackObj = null;
    private boolean backCallbackRegistered = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FAF8F1"));
        window.setNavigationBarColor(Color.parseColor("#FAF8F1"));

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.parseColor("#FAF8F1"));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#FAF8F1"));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

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

        rootContainer.addView(
                webView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        setContentView(rootContainer);

        rootContainer.setOnApplyWindowInsetsListener((v, insets) -> {
            float density = getResources().getDisplayMetrics().density;
            int topPx;
            int navPx;
            int imePx = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                topPx = sys.top;
                navPx = sys.bottom;
                imePx = ime.bottom;
            } else {
                topPx = insets.getSystemWindowInsetTop();
                navPx = insets.getSystemWindowInsetBottom();
            }

            int bottomPaddingPx = imePx > 0 ? imePx : 0;
            if (v.getPaddingBottom() != bottomPaddingPx) {
                v.setPadding(0, 0, 0, bottomPaddingPx);
            }

            statusInsetDp = Math.round(topPx / density);
            navInsetDp = imePx > 0 ? 0 : Math.round(navPx / density);
            imeInsetDp = Math.round(imePx / density);
            pushInsetsToWeb();
            updateBackCallbackRegistration(imeInsetDp > 0 || backCallbackRegistered);
            return insets;
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            rootContainer.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Rect r = new Rect();
                rootContainer.getWindowVisibleDisplayFrame(r);
                int screenH = rootContainer.getRootView().getHeight();
                int keypadPx = screenH - r.bottom;
                float density = getResources().getDisplayMetrics().density;
                int newImeDp = keypadPx > screenH * 0.15 ? Math.round(keypadPx / density) : 0;
                if (newImeDp != imeInsetDp) {
                    imeInsetDp = newImeDp;
                    pushInsetsToWeb();
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallbackObj = (OnBackInvokedCallback) this::handleBackAction;
        }

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void updateBackCallbackRegistration(final boolean shouldIntercept) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backCallbackObj == null) {
            return;
        }
        runOnUiThread(() -> {
            try {
                OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
                OnBackInvokedCallback cb = (OnBackInvokedCallback) backCallbackObj;
                if (shouldIntercept && !backCallbackRegistered) {
                    dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb);
                    backCallbackRegistered = true;
                } else if (!shouldIntercept && backCallbackRegistered) {
                    dispatcher.unregisterOnBackInvokedCallback(cb);
                    backCallbackRegistered = false;
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void pushInsetsToWeb() {
        if (webView == null) return;
        final String js = "if(window.__setAndroidInsets){window.__setAndroidInsets("
                + statusInsetDp + "," + navInsetDp + "," + imeInsetDp + ");}";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void hideSoftKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View target = getCurrentFocus();
            if (target == null) target = webView;
            if (imm != null && target != null) {
                imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
            }
            if (webView != null) {
                webView.clearFocus();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleBackAction() {
        if (imeInsetDp > 0) {
            hideSoftKeyboard();
            return;
        }
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(
                "(function(){ return window.handleAndroidBack ? window.handleAndroidBack() : false; })()",
                value -> {
                    if (!"true".equals(value)) {
                        updateBackCallbackRegistration(false);
                        finish();
                    }
                }
        );
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBackAction();
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
        public void setCanGoBack(final boolean canGoBack) {
            updateBackCallbackRegistration(canGoBack || imeInsetDp > 0);
        }

        @JavascriptInterface
        public void hideKeyboard() {
            runOnUiThread(() -> hideSoftKeyboard());
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
