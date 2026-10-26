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
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
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
    // Ecran de connexion HTML (assets/login/index.html), pilote par les dialogues "@auth" (ids 9520-9522)
    private boolean authOpen;
    // Inventaire HTML (assets/inventory/index.html), pilote par les dialogues "@inv" (ids 9530-9532)
    private boolean invOpen;
    private volatile boolean invReady;
    private volatile String pendingInvJson;
    private Runnable invWatchdog;
    // Panneau d'administration HTML (assets/admin/index.html), pilote par les dialogues "@admin" (ids 9540-9542)
    private boolean adminOpen;
    private volatile boolean adminReady;
    private volatile String pendingAdminJson;
    private Runnable adminWatchdog;
    // Vestiaire de faction HTML (assets/locker/index.html), pilote par les dialogues "@locker" (ids 9560-9562)
    private boolean lockerOpen;
    private volatile boolean lockerReady;
    private volatile String pendingLockerJson;
    private Runnable lockerWatchdog;
    // Actions de police HTML (assets/police/index.html), pilote par les dialogues "@police" (ids 9570-9572)
    private boolean policeOpen;
    private volatile boolean policeReady;
    private volatile String pendingPoliceJson;
    private Runnable policeWatchdog;
    // Boutique 24/7 HTML (assets/shop/index.html), pilotee par les dialogues "@shop" (ids 9590-9592)
    private boolean shopOpen;
    private volatile boolean shopReady;
    private volatile String pendingShopJson;
    private Runnable shopWatchdog;
    // Contrat de travail / agence pour l'emploi HTML (assets/job/index.html), dialogues "@job" (ids 9600-9602)
    private boolean jobOpen;
    private volatile boolean jobReady;
    private volatile String pendingJobJson;
    private Runnable jobWatchdog;
    // Carte : icones fixes ("@blips", id 9605) et destination GPS ("@gps", id 9606) envoyees par le serveur,
    // gardees ici pour etre rejouees quand la page du HUD ou de la carte est rechargee.
    // Interfaces joueur HTML (assets/ui/index.html) : interaction, vehicule, papiers, permis, tenues ("@ui", 9630-9632)
    private boolean uiOpen;
    private volatile boolean uiReady;
    private volatile String pendingUiJson;
    private Runnable uiWatchdog;
    // Support / reports HTML (assets/report/index.html) : "@report" 9640-9642 (joueur), "@reports" 9643 (panneau admin)
    private boolean reportOpen;
    private volatile boolean reportReady;
    private volatile String pendingReportJson;
    private Runnable reportWatchdog;
    // Objectifs de mission (panneau du HUD) : "@obj" 9650, rejoue quand le HUD est recharge
    private volatile String lastObjJson = null;
    private volatile String lastBlipsJson = null;
    private volatile String lastGpsJson = null;      // {"x":..,"y":..,"auto":0|1} ou null
    private volatile float lastPosX = 0f, lastPosY = 0f;
    private volatile boolean hasLastPos = false;
    private volatile boolean authReady;
    // Redemande de l'ecran de connexion au serveur (le 1er dialogue peut arriver avant que le HUD soit pret)
    private boolean authBootstrapDone;
    private int authOpenAttempts;
    private volatile String pendingAuthJson;
    private final Handler authHandler = new Handler(Looper.getMainLooper());
    private Runnable authWatchdog;
    private volatile String pendingBankJson;
    private volatile boolean bankReady;
    private final Handler bankHandler = new Handler(Looper.getMainLooper());
    private Runnable bankWatchdog;
    // Panneau de vetements HTML (assets/clothes/index.html), pilote par le moteur natif (CharacterCreator)
    private boolean clothesPageOpen;
    private volatile boolean clothesReady;
    private volatile String pendingClothesJson;
    private final Handler clothesHandler = new Handler(Looper.getMainLooper());
    private Runnable clothesWatchdog;
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

    /** Dernier etat d'interaction recu du serveur : rejoue quand la page du HUD est rechargee (retour de la banque, du telephone...). */
    private volatile String lastInteractJson = null;

    public void showDialog(int dialogId, int dialogTypeId, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) {
        // Textes du serveur : UTF-8 s'ils sont valides, sinon Windows-1252 (accents francais Latin-1 des .pwn)
        final String caption = decodeSamp(bArr);
        final String content = decodeSamp(bArr2);
        final String leftBtnText = decodeSamp(bArr3);
        final String rightBtnText = decodeSamp(bArr4);
        // Interaction tactile : le serveur signale qu'un point d'interaction est proche (id 9510, titre
        // "@interact", contenu JSON {"show":1,"id":"bank","label":"Banque"} ou {"show":0}).
        // Rien n'est affiche en dialogue : l'icone de main du HUD apparait ou disparait.
        if (dialogId == 9510) {
            final String capInteract = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            if (capInteract.startsWith("@interact")) {
                final String interactJson = content == null ? "{}" : content.replace("\u0000", "").trim();
                lastInteractJson = interactJson;
                hudJs("window.HudBridge && HudBridge.setInteract && HudBridge.setInteract(" + JSONObject.quote(interactJson) + ")");
                return;
            }
        }
        // Boutique : ids 9590 (ouvrir), 9591 (mise a jour), 9592 (fermer), titre "@shop", contenu JSON.
        if (dialogId >= 9590 && dialogId <= 9592) {
            final String capShop = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean shopJson = content != null && content.trim().startsWith("{");
            if (capShop.startsWith("@shop") || shopJson) {
                handleShopDialog(dialogId, content);
                return;
            }
        }
        // Emplois : ids 9600 (ouvrir), 9601 (mise a jour), 9602 (fermer), titre "@job", contenu JSON.
        if (dialogId >= 9600 && dialogId <= 9602) {
            final String capJob = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean jobJson = content != null && content.trim().startsWith("{");
            if (capJob.startsWith("@job") || jobJson) {
                handleJobDialog(dialogId, content);
                return;
            }
        }
        // Interfaces joueur : ids 9630 (ouvrir), 9631 (mise a jour), 9632 (fermer), titre "@ui", contenu JSON.
        if (dialogId >= 9630 && dialogId <= 9632) {
            final String capUi = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean uiJson = content != null && content.trim().startsWith("{");
            if (capUi.startsWith("@ui") || uiJson) {
                handleUiDialog(dialogId, content);
                return;
            }
        }
        // Support : 9640 (ouvrir), 9641 (mise a jour), 9642 (fermer) "@report" ; 9643 "@reports" (panneau admin) ;
        // 9644 "@note" (notification du HUD) ; 9650 "@obj" (objectifs de mission). Contenu JSON, rien n'est affiche en dialogue.
        if ((dialogId >= 9640 && dialogId <= 9644) || dialogId == 9650) {
            final String capRep = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean repJson = content != null && content.trim().startsWith("{");
            if (capRep.startsWith("@report") || capRep.startsWith("@note") || capRep.startsWith("@obj") || repJson) {
                handleSupportDialog(dialogId, content);
                return;
            }
        }
        // Carte : icones fixes (9605 "@blips", liste JSON) et GPS (9606 "@gps", {"x","y"} ou {"clear":1}).
        if (dialogId == 9605 || dialogId == 9606) {
            final String capMap = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            if (capMap.startsWith("@blips") || capMap.startsWith("@gps")) {
                handleMapDialog(dialogId, content == null ? "" : content.replace("\u0000", "").trim());
                return;
            }
        }
        // Connexion / inscription : dialogues speciaux ids 9520 (ouvrir), 9521 (reponse du serveur), 9522 (fermer),
        // titre "@auth", contenu JSON. Rien n'est affiche en dialogue : on pilote assets/login/index.html.
        if (dialogId >= 9520 && dialogId <= 9522) {
            final String capAuth = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean authJson = content != null && content.trim().startsWith("{");
            if (capAuth.startsWith("@auth") || authJson) {
                Log.i(TAG, "[auth] dialogue " + dialogId + " intercepte");
                handleAuthDialog(dialogId, content);
                return;
            }
            Log.w(TAG, "[auth] dialogue " + dialogId + " NON intercepte, titre='" + caption + "'");
        }
        // Administration : ids 9540 (ouvrir), 9541 (mise a jour), 9542 (fermer), titre "@admin", contenu JSON.
        if (dialogId >= 9540 && dialogId <= 9542) {
            final String capAdm = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean admJson = content != null && content.trim().startsWith("{");
            if (capAdm.startsWith("@admin") || admJson) {
                handleAdminDialog(dialogId, content);
                return;
            }
        }
        // Actions de police : ids 9570 (ouvrir), 9571 (mise a jour), 9572 (fermer), titre "@police", contenu JSON.
        if (dialogId >= 9570 && dialogId <= 9572) {
            final String capPolice = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean policeJson = content != null && content.trim().startsWith("{");
            if (capPolice.startsWith("@police") || policeJson) {
                handlePoliceDialog(dialogId, content);
                return;
            }
        }
        // Vestiaire de faction : ids 9560 (ouvrir), 9561 (mise a jour), 9562 (fermer), titre "@locker", contenu JSON.
        if (dialogId >= 9560 && dialogId <= 9562) {
            final String capLocker = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean lockerJson = content != null && content.trim().startsWith("{");
            if (capLocker.startsWith("@locker") || lockerJson) {
                handleLockerDialog(dialogId, content);
                return;
            }
        }
        // Vetements : ids 9550 (appliquer l'apparence sauvegardee), 9551 (ouvrir l'editeur), 9552 (fermer),
        // titre "@clothes", contenu JSON. Rien n'est affiche en dialogue : le panneau natif prend le relais.
        if (dialogId >= 9550 && dialogId <= 9554) {
            final String capCloth = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean clothJson = content != null && content.trim().startsWith("{");
            if (capCloth.startsWith("@clothes") || clothJson) {
                String rawCloth = content == null ? "{}" : content.replace("\u0000", "").trim();
                int cs = rawCloth.indexOf('{');
                int ce = rawCloth.lastIndexOf('}');
                final String clothPayload = (cs >= 0 && ce > cs) ? rawCloth.substring(cs, ce + 1) : "{}";
                final int clothId = dialogId;
                try {
                    onClothesDialog(clothId, clothPayload);
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "[clothes] natif indisponible : " + e.getMessage());
                }
                return;
            }
        }
        // Inventaire : ids 9530 (ouvrir), 9531 (mise a jour), 9532 (fermer), titre "@inv", contenu JSON.
        if (dialogId >= 9530 && dialogId <= 9532) {
            final String capInv = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean invJson = content != null && content.trim().startsWith("{");
            if (capInv.startsWith("@inv") || invJson) {
                handleInvDialog(dialogId, content);
                return;
            }
        }
        // Banque : le serveur envoie l'etat en JSON dans un dialogue special (ids 9500-9502,
        // titre "@bank"). On ne l'affiche pas : il va a la page assets/bank/index.html.
        if (dialogId >= 9500 && dialogId <= 9502) {
            // titre nettoye (codes couleur, caracteres nuls, espaces) + secours : contenu JSON
            final String capClean = caption.replace("\u0000", "").replaceAll("\\{[0-9A-Fa-f]{6}\\}", "").trim();
            final boolean looksJson = content != null && content.trim().startsWith("{");
            if (capClean.startsWith("@bank") || looksJson) {
                Log.i(TAG, "[bank] dialogue " + dialogId + " intercepte, titre='" + capClean + "', "
                        + (content == null ? 0 : content.length()) + " car.");
                handleBankDialog(dialogId, content);
                return;
            }
            Log.w(TAG, "[bank] dialogue " + dialogId + " NON intercepte, titre='" + caption + "'");
        }
        // Le dialogue de connexion du serveur (login/inscription) s'affiche DERRIERE l'ecran de chargement :
        // sans cette ligne le joueur ne pouvait jamais le voir, donc jamais arriver au spawn.
        loadingClosedByGame = true;
        hideLoadingScreen();
        runOnUiThread(() -> { this.mDialog.show(dialogId, dialogTypeId, caption, content, leftBtnText, rightBtnText); });
    }
    private static String decodeSamp(byte[] b) {
        if (b == null) return "";
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b)).toString();
        } catch (Exception e) {
            try {
                return new String(b, "windows-1252");
            } catch (Exception e2) {
                return new String(b);
            }
        }
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
    /** Dialogues "@clothes" du serveur (ids 9550 appliquer, 9551 ouvrir l'editeur, 9552 fermer, 9553/9554 apparence d'un autre joueur : appliquer/retirer). */
    private native void onClothesDialog(int dialogId, String json);
    /** Commandes du panneau HTML des vetements : 0 option (key, value), 1 genre, 2 enregistrer, 3 annuler, 4 page indisponible. */
    private native void clothesCommand(int type, String key, int value);

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
            hudWebView.removeJavascriptInterface("AndroidClothes");
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
        hudWebView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("HUD-JS", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        hudWebView.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e(TAG, "[webview] erreur " + (request != null ? request.getUrl() : "?")
                        + " : " + (error != null ? error.getDescription() : "?"));
            }
            @Override public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "[webview] page chargee : " + url);
                String invJson = pendingInvJson;
                if (invJson != null && url != null && url.endsWith("inventory/index.html")) {
                    pendingInvJson = null;
                    view.evaluateJavascript("window.InvApp && InvApp.openFromNative(" + JSONObject.quote(invJson) + ")", null);
                }
                String admJson = pendingAdminJson;
                if (admJson != null && url != null && url.endsWith("admin/index.html")) {
                    pendingAdminJson = null;
                    view.evaluateJavascript("window.AdminApp && AdminApp.openFromNative(" + JSONObject.quote(admJson) + ")", null);
                }
                String policeJson = pendingPoliceJson;
                if (policeJson != null && url != null && url.endsWith("police/index.html")) {
                    pendingPoliceJson = null;
                    view.evaluateJavascript("window.PoliceApp && PoliceApp.openFromNative(" + JSONObject.quote(policeJson) + ")", null);
                }
                String shopJson = pendingShopJson;
                if (shopJson != null && url != null && url.endsWith("shop/index.html")) {
                    pendingShopJson = null;
                    view.evaluateJavascript("window.ShopApp && ShopApp.openFromNative(" + JSONObject.quote(shopJson) + ")", null);
                }
                String jobJson = pendingJobJson;
                if (jobJson != null && url != null && url.endsWith("job/index.html")) {
                    pendingJobJson = null;
                    view.evaluateJavascript("window.JobApp && JobApp.openFromNative(" + JSONObject.quote(jobJson) + ")", null);
                }
                String uiJsonPending = pendingUiJson;
                if (uiJsonPending != null && url != null && url.endsWith("ui/index.html")) {
                    pendingUiJson = null;
                    view.evaluateJavascript("window.UiApp && UiApp.openFromNative(" + JSONObject.quote(uiJsonPending) + ")", null);
                }
                if (url != null && (url.endsWith("hud/index.html") || url.endsWith("map/index.html"))) {
                    replayMapState();
                }
                String repJson = pendingReportJson;
                if (repJson != null && url != null && url.endsWith("report/index.html")) {
                    pendingReportJson = null;
                    view.evaluateJavascript("window.ReportApp && ReportApp.openFromNative(" + JSONObject.quote(repJson) + ")", null);
                }
                final String objJson = lastObjJson;
                if (objJson != null && url != null && url.endsWith("hud/index.html")) {
                    view.evaluateJavascript("window.ObjTracker && ObjTracker.set(" + JSONObject.quote(objJson) + ")", null);
                }
                String lockerJson = pendingLockerJson;
                if (lockerJson != null && url != null && url.endsWith("locker/index.html")) {
                    pendingLockerJson = null;
                    view.evaluateJavascript("window.LockerApp && LockerApp.openFromNative(" + JSONObject.quote(lockerJson) + ")", null);
                }
                String authJson = pendingAuthJson;
                if (authJson != null && url != null && url.endsWith("login/index.html")) {
                    pendingAuthJson = null;
                    view.evaluateJavascript("window.DXAuth && DXAuth.openFromNative(" + JSONObject.quote(authJson) + ")", null);
                }
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
        // La musique de l'ecran de connexion doit pouvoir demarrer sans toucher l'ecran
        settings.setMediaPlaybackRequiresUserGesture(false);
        hudWebView.addJavascriptInterface(new HudAndroidBridge(), "AndroidHUD");
        hudWebView.addJavascriptInterface(new SampAuthBridge(), "SampAuth");
        hudWebView.addJavascriptInterface(new SampInvBridge(), "SampInv");
        hudWebView.addJavascriptInterface(new SampAdminBridge(), "SampAdmin");
        hudWebView.addJavascriptInterface(new SampLockerBridge(), "SampLocker");
        hudWebView.addJavascriptInterface(new SampPoliceBridge(), "SampPolice");
        hudWebView.addJavascriptInterface(new SampShopBridge(), "SampShop");
        hudWebView.addJavascriptInterface(new SampJobBridge(), "SampJob");
        hudWebView.addJavascriptInterface(new SampUiBridge(), "SampUi");
        hudWebView.addJavascriptInterface(new SampReportBridge(), "SampReport");
        hudWebView.addJavascriptInterface(new SampHudBridge(), "SampHud");
        hudWebView.addJavascriptInterface(new SampBankBridge(), "SampBank");
        hudWebView.addJavascriptInterface(new SampClothesBridge(), "AndroidClothes");
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

    // ---- Inventaire (page assets/inventory/index.html) ----
    private void handleInvDialog(final int dialogId, final String rawJson) {
        String tmp = rawJson == null ? "{}" : rawJson;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        final String json = (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9532) {            // fermeture demandee par le serveur
                closeInvPage(false);
            } else if (dialogId == 9530) {     // ouverture
                openInvPage(json);
            } else if (invOpen) {              // 9531 : mise a jour (objets, message)
                hudWebView.evaluateJavascript("window.InvApp && InvApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openInvPage(String json) {
        if (hudWebView == null || authOpen) return;
        if (invOpen && invReady) {
            hudWebView.evaluateJavascript("window.InvApp && InvApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[inv] ouverture de l'inventaire");
        invOpen = true;
        invReady = false;
        configOpen = true;
        bankOpen = false;
        pharmacyOpen = false;
        pendingInvJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/inventory/index.html");
        if (invWatchdog != null) authHandler.removeCallbacks(invWatchdog);
        invWatchdog = () -> {
            if (invOpen && !invReady) {
                Log.e(TAG, "[inv] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeInvPage(true);
            }
        };
        authHandler.postDelayed(invWatchdog, 5000);
    }

    private void closeInvPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = invOpen;
            if (invWatchdog != null) { authHandler.removeCallbacks(invWatchdog); invWatchdog = null; }
            invOpen = false;
            invReady = false;
            configOpen = false;
            pendingInvJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/invact close x 0");
        });
    }

    private final class SampInvBridge {
        // JS -> serveur : /invact <use|drop> <objet> <quantite>
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                if (!t.equals("use") && !t.equals("drop")) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                String item = o.optString("item", "").replaceAll("[^a-z_]", "");
                long qty = Math.max(1L, Math.min(99L, o.optLong("qty", 1)));
                if (item.isEmpty() || item.length() > 23) return;
                sendHudCommand("/invact " + t + " " + item + " " + qty);
            } catch (Exception e) {
                Log.e(TAG, "inv action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeInvPage(true);
        }

        @JavascriptInterface public void ready() {
            invReady = true;
            Log.i(TAG, "[inv] page affichee");
        }
    }

    // ---- Administration (page assets/admin/index.html) ----
    private void handleAdminDialog(final int dialogId, final String rawJson) {
        String tmp = rawJson == null ? "{}" : rawJson;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        final String json = (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9542) {            // fermeture demandee par le serveur
                closeAdminPage(false);
            } else if (dialogId == 9540) {     // ouverture (/adminpanel)
                openAdminPage(json);
            } else if (adminOpen) {            // 9541 : mise a jour (joueurs, message)
                hudWebView.evaluateJavascript("window.AdminApp && AdminApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openAdminPage(String json) {
        if (hudWebView == null || authOpen) return;
        if (adminOpen && adminReady) {
            hudWebView.evaluateJavascript("window.AdminApp && AdminApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[admin] ouverture du panneau admin");
        adminOpen = true;
        adminReady = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingAdminJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/admin/index.html");
        if (adminWatchdog != null) authHandler.removeCallbacks(adminWatchdog);
        adminWatchdog = () -> {
            if (adminOpen && !adminReady) {
                Log.e(TAG, "[admin] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeAdminPage(true);
            }
        };
        authHandler.postDelayed(adminWatchdog, 5000);
    }

    private void closeAdminPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = adminOpen;
            if (adminWatchdog != null) { authHandler.removeCallbacks(adminWatchdog); adminWatchdog = null; }
            adminOpen = false;
            adminReady = false;
            configOpen = false;
            pendingAdminJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/adminact close 0");
        });
    }

    private final class SampAdminBridge {
        // JS -> serveur : /adminact <action> <id> <texte>. Le serveur re-verifie le niveau a chaque action.
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                if (t.isEmpty() || t.length() > 12) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                long id = Math.max(0L, Math.min(9999L, o.optLong("id", 0)));
                String arg = o.optString("arg", "")
                        .replaceAll("[\\p{Cntrl}\"\\\\%]", " ")
                        .trim();
                if (arg.length() > 90) arg = arg.substring(0, 90);
                sendHudCommand("/adminact " + t + " " + id + (arg.isEmpty() ? "" : " " + arg));
            } catch (Exception e) {
                Log.e(TAG, "admin action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeAdminPage(true);
        }

        @JavascriptInterface public void ready() {
            adminReady = true;
            Log.i(TAG, "[admin] page affichee");
        }
    }

    // ---- Actions de police (page assets/police/index.html) ----
    // Le bouton "Police" du HUD (icone bouclier) apparait quand un policier est a cote d'un joueur ; un appui
    // envoie /interagir, le serveur ouvre alors cette page (dialogue 9570) avec la cible la plus proche.
    private void handlePoliceDialog(final int dialogId, final String rawJson) {
        String tmp = rawJson == null ? "{}" : rawJson;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        final String json = (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9572) {            // fermeture demandee par le serveur (cible perdue, menotte...)
                closePolicePage(false);
            } else if (dialogId == 9570) {     // ouverture
                openPolicePage(json);
            } else if (policeOpen) {           // 9571 : mise a jour (etat de la cible, vue, message)
                hudWebView.evaluateJavascript("window.PoliceApp && PoliceApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openPolicePage(String json) {
        if (hudWebView == null || authOpen || adminOpen || lockerOpen) return;
        if (policeOpen && policeReady) {
            hudWebView.evaluateJavascript("window.PoliceApp && PoliceApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[police] ouverture du menu d'actions");
        policeOpen = true;
        policeReady = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingPoliceJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/police/index.html");
        if (policeWatchdog != null) authHandler.removeCallbacks(policeWatchdog);
        policeWatchdog = () -> {
            if (policeOpen && !policeReady) {
                Log.e(TAG, "[police] la page ne s'est pas affichee a temps -> fermeture de secours");
                closePolicePage(true);
            }
        };
        authHandler.postDelayed(policeWatchdog, 5000);
    }

    private void closePolicePage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = policeOpen;
            if (policeWatchdog != null) { authHandler.removeCallbacks(policeWatchdog); policeWatchdog = null; }
            policeOpen = false;
            policeReady = false;
            configOpen = false;
            pendingPoliceJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/policeact close");
        });
    }

    private final class SampPoliceBridge {
        // JS -> serveur : /policeact <action> [arguments numeriques]. Le serveur re-verifie faction, grade,
        // distance et etat de la cible a chaque action : seule une liste blanche d'actions est transmise.
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                switch (t) {
                    case "refresh": case "id": case "papers": case "search":
                    case "cuff": case "uncuff": case "drag": case "undrag":
                    case "licwd": case "licret": case "charge": case "fine": case "arrest":
                        break;
                    default:
                        return;
                }
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                String arg = o.optString("arg", "").replaceAll("[^0-9 ]", "").trim();
                if (arg.length() > 20) arg = arg.substring(0, 20);
                sendHudCommand("/policeact " + t + (arg.isEmpty() ? "" : " " + arg));
            } catch (Exception e) {
                Log.e(TAG, "police action", e);
            }
        }

        @JavascriptInterface public void close() {
            closePolicePage(true);
        }

        @JavascriptInterface public void ready() {
            policeReady = true;
            runOnUiThread(() -> { if (policeWatchdog != null) { authHandler.removeCallbacks(policeWatchdog); policeWatchdog = null; } });
            Log.i(TAG, "[police] page affichee");
        }
    }

    // ---- Carte : icones fixes + GPS (minimap du HUD et page assets/map/index.html) ----
    private void handleMapDialog(final int dialogId, final String raw) {
        if (dialogId == 9605) {
            int s0 = raw.indexOf('['), s1 = raw.lastIndexOf(']');
            try {
                lastBlipsJson = new org.json.JSONArray((s0 >= 0 && s1 > s0) ? raw.substring(s0, s1 + 1) : "[]").toString();
            } catch (Exception e) {
                Log.e(TAG, "[blips] JSON invalide", e);
                return;
            }
        } else {
            try {
                int s0 = raw.indexOf('{'), s1 = raw.lastIndexOf('}');
                JSONObject o = new JSONObject((s0 >= 0 && s1 > s0) ? raw.substring(s0, s1 + 1) : "{}");
                if (o.has("x") && o.has("y")) {
                    JSONObject g = new JSONObject();
                    g.put("x", o.getDouble("x"));
                    g.put("y", o.getDouble("y"));
                    g.put("auto", 0);
                    lastGpsJson = g.toString();
                } else {
                    lastGpsJson = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "[gps] JSON invalide", e);
                return;
            }
        }
        replayMapState();
    }

    /** Pousse les icones, le GPS et la position connus vers la page affichee (HUD ou carte). */
    private void replayMapState() {
        final String blips = lastBlipsJson;
        final String gps = lastGpsJson;
        StringBuilder js = new StringBuilder();
        if (blips != null) {
            js.append("window.MinimapBridge && MinimapBridge.setBlips(").append(blips).append(");");
            js.append("window.MapApp && MapApp.setBlips && MapApp.setBlips(").append(blips).append(");");
        }
        if (gps != null) {
            js.append("(function(g){window.MinimapBridge && MinimapBridge.setWaypoint(g.x, g.y, g.auto === 1);")
              .append("window.MapApp && MapApp.setGps && MapApp.setGps(g.x, g.y);})(").append(gps).append(");");
        } else {
            js.append("window.MinimapBridge && MinimapBridge.clearWaypoint();");
            js.append("window.MapApp && MapApp.clearGps && MapApp.clearGps();");
        }
        if (hasLastPos) {
            js.append("window.MapApp && MapApp.setPlayer(").append(lastPosX).append(",").append(lastPosY).append(");");
        }
        final String script = js.toString();
        runOnUiThread(() -> {
            if (hudWebView != null && !isFinishing()) hudWebView.evaluateJavascript(script, null);
        });
    }

    // ---- Support / reports (page assets/report/index.html + onglet Reports du panneau admin) ----
    private void handleSupportDialog(final int dialogId, final String rawJson) {
        final String json = extractJson(rawJson);
        if (dialogId == 9650) {                        // objectifs de mission
            boolean show = json.contains("\"show\":1");
            lastObjJson = show ? json : null;
            hudJs("window.ObjTracker && ObjTracker.set(" + JSONObject.quote(json) + ")");
            return;
        }
        if (dialogId == 9644) {                        // notification en bas de l'ecran
            hudJs("window.HudApp && HudApp.receiveGameNotification && HudApp.receiveGameNotification(" + JSONObject.quote(json) + ")");
            hudJs("window.ReportApp && ReportApp.onNote && ReportApp.onNote(" + JSONObject.quote(json) + ")");
            return;
        }
        if (dialogId == 9643) {                        // panneau admin : liste des reports + conversation
            runOnUiThread(() -> {
                if (adminOpen && hudWebView != null && !isFinishing()) {
                    hudWebView.evaluateJavascript("window.AdminApp && AdminApp.onReports && AdminApp.onReports(" + JSONObject.quote(json) + ")", null);
                }
            });
            return;
        }
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9642) {
                closeReportPage(false);
            } else if (dialogId == 9640) {
                openReportPage(json);
            } else if (reportOpen) {
                hudWebView.evaluateJavascript("window.ReportApp && ReportApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openReportPage(String json) {
        if (hudWebView == null || authOpen) return;
        if (reportOpen && reportReady) {
            hudWebView.evaluateJavascript("window.ReportApp && ReportApp.openFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        if (reportOpen) {                              // page en cours de chargement : on garde le dernier etat
            pendingReportJson = json;
            return;
        }
        Log.i(TAG, "[report] ouverture du support");
        reportOpen = true;
        reportReady = false;
        adminOpen = false;
        uiOpen = false;
        shopOpen = false;
        jobOpen = false;
        lockerOpen = false;
        policeOpen = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingReportJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/report/index.html");
        if (reportWatchdog != null) authHandler.removeCallbacks(reportWatchdog);
        reportWatchdog = () -> {
            if (reportOpen && !reportReady) {
                Log.e(TAG, "[report] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeReportPage(true);
            }
        };
        authHandler.postDelayed(reportWatchdog, 5000);
    }

    private void closeReportPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = reportOpen;
            if (reportWatchdog != null) { authHandler.removeCallbacks(reportWatchdog); reportWatchdog = null; }
            reportOpen = false;
            reportReady = false;
            configOpen = false;
            pendingReportJson = null;
            hudWebView.hideKeyboard();
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/repact hide");
        });
    }

    /** Texte libre d'un message de support : une ligne, sans guillemet / antislash / %, 100 caracteres max. */
    private static String cleanSupportText(String value) {
        if (value == null) return "";
        String v = value.replaceAll("[\\p{Cntrl}\"\\\\%]", " ").replaceAll("\\s+", " ").trim();
        return v.length() > 100 ? v.substring(0, 100).trim() : v;
    }

    private final class SampReportBridge {
        // JS -> serveur : /repact new <categorie> <id> <texte> | msg <texte> | cancel | open
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                switch (t) {
                    case "new": {
                        long cat = Math.max(0L, Math.min(4L, o.optLong("cat", 4)));
                        long target = Math.max(-1L, Math.min(999L, o.optLong("target", -1)));
                        String text = cleanSupportText(o.optString("text", ""));
                        if (text.isEmpty()) return;
                        sendHudCommand("/repact new " + cat + " " + target + " " + text);
                        break;
                    }
                    case "msg": {
                        String text = cleanSupportText(o.optString("text", ""));
                        if (!text.isEmpty()) sendHudCommand("/repact msg " + text);
                        break;
                    }
                    case "cancel":
                    case "open":
                        sendHudCommand("/repact " + t);
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "report action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeReportPage(true);
        }

        @JavascriptInterface public void showKeyboard() {
            runOnUiThread(() -> { if (hudWebView != null) hudWebView.showKeyboard(); });
        }

        @JavascriptInterface public void hideKeyboard() {
            runOnUiThread(() -> { if (hudWebView != null) hudWebView.hideKeyboard(); });
        }

        @JavascriptInterface public void ready() {
            reportReady = true;
            runOnUiThread(() -> {
                if (reportWatchdog != null) { authHandler.removeCallbacks(reportWatchdog); reportWatchdog = null; }
                // etat arrive pendant le chargement de la page
                String pending = pendingReportJson;
                pendingReportJson = null;
                if (pending != null && reportOpen && hudWebView != null) {
                    hudWebView.evaluateJavascript("window.ReportApp && ReportApp.openFromNative(" + JSONObject.quote(pending) + ")", null);
                }
            });
            Log.i(TAG, "[report] page affichee");
        }
    }

    // ---- Boutique 24/7 (page assets/shop/index.html) ----
    private static String extractJson(String raw) {
        String tmp = raw == null ? "{}" : raw;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        return (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
    }

    private void handleShopDialog(final int dialogId, final String rawJson) {
        final String json = extractJson(rawJson);
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9592) {
                closeShopPage(false);
            } else if (dialogId == 9590) {
                openShopPage(json);
            } else if (shopOpen) {
                hudWebView.evaluateJavascript("window.ShopApp && ShopApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openShopPage(String json) {
        if (hudWebView == null || authOpen || adminOpen) return;
        if (shopOpen && shopReady) {
            hudWebView.evaluateJavascript("window.ShopApp && ShopApp.openFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[shop] ouverture de la boutique");
        shopOpen = true;
        shopReady = false;
        jobOpen = false;
        lockerOpen = false;
        policeOpen = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingShopJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/shop/index.html");
        if (shopWatchdog != null) authHandler.removeCallbacks(shopWatchdog);
        shopWatchdog = () -> {
            if (shopOpen && !shopReady) {
                Log.e(TAG, "[shop] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeShopPage(true);
            }
        };
        authHandler.postDelayed(shopWatchdog, 5000);
    }

    private void closeShopPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = shopOpen;
            if (shopWatchdog != null) { authHandler.removeCallbacks(shopWatchdog); shopWatchdog = null; }
            shopOpen = false;
            shopReady = false;
            configOpen = false;
            pendingShopJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/shopact close");
        });
    }

    private final class SampShopBridge {
        // JS -> serveur : /shopact buy <article> <quantite> <cash|card> | refresh. Le serveur re-verifie tout.
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                if (!t.equals("buy") && !t.equals("refresh")) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                String arg = o.optString("arg", "").replaceAll("[^a-z0-9_ ]", "").trim();
                if (arg.length() > 48) arg = arg.substring(0, 48);
                sendHudCommand("/shopact " + t + (arg.isEmpty() ? "" : " " + arg));
            } catch (Exception e) {
                Log.e(TAG, "shop action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeShopPage(true);
        }

        @JavascriptInterface public void ready() {
            shopReady = true;
            Log.i(TAG, "[shop] page affichee");
        }
    }

    // ---- Interfaces joueur (page assets/ui/index.html) ----
    private void handleUiDialog(final int dialogId, final String rawJson) {
        final String json = extractJson(rawJson);
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9632) {
                closeUiPage(false);
            } else if (dialogId == 9630) {
                openUiPage(json);
            } else if (uiOpen) {
                hudWebView.evaluateJavascript("window.UiApp && UiApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openUiPage(String json) {
        if (hudWebView == null || authOpen || adminOpen) return;
        if (uiOpen && uiReady) {
            hudWebView.evaluateJavascript("window.UiApp && UiApp.openFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[ui] ouverture d'une interface joueur");
        uiOpen = true;
        uiReady = false;
        shopOpen = false;
        jobOpen = false;
        lockerOpen = false;
        policeOpen = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingUiJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/ui/index.html");
        if (uiWatchdog != null) authHandler.removeCallbacks(uiWatchdog);
        uiWatchdog = () -> {
            if (uiOpen && !uiReady) {
                Log.e(TAG, "[ui] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeUiPage(true);
            }
        };
        authHandler.postDelayed(uiWatchdog, 5000);
    }

    private void closeUiPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = uiOpen;
            if (uiWatchdog != null) { authHandler.removeCallbacks(uiWatchdog); uiWatchdog = null; }
            uiOpen = false;
            uiReady = false;
            configOpen = false;
            pendingUiJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/uiact close");
        });
    }

    private final class SampUiBridge {
        // JS -> serveur : /uiact <action>. Le serveur re-verifie distance, faction et vehicule.
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z_]", "");
                if (t.isEmpty() || t.length() > 16) return;
                sendHudCommand("/uiact " + t);
            } catch (Exception e) {
                Log.e(TAG, "ui action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeUiPage(true);
        }

        @JavascriptInterface public void ready() {
            uiReady = true;
            Log.i(TAG, "[ui] page affichee");
        }
    }

    // ---- Emplois : contrat de travail + agence (page assets/job/index.html) ----
    private void handleJobDialog(final int dialogId, final String rawJson) {
        final String json = extractJson(rawJson);
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9602) {
                closeJobPage(false);
            } else if (dialogId == 9600) {
                openJobPage(json);
            } else if (jobOpen) {
                hudWebView.evaluateJavascript("window.JobApp && JobApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openJobPage(String json) {
        if (hudWebView == null || authOpen || adminOpen) return;
        if (jobOpen && jobReady) {
            hudWebView.evaluateJavascript("window.JobApp && JobApp.openFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[job] ouverture du contrat / de l'agence");
        jobOpen = true;
        jobReady = false;
        shopOpen = false;
        lockerOpen = false;
        policeOpen = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingJobJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/job/index.html");
        if (jobWatchdog != null) authHandler.removeCallbacks(jobWatchdog);
        jobWatchdog = () -> {
            if (jobOpen && !jobReady) {
                Log.e(TAG, "[job] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeJobPage(true);
            }
        };
        authHandler.postDelayed(jobWatchdog, 5000);
    }

    private void closeJobPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = jobOpen;
            if (jobWatchdog != null) { authHandler.removeCallbacks(jobWatchdog); jobWatchdog = null; }
            jobOpen = false;
            jobReady = false;
            configOpen = false;
            pendingJobJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/jobact close");
        });
    }

    private final class SampJobBridge {
        // JS -> serveur : /jobact <sign|start|stop|resign|gps|list|refresh> [id]. Le serveur re-verifie tout.
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                if (!t.equals("sign") && !t.equals("start") && !t.equals("stop") && !t.equals("resign")
                        && !t.equals("gps") && !t.equals("list") && !t.equals("refresh")) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                String arg = o.optString("arg", "").replaceAll("[^0-9]", "");
                if (arg.length() > 3) arg = arg.substring(0, 3);
                sendHudCommand("/jobact " + t + (arg.isEmpty() ? "" : " " + arg));
            } catch (Exception e) {
                Log.e(TAG, "job action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeJobPage(true);
        }

        @JavascriptInterface public void ready() {
            jobReady = true;
            Log.i(TAG, "[job] page affichee");
        }
    }

    // ---- Vestiaire de faction (page assets/locker/index.html) ----
    private void handleLockerDialog(final int dialogId, final String rawJson) {
        String tmp = rawJson == null ? "{}" : rawJson;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        final String json = (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) return;
            if (dialogId == 9562) {            // fermeture demandee par le serveur
                closeLockerPage(false);
            } else if (dialogId == 9560) {     // ouverture (icone de main "Vestiaire" ou /vestiaire)
                openLockerPage(json);
            } else if (lockerOpen) {           // 9561 : mise a jour (tenue, message)
                hudWebView.evaluateJavascript("window.LockerApp && LockerApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openLockerPage(String json) {
        if (hudWebView == null || authOpen || adminOpen) return;
        if (lockerOpen && lockerReady) {
            hudWebView.evaluateJavascript("window.LockerApp && LockerApp.onUpdateFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[locker] ouverture du vestiaire");
        lockerOpen = true;
        lockerReady = false;
        configOpen = true;
        invOpen = false;
        bankOpen = false;
        pharmacyOpen = false;
        pendingLockerJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/locker/index.html");
        if (lockerWatchdog != null) authHandler.removeCallbacks(lockerWatchdog);
        lockerWatchdog = () -> {
            if (lockerOpen && !lockerReady) {
                Log.e(TAG, "[locker] la page ne s'est pas affichee a temps -> fermeture de secours");
                closeLockerPage(true);
            }
        };
        authHandler.postDelayed(lockerWatchdog, 5000);
    }

    private void closeLockerPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = lockerOpen;
            if (lockerWatchdog != null) { authHandler.removeCallbacks(lockerWatchdog); lockerWatchdog = null; }
            lockerOpen = false;
            lockerReady = false;
            configOpen = false;
            pendingLockerJson = null;
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
            if (notifyServer && wasOpen) sendChatInput("/lockeract close");
        });
    }

    private final class SampLockerBridge {
        // JS -> serveur : /lockeract <wear|civil|refresh> [femme ceinture gilet casquette]. Le serveur re-verifie faction et distance.
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.replaceAll("[^a-z]", "");
                if (!t.equals("wear") && !t.equals("civil") && !t.equals("refresh")) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                String arg = o.optString("arg", "").replaceAll("[^01 ]", "").trim();
                if (arg.length() > 12) arg = arg.substring(0, 12);
                sendHudCommand("/lockeract " + t + (arg.isEmpty() ? "" : " " + arg));
            } catch (Exception e) {
                Log.e(TAG, "locker action", e);
            }
        }

        @JavascriptInterface public void close() {
            closeLockerPage(true);
        }

        @JavascriptInterface public void ready() {
            lockerReady = true;
            Log.i(TAG, "[locker] page affichee");
        }
    }

    // ---- Connexion (page assets/login/index.html) ----
    private void handleAuthDialog(final int dialogId, final String rawJson) {
        String tmp = rawJson == null ? "{}" : rawJson;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        final String json = (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
        // Le serveur parle : l'ecran de chargement ne doit plus masquer la page de connexion
        loadingClosedByGame = true;
        hideLoadingScreen();
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) {
                Log.e(TAG, "[auth] WebView indisponible, ouverture impossible");
                return;
            }
            if (dialogId == 9522) {            // fermeture demandee par le serveur (connexion reussie)
                closeAuthPage();
            } else if (dialogId == 9520) {     // ouverture
                openAuthPage(json);
            } else if (authOpen) {             // 9521 : reponse (succes / erreur / changement d'onglet)
                hudWebView.evaluateJavascript("window.DXAuth && DXAuth.handleResponse(" + JSONObject.quote(json) + ")", null);
            }
        });
    }

    private void openAuthPage(String json) {
        if (hudWebView == null) return;
        if (authOpen && authReady) {
            // deja affichee : on reapplique seulement l'onglet / le pseudo
            hudWebView.evaluateJavascript("window.DXAuth && DXAuth.openFromNative(" + JSONObject.quote(json) + ")", null);
            return;
        }
        Log.i(TAG, "[auth] ouverture de la page de connexion");
        authOpen = true;
        authReady = false;
        configOpen = true;
        bankOpen = false;
        pharmacyOpen = false;
        pendingAuthJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/login/index.html");

        // Garde-fou : si la page ne s affiche pas en 6 s, on la ferme pour ne pas laisser un ecran vide
        // (le serveur peut la rouvrir : voir Auth_OnRequestClass).
        cancelAuthWatchdog();
        authWatchdog = () -> {
            if (authOpen && !authReady) {
                Log.e(TAG, "[auth] la page ne s'est pas affichee a temps -> fermeture de secours");
                Toast.makeText(SAMP.this, "Ecran de connexion indisponible, reessaie.", Toast.LENGTH_SHORT).show();
                closeAuthPage();
                requestAuthOpenLater(2000);
            }
        };
        authHandler.postDelayed(authWatchdog, 6000);
    }

    /** Si la page de connexion n'est pas ouverte, demande au serveur (/authact open) de l'envoyer ; 5 essais max. */
    private void requestAuthOpenLater(long delayMs) {
        authHandler.postDelayed(() -> {
            if (authOpen || authOpenAttempts >= 5 || isFinishing()) return;
            authOpenAttempts++;
            Log.i(TAG, "[auth] ecran de connexion absent -> demande au serveur (essai " + authOpenAttempts + "/5)");
            sendHudCommand("/authact open");
            requestAuthOpenLater(4000);
        }, delayMs);
    }

    private void cancelAuthWatchdog() {
        if (authWatchdog != null) {
            authHandler.removeCallbacks(authWatchdog);
            authWatchdog = null;
        }
    }

    private void closeAuthPage() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            cancelAuthWatchdog();
            authOpen = false;
            authReady = false;
            configOpen = false;
            pendingAuthJson = null;
            hudWebView.hideKeyboard();
            hudWebView.setFullscreenPage(false);
            hudWebView.setPhonePage(false);
            hudWebView.setVisibility(View.VISIBLE);
            // recharger le HUD arrete aussi la musique de la page de connexion
            hudWebView.loadUrl("file:///android_asset/hud/index.html");
        });
    }

    private final class SampAuthBridge {
        // JS -> serveur : /authact <type> <arguments...>
        // Les champs sont deja valides cote page (pseudo, e-mail, mot de passe sans espace ni / \).
        @JavascriptInterface public void action(String type, String json) {
            try {
                String t = type == null ? "" : type.toLowerCase().replaceAll("[^a-z]", "");
                if (t.isEmpty() || t.equals("openlink")) return;
                JSONObject o = new JSONObject(json == null || json.isEmpty() ? "{}" : json);
                String user = cleanAuthToken(o.optString("username", ""), 24);
                String command;
                switch (t) {
                    case "login":
                        command = "/authact login " + user + " " + cleanAuthToken(o.optString("password", ""), 100);
                        break;
                    case "register":
                        command = "/authact register " + user + " " + cleanAuthToken(o.optString("email", ""), 63)
                                + " " + cleanAuthToken(o.optString("password", ""), 100);
                        break;
                    case "requestrecoverycode":
                        command = "/authact code " + user;
                        break;
                    case "recover":
                        command = "/authact recover " + user + " " + cleanAuthToken(o.optString("code", ""), 18)
                                + " " + cleanAuthToken(o.optString("newPassword", ""), 100);
                        break;
                    default:
                        return;
                }
                // Le chat SA-MP limite une commande a 128 caracteres : au-dela le serveur la tronquerait
                if (command.length() > 127) {
                    runOnUiThread(() -> Toast.makeText(SAMP.this, "Donnees trop longues (pseudo + mot de passe).", Toast.LENGTH_SHORT).show());
                    return;
                }
                sendHudCommand(command);
            } catch (Exception e) {
                Log.e(TAG, "auth action", e);
            }
        }

        // Appele par login/app.js quand la page est bien affichee (annule le garde-fou)
        @JavascriptInterface public void ready() {
            authReady = true;
            runOnUiThread(() -> cancelAuthWatchdog());
            Log.i(TAG, "[auth] page affichee");
        }
    }

    /** Un seul argument de commande : ni espace ni caractere de controle, longueur bornee. */
    private static String cleanAuthToken(String value, int max) {
        if (value == null) return "";
        String v = value.replaceAll("[\\s\\x00-\\x1F\\x7F]", "");
        return v.length() > max ? v.substring(0, max) : v;
    }

    // ---- Banque (page assets/bank/index.html) ----
    private void handleBankDialog(final int dialogId, final String rawJson) {
        String tmp = rawJson == null ? "{}" : rawJson;
        int js = tmp.indexOf('{');
        int je = tmp.lastIndexOf('}');
        final String json = (js >= 0 && je > js) ? tmp.substring(js, je + 1) : "{}";
        runOnUiThread(() -> {
            if (hudWebView == null || isFinishing()) {
                Log.e(TAG, "[bank] WebView indisponible, ouverture impossible");
                return;
            }
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
        Log.i(TAG, "[bank] ouverture de la page banque");
        bankOpen = true;
        bankReady = false;
        configOpen = true;
        pharmacyOpen = false;
        pendingBankJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        hudWebView.setFullscreenPage(true);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/bank/index.html");

        // Garde-fou : si la page n'a pas confirme son affichage en 5 s, on la ferme et on previent le
        // serveur (/bankact close) pour que le joueur ne reste JAMAIS fige (TogglePlayerControllable).
        cancelBankWatchdog();
        bankWatchdog = () -> {
            if (bankOpen && !bankReady) {
                Log.e(TAG, "[bank] la page ne s'est pas affichee a temps -> fermeture de secours");
                Toast.makeText(SAMP.this, "Banque indisponible, reessaie.", Toast.LENGTH_SHORT).show();
                closeBankPage(true);
            }
        };
        bankHandler.postDelayed(bankWatchdog, 5000);
    }

    private void cancelBankWatchdog() {
        if (bankWatchdog != null) {
            bankHandler.removeCallbacks(bankWatchdog);
            bankWatchdog = null;
        }
    }

    private void closeBankPage(final boolean notifyServer) {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            boolean wasOpen = bankOpen;
            cancelBankWatchdog();
            bankOpen = false;
            bankReady = false;
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

        // Appele par bank.js quand la page est bien affichee (annule le garde-fou)
        @JavascriptInterface public void ready() {
            bankReady = true;
            runOnUiThread(() -> cancelBankWatchdog());
            Log.i(TAG, "[bank] page affichee");
        }
    }

    // ---- Vetements (page assets/clothes/index.html) ----
    // Appelees par le code natif (CJavaWrapper::ClothesPanelOpen / State / Close).
    public void clothesOpen(final String json) {
        runOnUiThread(() -> openClothesPage(json));
    }

    public void clothesState(final String json) {
        runOnUiThread(() -> {
            if (!clothesPageOpen || hudWebView == null) return;
            if (!clothesReady) {
                pendingClothesJson = json;
                return;
            }
            hudWebView.evaluateJavascript("window.ClothesApp && ClothesApp.onState(" + JSONObject.quote(json) + ")", null);
        });
    }

    public void clothesClose() {
        runOnUiThread(this::closeClothesPage);
    }

    private void openClothesPage(String json) {
        if (hudWebView == null || isFinishing()) return;
        Log.i(TAG, "[clothes] ouverture du panneau HTML");
        clothesPageOpen = true;
        clothesReady = false;
        configOpen = true;
        pendingClothesJson = json;
        hudWebView.setWheelOpen(false);
        hudWebView.setPhonePage(false);
        // Pas de page plein ecran : seul le panneau capte le tactile (zones envoyees par la page),
        // le reste de l'ecran laisse passer vers le jeu pour voir le personnage.
        hudWebView.setFullscreenPage(false);
        hudWebView.setInteractive(false);
        hudWebView.setTouchBounds(null);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/clothes/index.html");

        // Garde-fou : si la page ne s'affiche pas en 5 s, on la ferme et le moteur retombe sur le panneau natif.
        cancelClothesWatchdog();
        clothesWatchdog = () -> {
            if (clothesPageOpen && !clothesReady) {
                Log.e(TAG, "[clothes] la page ne s'est pas affichee a temps -> panneau natif");
                try {
                    clothesCommand(4, "", 0);
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "[clothes] natif indisponible : " + e.getMessage());
                }
                closeClothesPage();
            }
        };
        clothesHandler.postDelayed(clothesWatchdog, 5000);
    }

    private void cancelClothesWatchdog() {
        if (clothesWatchdog != null) {
            clothesHandler.removeCallbacks(clothesWatchdog);
            clothesWatchdog = null;
        }
    }

    private void closeClothesPage() {
        if (!clothesPageOpen) return;
        cancelClothesWatchdog();
        clothesPageOpen = false;
        clothesReady = false;
        pendingClothesJson = null;
        configOpen = false;
        if (hudWebView == null) return;
        hudWebView.setTouchBounds(null);
        hudWebView.setFullscreenPage(false);
        hudWebView.setPhonePage(false);
        hudWebView.setVisibility(View.VISIBLE);
        hudWebView.loadUrl("file:///android_asset/hud/index.html");
    }

    private void sendClothesCommand(int type, String key, int value) {
        try {
            clothesCommand(type, key == null ? "" : key, value);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "[clothes] natif indisponible : " + e.getMessage());
        }
    }

    private final class SampClothesBridge {
        // JS -> moteur : change une option ("topStyle", "topColor", ...)
        @JavascriptInterface public void setOption(String key, int value) {
            if (key == null || !key.matches("[A-Za-z]{1,24}")) return;
            sendClothesCommand(0, key, value);
        }

        // JS -> moteur : 0 homme / 1 femme
        @JavascriptInterface public void setGender(int gender) {
            sendClothesCommand(1, "", gender == 1 ? 1 : 0);
        }

        @JavascriptInterface public void save() {
            sendClothesCommand(2, "", 0);
            runOnUiThread(() -> closeClothesPage());
        }

        @JavascriptInterface public void cancel() {
            sendClothesCommand(3, "", 0);
            runOnUiThread(() -> closeClothesPage());
        }

        // Zones tactiles du panneau ("viewport=W,H;panel=l,t,r,b") : le reste de l'ecran reste jouable.
        @JavascriptInterface public void setTouchBounds(String raw) {
            if (hudWebView != null && clothesPageOpen) hudWebView.setTouchBounds(raw);
        }

        // Appele par la page quand elle est prete : annule le garde-fou et envoie l'etat en attente.
        @JavascriptInterface public void ready() {
            clothesReady = true;
            runOnUiThread(() -> {
                cancelClothesWatchdog();
                String json = pendingClothesJson;
                pendingClothesJson = null;
                if (json != null && hudWebView != null && clothesPageOpen) {
                    hudWebView.evaluateJavascript("window.ClothesApp && ClothesApp.onState(" + JSONObject.quote(json) + ")", null);
                }
            });
            Log.i(TAG, "[clothes] page affichee");
        }
    }

    private void closeConfig() {
        runOnUiThread(() -> {
            if (hudWebView == null) return;
            if (authOpen) return;
            clothesPageOpen = false;
            invOpen = false;
            adminOpen = false;
            lockerOpen = false;
            policeOpen = false;
            shopOpen = false;
            jobOpen = false;
            uiOpen = false;
            reportOpen = false;
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
        // Dialogue SA-MP ouvert (page assets/dialog) : retour = bouton droit
        if (mDialog != null && mDialog.onBackPressed()) {
            return;
        }
        if (authOpen) {
            // la connexion est obligatoire : le bouton retour ne ferme pas la page
            return;
        }
        if (adminOpen) {
            closeAdminPage(true);
            return;
        }
        if (reportOpen) {
            closeReportPage(true);
            return;
        }
        if (policeOpen) {
            closePolicePage(true);
            return;
        }
        if (lockerOpen) {
            closeLockerPage(true);
            return;
        }
        if (shopOpen) {
            closeShopPage(true);
            return;
        }
        if (jobOpen) {
            closeJobPage(true);
            return;
        }
        if (uiOpen) {
            closeUiPage(true);
            return;
        }
        if (invOpen) {
            closeInvPage(true);
            return;
        }
        if (bankOpen) {
            closeBankPage(true);
            return;
        }
        if (clothesPageOpen) {
            sendClothesCommand(3, "", 0); // retour = ANNULER
            closeClothesPage();
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
        lastPosX = posX;
        lastPosY = posY;
        hasLastPos = true;
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
                hudWebView.evaluateJavascript("(function(j){window.HudBridge && HudBridge.setPlayerState(j);"
                        + "if (window.MapApp && j.posX !== undefined) MapApp.setPlayer(j.posX, j.posY);})(" + json + ")", null);
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
                // payload {"x":..,"y":..} = point choisi sur la carte (efface a l'arrivee), {"clear":1} = effacer
                try {
                    JSONObject o = new JSONObject(payload == null || payload.isEmpty() ? "{}" : payload);
                    if (o.has("x") && o.has("y")) {
                        JSONObject g = new JSONObject();
                        g.put("x", o.getDouble("x"));
                        g.put("y", o.getDouble("y"));
                        g.put("auto", 1);
                        lastGpsJson = g.toString();
                    } else {
                        lastGpsJson = null;
                        sendHudCommand(CMD_MAP);   // /gps : le serveur remet le GPS de la mission en cours s'il y en a une
                    }
                } catch (Exception e) {
                    Log.e(TAG, "map:gps", e);
                }
                closeConfig();
                return;
            }
            if ("gps:arrived".equals(action)) {
                lastGpsJson = null;
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
    private static final String CMD_INTERACT = "/interagir";
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
            if (!authBootstrapDone) {
                authBootstrapDone = true;
                runOnUiThread(() -> requestAuthOpenLater(2500));
            }
            final String objNow = lastObjJson;
            if (objNow != null) hudJs("window.ObjTracker && ObjTracker.set(" + JSONObject.quote(objNow) + ")");
            final String pendingInteract = lastInteractJson;
            if (pendingInteract != null) {
                hudJs("window.HudBridge && HudBridge.setInteract && HudBridge.setInteract(" + JSONObject.quote(pendingInteract) + ")");
            }
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
                // casque du menu rotatif : support / report (la page s'ouvre tout de suite, le serveur la remplit)
                case "report": runOnUiThread(() -> { openReportPage("{\"loading\":1}"); sendHudCommand("/repact open"); }); break;
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
        /** Tap sur l'icone de main : le serveur choisit lui-meme la cible la plus proche (banque, vehicule...). */
        @JavascriptInterface public void interactAction() { sendHudCommand(CMD_INTERACT); }
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
