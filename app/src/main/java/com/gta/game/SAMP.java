package com.gta.game;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.graphics.Color;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import com.rockstargames.oswrapper.GameThread;
import org.json.JSONObject;
import androidx.constraintlayout.widget.ConstraintLayout;

//import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.joom.paranoid.Obfuscate;
import com.gta.game.ui.AttachEdit;
import com.gta.game.ui.LoadingScreen;
import com.gta.game.ui.dialog.DialogManager;

@Obfuscate
public class SAMP extends GTASA implements HeightProvider.HeightListener {

    private static final String TAG = "SAMP";
    private static SAMP instance;

    private DialogManager mDialog;
    private HeightProvider mHeightProvider;

    private AttachEdit mAttachEdit;
    private LoadingScreen mLoadingScreen;
    private HudWebView hudWebView;
    private EditText chatInput;
    private boolean configOpen;
    private boolean pharmacyOpen;
    private volatile int hudHunger = 100;
    private volatile int hudThirst = 100;
    private volatile int pharmacyBalance = 0;
    private String lastHudJson = "";
    private long lastHudDispatchMs;

    public static SAMP getInstance() {
        return instance;
    }


    private void showLoadingScreen() { }

    private void hideLoadingScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingScreen.hide();
            }
        });
    }

    public void setPauseState(boolean pause) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pause) {
                    mDialog.hideWithoutReset();
                    mAttachEdit.hideWithoutReset();
                } else {
                    if (mDialog.isShow)
                        mDialog.showWithOldContent();
                    if (mAttachEdit.isShow)
                        mAttachEdit.showWithoutReset();
                }
            }
        });
    }

    public void exitGame() {
        //FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);
        finishAndRemoveTask();
        System.exit(0);
    }

    public void showDialog(int dialogId, int dialogTypeId, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) {
        final String caption = new String(bArr);
        final String content = new String(bArr2);
        final String leftBtnText = new String(bArr3);
        final String rightBtnText = new String(bArr4);
        runOnUiThread(() -> { this.mDialog.show(dialogId, dialogTypeId, caption, content, leftBtnText, rightBtnText); });
    }
    private void showEditObject() {
        runOnUiThread(() -> mAttachEdit.show());
    }

    private void hideEditObject() {
        runOnUiThread(() -> mAttachEdit.hide());
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "**** onCreate");
        super.onCreate(savedInstanceState);

        mDialog     = new DialogManager(this);
        mAttachEdit = new AttachEdit(this);
        mLoadingScreen = new LoadingScreen(this);
        instance = this;
        initializeChatInput();
        initializeHudWebView();

        try {
            setNativeStoragePath(GetGameBaseDirectory());
            initializeSAMP();
        } catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }
    }

    private native void setNativeStoragePath(String path);
    private native void initializeSAMP();
    private native void openChatInput();
    private native void sendChatInput(String input);

    @Override
    public void onStart() {
        Log.i(TAG, "**** onStart");
        super.onStart();
    }

    @Override
    public void onRestart() {
        Log.i(TAG, "**** onRestart");
        super.onRestart();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "**** onResume");
        super.onResume();
        if (hudWebView != null) {
            hudWebView.onResume();
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.evaluateJavascript("window.HudBridge && HudBridge.setVisible(true)", null);
        }
    }

    @Override
    public void onPause() {
        Log.i(TAG, "**** onPause");
        if (hudWebView != null) {
            hudWebView.evaluateJavascript("window.HudBridge && HudBridge.setVisible(false)", null);
            hudWebView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "**** onStop");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "**** onDestroy");
        if (hudWebView != null) {
            hudWebView.loadUrl("about:blank");
            hudWebView.removeJavascriptInterface("AndroidHUD");
            hudWebView.destroy();
            hudWebView = null;
        }
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void initializeChatInput() {
        chatInput = new EditText(this);
        chatInput.setSingleLine(true);
        chatInput.setHint("Digite sua mensagem...");
        chatInput.setTextColor(Color.WHITE);
        chatInput.setHintTextColor(Color.LTGRAY);
        chatInput.setTextSize(16);
        chatInput.setPadding(dp(14), 0, dp(14), 0);
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        chatInput.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        chatInput.setVisibility(View.GONE);
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                submitChatInput();
                return true;
            }
            return false;
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM);
        params.leftMargin = dp(18);
        params.rightMargin = dp(18);
        params.bottomMargin = dp(12);
        addContentView(chatInput, params);
    }

    private void showChatInput() {
        runOnUiThread(() -> {
            if (chatInput == null) return;
            chatInput.setVisibility(View.VISIBLE);
            chatInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void submitChatInput() {
        if (chatInput == null) return;
        String value = chatInput.getText().toString().trim();
        chatInput.setText("");
        chatInput.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(chatInput.getWindowToken(), 0);
        if (!value.isEmpty()) sendChatInput(value);
    }

    private void initializeHudWebView() {
        hudWebView = findViewById(R.id.hudWebView);
        if (hudWebView == null) return;
        hudWebView.setBackgroundColor(Color.TRANSPARENT);
        hudWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        hudWebView.setWebViewClient(new WebViewClient());
        WebSettings settings = hudWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        hudWebView.addJavascriptInterface(new HudAndroidBridge(), "AndroidHUD");
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/hud/index.html");
    }

    private String payloadValue(String payload) {
        if (payload == null) return "";
        String marker = "\"value\":\"";
        int start = payload.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = payload.indexOf('\"', start);
        return end > start ? payload.substring(start, end) : "";
    }

    private void applyGameSurfaceLayout(int bufferWidth, int bufferHeight) {
        View game = findViewById(R.id.viewGame);
        if (game == null || bufferWidth <= 0 || bufferHeight <= 0) return;
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float scale = Math.min(1f, Math.min((float) metrics.widthPixels / bufferWidth, (float) metrics.heightPixels / bufferHeight));
        int viewWidth = Math.max(1, Math.round(bufferWidth * scale));
        int viewHeight = Math.max(1, Math.round(bufferHeight * scale));
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(viewWidth, viewHeight);
        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        game.setLayoutParams(params);
        GameThread.INSTANCE.setFixedSurfaceSize(bufferWidth, bufferHeight);
    }

    private void applyResolution(String value) {
        try {
            String[] parts = value.toLowerCase().split("x");
            if (parts.length != 2) return;
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width >= 640 && height >= 360) {
                applyGameSurfaceLayout(width, height);
                getSharedPreferences("display_settings", MODE_PRIVATE).edit()
                        .putString("resolution", value).apply();
            }
        } catch (Exception ignored) { }
    }

    private void applyResolutionScale(String value) {
        try {
            int percent = Math.max(50, Math.min(100, Integer.parseInt(value.trim())));
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = Math.max(640, Math.round(metrics.widthPixels * percent / 100f));
            int height = Math.max(360, Math.round(metrics.heightPixels * percent / 100f));
            applyGameSurfaceLayout(width, height);
            getSharedPreferences("display_settings", MODE_PRIVATE).edit()
                    .putInt("resolution_scale", percent).apply();
        } catch (Exception ignored) { }
    }

    private void applyAspect(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 2) return;
            float ratio = Float.parseFloat(parts[0]) / Float.parseFloat(parts[1]);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            int width = screenWidth;
            int height = Math.round(width / ratio);
            if (height > screenHeight) {
                height = screenHeight;
                width = Math.round(height * ratio);
            }
            applyGameSurfaceLayout(width, height);
            getSharedPreferences("display_settings", MODE_PRIVATE).edit()
                    .putString("aspect", value).apply();
        } catch (Exception ignored) { }
    }

    private void openConfig() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            configOpen = true;
            hudWebView.setWheelOpen(false);
            hudWebView.setFullscreenPage(true);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/config/index.html");
        });
    }

    private void openInteractionPage(final String assetPath) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            configOpen = true;
            pharmacyOpen = false;
            hudWebView.setWheelOpen(false);
            hudWebView.setPhonePage(false);
            hudWebView.setFullscreenPage(true);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/" + assetPath);
        });
    }

    private void openPhonePage() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            configOpen = true;
            pharmacyOpen = false;
            hudWebView.setWheelOpen(false);
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(true);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/phone/index.html");
        });
    }

    public void openPharmacy(final int balance, final int hunger, final int thirst) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            pharmacyOpen = true;
            configOpen = false;
            pharmacyBalance = Math.max(0, balance);
            hudHunger = Math.max(0, Math.min(100, hunger));
            hudThirst = Math.max(0, Math.min(100, thirst));
            hudWebView.setWheelOpen(false);
            hudWebView.setFullscreenPage(true);
            hudWebView.setVisibility(View.VISIBLE);
            String url = "file:///android_asset/pharmacy/index.html?balance=" + Math.max(0, balance) + "&hunger=" + Math.max(0, hunger) + "&thirst=" + Math.max(0, thirst);
            hudWebView.loadUrl(url);
        });
    }

    private void closePharmacy() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            pharmacyOpen = false;
            hudWebView.setFullscreenPage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            sendChatInput("/farmclose");
        });
    }

    private void closeConfig() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            configOpen = false;
            pharmacyOpen = false;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
        });
    }

    @Override
    public void onBackPressed() {
        if (pharmacyOpen) {
            closePharmacy();
            return;
        }
        if (configOpen) {
            closeConfig();
            return;
        }
        super.onBackPressed();
    }

    public void updateHudState(float health, float armour, float thirst, float hunger, float energy, boolean dead, boolean running, boolean exhausted, boolean inVehicle, float speed, float vehHealth, boolean handbrake) {
        String json = "{\"health\":" + health + ",\"armour\":" + armour + ",\"thirst\":" + hudThirst + ",\"hunger\":" + hudHunger + ",\"energy\":" + energy + ",\"dead\":" + dead + ",\"running\":" + running + ",\"exhausted\":" + exhausted + ",\"inVehicle\":" + inVehicle + ",\"speed\":" + speed + ",\"vehHealth\":" + vehHealth + ",\"fuel\":100,\"handbrake\":" + handbrake + "}";
        updateHudState(json);
    }

    public void updateHudState(final String json) {
        if (hudWebView == null || json == null || isFinishing()) return;
        final long now = SystemClock.uptimeMillis();
        if (json.equals(lastHudJson) && now - lastHudDispatchMs < 250L) return;
        lastHudJson = json;
        lastHudDispatchMs = now;
        runOnUiThread(() -> {
            if (hudWebView != null && !isFinishing()) {
                hudWebView.evaluateJavascript("window.HudBridge && HudBridge.setPlayerState(" + json + ")", null);
            }
        });
    }

    public void updateHudNeeds(final int hunger, final int thirst) {
        hudHunger = Math.max(0, Math.min(100, hunger));
        hudThirst = Math.max(0, Math.min(100, thirst));
        String json = "{\"hunger\":" + hudHunger + ",\"thirst\":" + hudThirst + "}";
        updateHudState(json);
        runOnUiThread(() -> {
            if (pharmacyOpen && hudWebView != null) {
                hudWebView.evaluateJavascript("window.PharmacyBridge && PharmacyBridge.setState(" + json.replace("hunger", "hunger").replace("thirst", "thirst") + ")", null);
            }
        });
    }

    public void updatePharmacyState(final int balance, final int hunger, final int thirst) {
        pharmacyBalance = Math.max(0, balance);
        hudHunger = Math.max(0, Math.min(100, hunger));
        hudThirst = Math.max(0, Math.min(100, thirst));
        runOnUiThread(() -> {
            if (!pharmacyOpen || hudWebView == null) return;
            String json = "{\"balance\":" + Math.max(0, balance) + ",\"hunger\":" + Math.max(0, hunger) + ",\"thirst\":" + Math.max(0, thirst) + "}";
            hudWebView.evaluateJavascript("window.PharmacyBridge && PharmacyBridge.setState(" + json + ")", null);
        });
    }

    private String payloadField(final String payload, final String field) {
        try {
            JSONObject object = new JSONObject(payload == null ? "{}" : payload);
            return object.optString(field, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private final class HudAndroidBridge {
        @JavascriptInterface
        public void onHudAction(final String action, final String payload) {
            if ("chat:open".equals(action)) {
                runOnUiThread(() -> openChatInput());
                return;
            }
            if ("config:open".equals(action)) {
                openConfig();
                return;
            }
            if ("phone:open".equals(action)) {
                openPhonePage();
                return;
            }
            if ("blazer:open".equals(action)) {
                openInteractionPage("blazer/index.html");
                return;
            }
            if ("settings:close".equals(action)) {
                closeConfig();
                return;
            }
            if ("settings:aspect".equals(action)) {
                applyAspect(payloadValue(payload));
                return;
            }
            if ("settings:resolution".equals(action)) {
                applyResolution(payloadValue(payload));
                return;
            }
            if ("settings:resolution-scale".equals(action)) {
                applyResolutionScale(payloadValue(payload));
                return;
            }
            if ("settings:fps".equals(action) || "settings:toggle".equals(action) || "settings:slider".equals(action)) {
                getSharedPreferences("display_settings", MODE_PRIVATE).edit()
                        .putString(action, payload == null ? "" : payload).apply();
                return;
            }
            if ("pharmacy:close".equals(action)) {
                closePharmacy();
                return;
            }
            if ("pharmacy:buy".equals(action)) {
                String item = payloadField(payload, "item");
                if (!item.isEmpty()) runOnUiThread(() -> sendChatInput("/farmbuy " + item));
                return;
            }
            if ("vehicle:mode".equals(action)) {
                boolean active = payload != null && payload.contains("\"active\":true");
                if (hudWebView != null) hudWebView.setVehicleMode(active);
                return;
            }
            if ("vehicle:control".equals(action)) {
                dispatchVehicleControl(payload);
                return;
            }
            if ("settings:reset".equals(action)) {
                applyGameSurfaceLayout(1600, 720);
                getSharedPreferences("display_settings", MODE_PRIVATE).edit().clear().apply();
                return;
            }
            if (hudWebView != null) {
                if ("wheel:open".equals(action)) hudWebView.setWheelOpen(true);
                if ("wheel:close".equals(action) || "wheel:select".equals(action)) hudWebView.setWheelOpen(false);
            }
            Log.d(TAG, "HUD action=" + action + " payload=" + payload);
        }
    }

    private void dispatchVehicleControl(String payload) {
        try {
            JSONObject data = new JSONObject(payload == null ? "{}" : payload);
            String control = data.optString("control", "");
            boolean pressed = data.optBoolean("pressed", false);
            int pointerId;
            float svgX;
            float svgY;
            if ("left".equals(control)) { pointerId = 201; svgX = 104f; svgY = 610f; }
            else if ("right".equals(control)) { pointerId = 202; svgX = 220f; svgY = 610f; }
            else if ("brake".equals(control)) { pointerId = 203; svgX = 1300f; svgY = 610f; }
            else if ("accelerate".equals(control)) { pointerId = 204; svgX = 1428f; svgY = 610f; }
            else if ("handbrake".equals(control)) { pointerId = 205; svgX = 1320f; svgY = 494f; }
            else return;
            View game = findViewById(R.id.viewGame);
            if (game == null || game.getWidth() <= 0 || game.getHeight() <= 0) return;
            float x = svgX * game.getWidth() / 1520f;
            float y = svgY * game.getHeight() / 720f;
            if (pressed) GameThread.INSTANCE.onTouchStart(pointerId, x, y);
            else GameThread.INSTANCE.onTouchEnd(pointerId, x, y);
        } catch (Exception ignored) { }
    }

    @Override
    public void onHeightChanged(int orientation, int height) { }
}
