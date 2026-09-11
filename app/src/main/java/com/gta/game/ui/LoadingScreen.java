package com.gta.game.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.gta.game.R;
import com.rockstargames.oswrapper.DataInstaller;

public class LoadingScreen {
    private final Activity activity;
    private ConstraintLayout mainLayout;
    private WebView loadingWebView;
    private Handler handler;
    private boolean completed;

    public LoadingScreen(Activity activity) {
        this.activity = activity;
        mainLayout = (ConstraintLayout) activity.getLayoutInflater().inflate(R.layout.loadingscreen, null);
        activity.addContentView(mainLayout, new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT));
        initializeViews();
    }

    private void initializeViews() {
        handler = new Handler(Looper.getMainLooper());
        loadingWebView = mainLayout.findViewById(R.id.loadingWebView);
        if (loadingWebView == null) return;
        loadingWebView.setWebViewClient(new WebViewClient());
        WebSettings settings = loadingWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        loadingWebView.addJavascriptInterface(new LoadingBridge(), "AndroidLoading");
        loadingWebView.loadUrl("file:///android_asset/loading/index.html");

        DataInstaller.setListener(new DataInstaller.Listener() {
            @Override public void onProgress(final int percent, final String phase, final long done, final long total) {
                runOnUiThread(() -> evaluate("window.LoadingBridge&&LoadingBridge.setProgress(" + percent + "," + quote(phase) + "," + done + "," + total + ")"));
            }
            @Override public void onComplete(java.io.File root) {
                runOnUiThread(() -> {
                    completed = true;
                    evaluate("window.LoadingBridge&&LoadingBridge.ready()");
                    if (handler != null) handler.postDelayed(LoadingScreen.this::hide, 700L);
                });
            }
            @Override public void onError(final String message, Throwable error) {
                runOnUiThread(() -> evaluate("window.LoadingBridge&&LoadingBridge.error(" + quote(message) + ")"));
            }
        });
        DataInstaller.prepare(activity);
    }

    private void runOnUiThread(Runnable action) {
        if (activity == null || action == null) return;
        activity.runOnUiThread(action);
    }

    private void evaluate(String script) {
        if (loadingWebView != null && mainLayout != null && mainLayout.getVisibility() == View.VISIBLE) {
            loadingWebView.evaluateJavascript(script, null);
        }
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }

    public void hide() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (mainLayout != null) mainLayout.setVisibility(View.GONE);
    }

    public void show() {
        if (mainLayout != null) mainLayout.setVisibility(View.VISIBLE);
    }

    public void destroy() {
        hide();
        if (loadingWebView != null) {
            loadingWebView.stopLoading();
            loadingWebView.removeJavascriptInterface("AndroidLoading");
            loadingWebView.destroy();
            loadingWebView = null;
        }
        handler = null;
        mainLayout = null;
    }

    private final class LoadingBridge {
        @JavascriptInterface public void start() { DataInstaller.prepare(activity); }
        @JavascriptInterface public void retry() { DataInstaller.repair(activity); }
    }
}
