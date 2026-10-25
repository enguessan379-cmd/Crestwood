package com.gta.game;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.SurfaceView;
import java.io.ByteArrayOutputStream;
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
import android.view.ViewGroup;
import com.rockstargames.oswrapper.GameThread;
import org.json.JSONObject;
import androidx.constraintlayout.widget.ConstraintLayout;

// TODO: ajuster ce package selon celui fourni par le .aar sampmobilecef-...-release.aar
import com.akazuki.sampmobilecef.CefJavaManager;
import com.akazuki.sampmobilecef.CefClientManager;

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
    private boolean bankOpen;
    private volatile String pendingBankJson;
    private boolean cameraOpen;
    private volatile int hudHunger = 100;
    private volatile int hudThirst = 100;
    private volatile int pharmacyBalance = 0;
    private String lastHudJson = "";
    private long lastHudDispatchMs;
    // true des que le jeu est vivant (dialogue serveur recu ou HUD alimente) : l'ecran de chargement a ete ferme
    private volatile boolean loadingClosedByGame;

    private CefJavaManager mJavaManager;
    private CefClientManager mClientManager;

    public static SAMP getInstance() {
        return instance;
    }


    private void showLoadingScreen() {
        runOnUiThread(() -> { if (mLoadingScreen != null) mLoadingScreen.show(); });
    }

    private void hideLoadingScreen() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLoadingScreen != null) mLoadingScreen.hide();
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

                if (mJavaManager != null && mJavaManager.isShow()) {
                    if (pause)
                        mJavaManager.hideBrowserView();
                    else
                        mJavaManager.showBrowserView();
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
        // Banque : le serveur envoie l'etat en JSON dans un dialogue special (ids 9500-9502,
        // titre "@bank"). On ne l'affiche pas : il va a la page assets/bank/index.html.
        if (dialogId >= 9500 && dialogId <= 9502 && "@bank".equals(caption)) {
            handleBankDialog(dialogId, content);
            return;
        }
        // Le dialogue de connexion du serveur (login/inscription) s'affiche DERRIERE l'ecran de chargement :
        // sans cette ligne le joueur ne pouvait jamais le voir, donc jamais arriver au spawn.
        loadingClosedByGame = true;
        hideLoadingScreen();
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

        ViewGroup rootFrame = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        // CEF : les fonctions JNI (initJava...) n'existent qu'en armeabi-v7a (libSAMPMobileCef.a est 32-bit).
        // En arm64 le constructeur de CefClientManager lance un UnsatisfiedLinkError -> crash. On l'isole.
        try {
            mJavaManager = new CefJavaManager((FrameLayout) rootFrame, getInstance());
            mClientManager = new CefClientManager(getInstance());
            mJavaManager.setClientManager(mClientManager);
            mClientManager.setJavaManager(mJavaManager);
        } catch (Throwable t) {
            Log.w(TAG, "CEF indisponible sur cette ABI, ignoré : " + t);
            mJavaManager = null;
            mClientManager = null;
        }

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
    private native void openClothesTest();

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
        hudWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String json = pendingBankJson;
                if (json != null && url != null && url.endsWith("bank/index.html")) {
                    pendingBankJson = null;
                    view.evaluateJavascript("window.BankApp && BankApp.openFromNative(" + JSONObject.quote(json) + ")", null);
                }
            }
        });
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
        hudWebView.addJavascriptInterface(new SampHudBridge(), "SampHud");
        hudWebView.addJavascriptInterface(new SampBankBridge(), "SampBank");
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
        // Le buffer de rendu (résolution) peut être plus petit que l'écran : la vue doit alors être AGRANDIE pour remplir
        // l'écran (le compositeur étire la surface). Avant, le scale était plafonné à 1 -> la vue restait en petit
        // rectangle centré avec des bords blancs autour.
        float scale = Math.min((float) metrics.widthPixels / bufferWidth, (float) metrics.heightPixels / bufferHeight);
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

    // ---- Banque (page assets/bank/index.html) ----
    private void handleBankDialog(final int dialogId, final String json) {
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9502) {            // fermeture demandee par le serveur
                closeBankPage(false);
            } else if (dialogId == 9500) {     // ouverture
                openBankPage(json);
            } else if (bankOpen) {             // 9501 : mise a jour (solde, historique, message)
                hudWebView.evaluateJavascript("window.BankApp && BankApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openBankPage(String json) {
        if (hudWebView == null) return;
        bankOpen = true;
        configOpen = true;
        pharmacyOpen = false;
        pendingBankJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/bank/index.html");
    }

    private void closeBankPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = bankOpen;
            bankOpen = false;
            configOpen = false;
            pendingBankJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/bankact close 0 0 0");
        });
    }

    private final class SampBankBridge {
        // JS -> serveur : /bankact <type> <pin> <montant> <cible>
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z_]", "");
                if (t.isEmpty()) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                long pin = Math.max(0L, Math.min(9999L, o.optLong("pin", 0)));
                long amount = Math.max(0L, Math.min(2000000000L, o.optLong("amount", 0)));
                long target = Math.max(0L, Math.min(1000L, o.optLong("target", 0)));
                sendHudCommand("/bankact " + t + " " + pin + " " + amount + " " + target);
            } catch (Exception e) {
                Log.e(TAG, "bank action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeBankPage(true);
        }
    }

    private void closeConfig() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            configOpen = false;
            pharmacyOpen = false;
            bankOpen = false;
            cameraOpen = false;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
        });
    }

    @Override
    public void onBackPressed() {
        if (bankOpen) {
            closeBankPage(true);
            return;
        }
        if (cameraOpen) {
            closePhoneCamera();
            return;
        }
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

    public void updateHudState(float health, float armour, float thirst, float hunger, float energy, boolean dead, boolean running, boolean exhausted, boolean inVehicle, float speed, float vehHealth, boolean handbrake,
                               float posX, float posY, float camFx, float camFy, float heading) {
        String json = "{\"health\":" + health + ",\"armour\":" + armour + ",\"thirst\":" + hudThirst + ",\"hunger\":" + hudHunger + ",\"energy\":" + energy + ",\"dead\":" + dead + ",\"running\":" + running + ",\"exhausted\":" + exhausted + ",\"inVehicle\":" + inVehicle + ",\"speed\":" + speed + ",\"vehHealth\":" + vehHealth + ",\"fuel\":100,\"handbrake\":" + handbrake
                + ",\"posX\":" + posX + ",\"posY\":" + posY
                + ",\"camFx\":" + camFx + ",\"camFy\":" + camFy + ",\"heading\":" + heading + "}";
        updateHudState(json);
    }

    public void updateHudState(final String json) {
        if (hudWebView == null || json == null || isFinishing()) return;
        // Premier etat du joueur recu du moteur = le jeu tourne : on ferme l'ecran de chargement (une seule fois)
        if (!loadingClosedByGame) {
            loadingClosedByGame = true;
            hideLoadingScreen();
        }
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
            if ("camera:open".equals(action)) {
                openPhoneCamera();
                return;
            }
            if ("blazer:open".equals(action)) {
                openInteractionPage("blazer/index.html");
                return;
            }
            if ("map:open".equals(action)) {
                openInteractionPage("map/index.html");
                return;
            }
            if ("map:gps".equals(action)) {
                closeConfig();
                sendHudCommand(CMD_MAP);
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
                android.util.DisplayMetrics resetMetrics = getResources().getDisplayMetrics();
                applyGameSurfaceLayout(Math.max(640, resetMetrics.widthPixels), Math.max(360, resetMetrics.heightPixels));
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


    // ------------------------------------------------------------------
    // Caméra du téléphone Crestwood (capture de l'écran de jeu -> galerie Photos du téléphone)
    // ------------------------------------------------------------------
    private void openPhoneCamera() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            configOpen = true;
            pharmacyOpen = false;
            cameraOpen = true;
            hudWebView.setWheelOpen(false);
            hudWebView.setPhonePage(false);
            hudWebView.setFullscreenPage(false);
            hudWebView.setCameraPage(true); // viseur transparent : le tactile passe au jeu (glisser pour regarder)
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html?camera=phone");
        });
    }

    /** Ferme la caméra et revient sur le téléphone (la page Photos s'ouvre si une photo a été prise). */
    private void closePhoneCamera() {
        runOnUiThread(() -> {
            cameraOpen = false;
            openPhonePage();
        });
    }

    private void hudJs(final String script) {
        runOnUiThread(() -> {
            if (hudWebView != null && !isFinishing()) hudWebView.evaluateJavascript(script, null);
        });
    }

    private void cameraStatus(String status, String url, String message) {
        hudJs("window.HudApp && HudApp.rgCameraNativeStatus("
                + JSONObject.quote(status) + "," + JSONObject.quote(url == null ? "" : url) + ","
                + JSONObject.quote(message == null ? "" : message) + ")");
    }

    /** Copie la zone demandée (coordonnées CSS du HUD) depuis la SurfaceView du jeu. */
    private void captureGameRegion(final int left, final int top, final int width, final int height, final int innerW, final int innerH) {
        runOnUiThread(() -> {
            View gameView = findViewById(R.id.viewGame);
            if (!(gameView instanceof SurfaceView) || hudWebView == null || innerW <= 0 || innerH <= 0 || width <= 0 || height <= 0) {
                cameraStatus("error", "", "Impossible de capturer l'image.");
                return;
            }
            final SurfaceView game = (SurfaceView) gameView;
            Rect frame = game.getHolder().getSurfaceFrame();
            int surfaceW = frame.width();
            int surfaceH = frame.height();
            if (surfaceW <= 0 || surfaceH <= 0 || game.getWidth() <= 0 || game.getHeight() <= 0) {
                cameraStatus("error", "", "Le jeu n'est pas prêt.");
                return;
            }
            float sx = hudWebView.getWidth() / (float) innerW;
            float sy = hudWebView.getHeight() / (float) innerH;
            float fx0 = (left * sx - game.getLeft()) / game.getWidth();
            float fy0 = (top * sy - game.getTop()) / game.getHeight();
            float fx1 = ((left + width) * sx - game.getLeft()) / game.getWidth();
            float fy1 = ((top + height) * sy - game.getTop()) / game.getHeight();
            int x0 = Math.max(0, Math.min(surfaceW - 1, Math.round(fx0 * surfaceW)));
            int y0 = Math.max(0, Math.min(surfaceH - 1, Math.round(fy0 * surfaceH)));
            int x1 = Math.max(x0 + 2, Math.min(surfaceW, Math.round(fx1 * surfaceW)));
            int y1 = Math.max(y0 + 2, Math.min(surfaceH, Math.round(fy1 * surfaceH)));
            final Rect src = new Rect(x0, y0, x1, y1);
            int outW = Math.min(src.width(), 640);
            int outH = Math.max(1, Math.round(outW * (src.height() / (float) src.width())));
            final Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            cameraStatus("capturing", "", "");
            try {
                PixelCopy.request(game, src, bitmap, result -> {
                    if (result != PixelCopy.SUCCESS) {
                        bitmap.recycle();
                        cameraStatus("error", "", "Capture impossible, réessaie.");
                        return;
                    }
                    new Thread(() -> {
                        try {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out);
                            bitmap.recycle();
                            String url = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                            cameraStatus("captured", url, "");
                        } catch (Throwable t) {
                            cameraStatus("error", "", "Impossible d'enregistrer la photo.");
                        }
                    }, "hud-photo").start();
                }, new Handler(Looper.getMainLooper()));
            } catch (Throwable t) {
                Log.e(TAG, "PixelCopy failed", t);
                cameraStatus("error", "", "Capture impossible sur cet appareil.");
            }
        });
    }

    // ------------------------------------------------------------------
    // Pont natif du HUD Crestwood (window.SampHud côté JavaScript)
    // ------------------------------------------------------------------

    /**
     * Commandes de chat envoyées au serveur par les boutons du HUD.
     * ADAPTE CES VALEURS aux commandes réelles de ton gamemode PAWN ("" = ne rien envoyer).
     */
    private static final String CMD_ENGINE = "/moteur";
    private static final String CMD_VEHICLE_LOCK = "/lock";
    private static final String CMD_ANIMATIONS = "/anim";
    private static final String CMD_INVENTORY = "/inventaire";
    private static final String CMD_MAP = "/gps";
    private static final String CMD_STORE = "/boutique";
    private static final String CMD_AUDIO = "";
    private static final String CMD_CALL_ACCEPT = "";
    private static final String CMD_CALL_REJECT = "";
    /** Les actions "système" (boutique, objectifs, documents, Faggio...) passent par "/hudaction <action> <données>". */
    private static final String CMD_SYSTEM = "/hudaction";

    private void sendHudCommand(final String command) {
        if (command == null || command.trim().isEmpty()) return;
        runOnUiThread(() -> sendChatInput(command.trim()));
    }

    private SharedPreferences identityPrefs() {
        return getSharedPreferences("crestwood_hud_identity", MODE_PRIVATE);
    }

    private final class SampHudBridge {
        @JavascriptInterface public void ready() {
            SharedPreferences launcher = getSharedPreferences("crestwood_launcher", MODE_PRIVATE);
            String nick = launcher.getString("saved_nickname", "");
            JSONObject info = new JSONObject();
            try {
                if (nick != null && !nick.isEmpty()) info.put("name", nick);
                info.put("ready", 1);
            } catch (Exception ignored) { }
            hudJs("window.HudApp && HudApp.openFromNative(" + info + ")");
        }

        @JavascriptInterface public void setTouchBounds(String raw) {
            if (hudWebView != null) hudWebView.setTouchBounds(raw);
        }

        @JavascriptInterface public void setInteractionState(boolean chatOpen, boolean menuOpen) {
            if (hudWebView != null) hudWebView.setInteractive(chatOpen || menuOpen);
        }

        @JavascriptInterface public void setHudEditorOpen(boolean open) {
            if (hudWebView != null) hudWebView.setInteractive(open);
        }

        @JavascriptInterface public void setIdentityOpen(boolean open) {
            if (hudWebView != null) hudWebView.setInteractive(open);
        }

        @JavascriptInterface public void setIdentitySigning(boolean signing) {
            if (hudWebView != null) hudWebView.setInteractive(signing);
        }

        @JavascriptInterface public void showAndroidKeyboard() {
            runOnUiThread(() -> { if (hudWebView != null) hudWebView.showKeyboard(); });
        }

        @JavascriptInterface public void hideAndroidKeyboard() {
            runOnUiThread(() -> { if (hudWebView != null) hudWebView.hideKeyboard(); });
        }

        @JavascriptInterface public void sendChatMessage(String message) {
            if (message == null) return;
            final String clean = message.trim();
            if (!clean.isEmpty()) runOnUiThread(() -> sendChatInput(clean));
        }

        @JavascriptInterface public void menuAction(String action) {
            if (action == null) return;
            switch (action) {
                case "phone": openPhonePage(); break;
                case "settings": openConfig(); break;
                case "inventory": sendHudCommand(CMD_INVENTORY); break;
                case "map": openInteractionPage("map/index.html"); break;
                case "audio": sendHudCommand(CMD_AUDIO); break;
                case "store": sendHudCommand(CMD_STORE); break;
                case "records": openInteractionPage("blazer/index.html"); break;
                case "clothes": runOnUiThread(() -> openClothesTest()); break; // bouton de test : ouvre les vêtements sans serveur
                default: Log.d(TAG, "menuAction inconnue: " + action);
            }
        }

        @JavascriptInterface public void systemAction(String action, String payload) {
            if (action == null || action.isEmpty()) return;
            if ("close".equals(action)) return;
            sendHudCommand(CMD_SYSTEM + " " + action + (payload == null || payload.isEmpty() ? "" : " " + payload));
        }

        @JavascriptInterface public void engineAction(String action) { sendHudCommand(CMD_ENGINE); }
        @JavascriptInterface public void vehicleLockAction() { sendHudCommand(CMD_VEHICLE_LOCK); }
        @JavascriptInterface public void openAnimations() { sendHudCommand(CMD_ANIMATIONS); }

        @JavascriptInterface public void radioAction(String action, String value) {
            if (action == null || action.isEmpty()) return;
            sendHudCommand("/radioact " + action + (value == null || value.isEmpty() ? "" : " " + value));
        }

        @JavascriptInterface public void weaponAction(String action) {
            Log.d(TAG, "weaponAction=" + action);
        }

        @JavascriptInterface public void phoneCallAction(String action, String phone) {
            String base = "accept".equals(action) ? CMD_CALL_ACCEPT : CMD_CALL_REJECT;
            if (base != null && !base.isEmpty()) sendHudCommand(base + (phone == null || phone.isEmpty() ? "" : " " + phone));
            else Log.d(TAG, "phoneCallAction=" + action + " phone=" + phone);
        }

        // ---- Carte d'identité : stockage local (SharedPreferences) ----
        @JavascriptInterface public void saveIdentityPhoto(String value) {
            identityPrefs().edit().putString("photo", value == null ? "" : value).apply();
        }

        @JavascriptInterface public void saveIdentitySignature(String value) {
            identityPrefs().edit().putString("signature", value == null ? "" : value).apply();
        }

        @JavascriptInterface public void requestIdentityAssets() {
            SharedPreferences prefs = identityPrefs();
            String photo = prefs.getString("photo", "");
            String signature = prefs.getString("signature", "");
            try {
                if (!photo.isEmpty()) {
                    JSONObject event = new JSONObject().put("type", "photo").put("value", photo);
                    hudJs("window.HudApp && HudApp.receiveIdentityEvent(" + event + ")");
                }
                if (!signature.isEmpty()) {
                    hudJs("window.HudApp && HudApp.receiveIdentityEvent(" + new JSONObject().put("type", "signature_start").put("chunks", 1) + ")");
                    hudJs("window.HudApp && HudApp.receiveIdentityEvent(" + new JSONObject().put("type", "signature_chunk").put("idx", 0).put("value", signature) + ")");
                    hudJs("window.HudApp && HudApp.receiveIdentityEvent(" + new JSONObject().put("type", "signature_end") + ")");
                }
            } catch (Exception ignored) { }
        }

        // ---- Caméra (carte d'identité et téléphone) ----
        @JavascriptInterface public void openRgCamera() { }
        @JavascriptInterface public void closeRgCamera() { }

        @JavascriptInterface public void captureRgPhoto(int left, int top, int width, int height, int innerWidth, int innerHeight) {
            captureGameRegion(left, top, width, height, innerWidth, innerHeight);
        }

        /** Le déplacement du cadre pilote la caméra du jeu : non branché côté moteur natif pour l'instant. */
        @JavascriptInterface public void adjustRgCamera(int dx, int dy) { }

        /** Appelé par le HUD quand la caméra du téléphone se ferme (photo prise ou annulée). */
        @JavascriptInterface public void phoneCameraDone(boolean saved) { closePhoneCamera(); }
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
