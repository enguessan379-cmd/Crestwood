package com.gta.launcher.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.gta.game.R;
import com.gta.game.SAMP;
import com.rockstargames.oswrapper.DataInstaller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String SERVER_HOST = "51.38.205.167";
    private static final int SERVER_PORT = 15118;
    private static final String LAUNCHER_PREFERENCES = "crestwood_launcher";
    private static final String SAVED_NICKNAME_KEY = "saved_nickname";
    private static final String LOADING_URL = "file:///android_asset/loading/index.html";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Button startButton;
    private EditText nickInput;
    private TextView authorText;
    private TextView connectionStatus;
    private FrameLayout updateOverlay;
    private WebView updateWebView;
    private volatile boolean dataReady;
    private volatile int lastUpdatePercent = -1;
    private volatile long lastProgressBytes = -1L;
    private volatile long lastProgressAtMs;
    private volatile long lastRenderedProgressAtMs;
    private ActivityResultLauncher<Intent> allFilesAccessLauncher;
    private ActivityResultLauncher<String> legacyStorageAccessLauncher;
    private boolean storageAccessRequestOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setFullScreenMode();
            setContentView(R.layout.main_activity);
            initViews();
            initializeStorageAccessLaunchers();
            setupClickListeners();
            initializeUpdateScreen();
            startDataInstallation();
        } catch (Exception error) {
            Log.e(TAG, "Falha criando o Launcher", error);
            finish();
        }
    }

    private void initViews() {
        startButton = findViewById(R.id.startButton);
        nickInput = findViewById(R.id.nickInput);
        authorText = findViewById(R.id.authorText);
        connectionStatus = findViewById(R.id.connectionStatus);
        restoreAndPersistNickname();
        setStartEnabled(false);
    }

    private void restoreAndPersistNickname() {
        if (nickInput == null) return;
        SharedPreferences preferences = getSharedPreferences(LAUNCHER_PREFERENCES, MODE_PRIVATE);
        String saved = preferences.getString(SAVED_NICKNAME_KEY, "");
        if (!TextUtils.isEmpty(saved)) {
            nickInput.setText(saved);
            nickInput.setSelection(saved.length());
        }
        nickInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                getSharedPreferences(LAUNCHER_PREFERENCES, MODE_PRIVATE).edit()
                        .putString(SAVED_NICKNAME_KEY, value == null ? "" : value.toString().trim())
                        .apply();
            }
        });
    }

    private void initializeUpdateScreen() {
        updateOverlay = new FrameLayout(this);
        updateOverlay.setBackgroundColor(Color.rgb(7, 17, 31));
        updateWebView = new WebView(this);
        updateWebView.setBackgroundColor(Color.TRANSPARENT);
        updateWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        updateWebView.setVerticalScrollBarEnabled(false);
        updateWebView.setHorizontalScrollBarEnabled(false);
        WebSettings settings = updateWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        updateWebView.addJavascriptInterface(new LauncherUpdateBridge(), "AndroidLoading");
        updateWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { renderUpdateState(); }
        });
        updateOverlay.addView(updateWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addContentView(updateOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateWebView.loadUrl(LOADING_URL);

        DataInstaller.setListener(new DataInstaller.Listener() {
            @Override public void onProgress(int percent, String phase, long done, long total) {
                long now = SystemClock.elapsedRealtime();
                boolean isFinal = percent >= 100 || (total > 0L && done >= total);
                boolean changedPercent = percent != lastUpdatePercent;
                boolean renderIntervalReached = now - lastRenderedProgressAtMs >= 350L;
                if (!isFinal && !changedPercent && !renderIntervalReached) return;
                long speed = 0L;
                if (phase != null && phase.startsWith("Téléchargement des") && lastProgressBytes >= 0L && done > lastProgressBytes && now > lastProgressAtMs) {
                    speed = (done - lastProgressBytes) * 1000L / (now - lastProgressAtMs);
                }
                lastUpdatePercent = percent;
                lastProgressBytes = done;
                lastProgressAtMs = now;
                lastRenderedProgressAtMs = now;
                final long reportedSpeed = speed;
                runOnUiThread(() -> evaluateUpdate("window.LoadingBridge&&LoadingBridge.setProgress(" + percent + "," + quoteJs(phase) + "," + done + "," + total + "," + reportedSpeed + ")"));
            }
            @Override public void onComplete(File root) {
                dataReady = DataInstaller.isInstallReady(MainActivity.this);
                runOnUiThread(() -> {
                    if (!dataReady) {
                        showRecoverableError("Installation incomplète. Appuyez sur JOUER pour réparer.");
                        return;
                    }
                    if (connectionStatus != null) connectionStatus.setText("Données à jour et validées");
                    showReadyThenHome();
                });
            }
            @Override public void onError(String message, Throwable error) {
                dataReady = false;
                runOnUiThread(() -> showRecoverableError(message));
            }
        });
    }

    private void initializeStorageAccessLaunchers() {
        allFilesAccessLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            storageAccessRequestOpen = false;
            startDataInstallation();
        });
        legacyStorageAccessLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            storageAccessRequestOpen = false;
            startDataInstallation();
        });
    }

    private void startDataInstallation() {
        if (DataInstaller.hasSharedStorageAccess(this)) {
            storageAccessRequestOpen = false;
            evaluateUpdate("window.LoadingBridge&&LoadingBridge.storageAccessGranted()");
            DataInstaller.prepare(this);
            return;
        }
        setStartEnabled(false);
        evaluateUpdate("window.LoadingBridge&&LoadingBridge.storageAccessRequired()");
        if (storageAccessRequestOpen) return;
        storageAccessRequestOpen = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                allFilesAccessLauncher.launch(intent);
            } catch (Exception error) {
                storageAccessRequestOpen = false;
                Log.e(TAG, "Falha abrindo a autorização de armazenamento", error);
                evaluateUpdate("window.LoadingBridge&&LoadingBridge.error(" + quoteJs("Impossible d'ouvrir l'autorisation de stockage.") + ")");
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            legacyStorageAccessLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void renderUpdateState() {
        if (!DataInstaller.hasSharedStorageAccess(this)) {
            evaluateUpdate("window.LoadingBridge&&LoadingBridge.storageAccessRequired()");
        } else if (DataInstaller.isReady() && DataInstaller.isInstallReady(this)) {
            dataReady = true;
            showReadyThenHome();
        } else if (!TextUtils.isEmpty(DataInstaller.getErrorMessage())) {
            showRecoverableError(DataInstaller.getErrorMessage());
        }
    }

    private void showReadyThenHome() {
        if (!dataReady || updateWebView == null) return;
        lastUpdatePercent = 100;
        updateWebView.evaluateJavascript("window.LoadingBridge&&LoadingBridge.ready()", value ->
                handler.postDelayed(this::hideUpdateScreen, 2500L));
    }

    private void showRecoverableError(String message) {
        dataReady = false;
        setStartEnabled(false);
        if (connectionStatus != null) connectionStatus.setText("Installation interrompue. Réparez les données pour continuer.");
        if (updateOverlay != null) updateOverlay.setVisibility(View.VISIBLE);
        evaluateUpdate("window.LoadingBridge&&LoadingBridge.error(" + quoteJs(message) + ")");
    }

    private void hideUpdateScreen() {
        if (!dataReady || !DataInstaller.isInstallReady(this)) return;
        if (updateOverlay != null) updateOverlay.setVisibility(View.GONE);
        setStartEnabled(true);
    }

    private void beginDataRepair(String message) {
        if (!DataInstaller.hasSharedStorageAccess(this)) {
            startDataInstallation();
            return;
        }
        if (DataInstaller.isInstalling()) {
            Toast.makeText(this, "L'installation des données est toujours en cours.", Toast.LENGTH_SHORT).show();
            return;
        }
        dataReady = false;
        lastUpdatePercent = -1;
        lastProgressBytes = -1L;
        lastProgressAtMs = 0L;
        lastRenderedProgressAtMs = 0L;
        handler.removeCallbacksAndMessages(null);
        setStartEnabled(false);
        if (connectionStatus != null) connectionStatus.setText("Réparation des données du jeu...");
        if (updateOverlay != null) updateOverlay.setVisibility(View.VISIBLE);
        DataInstaller.repair(this);
    }

    private void evaluateUpdate(String script) {
        if (updateWebView != null && updateOverlay != null && updateOverlay.getVisibility() == View.VISIBLE) {
            updateWebView.evaluateJavascript(script, null);
        }
    }

    private void setStartEnabled(boolean enabled) {
        if (startButton == null) return;
        startButton.setEnabled(enabled);
        startButton.setAlpha(enabled ? 1f : 0.55f);
        if (enabled) startButton.setText("JOUER");
    }

    private void startGame() {
        if (!dataReady || !DataInstaller.isInstallReady(this)) {
            beginDataRepair("Les données du jeu sont incomplètes. Réparation de l'installation...");
            return;
        }
        try {
            String nickname = nickInput == null ? "Nick_Name" : nickInput.getText().toString().trim();
            if (TextUtils.isEmpty(nickname)) nickname = "Nick_Name";
            if (nickname.length() > 24) nickname = nickname.substring(0, 24);
            getSharedPreferences(LAUNCHER_PREFERENCES, MODE_PRIVATE).edit()
                    .putString(SAVED_NICKNAME_KEY, nickname).apply();
            writeServerSettings(nickname);
            startButton.setEnabled(false);
            startButton.setText("...");
            Intent gameIntent = new Intent(this, SAMP.class);
            gameIntent.putExtra("nickname", nickname);
            gameIntent.putExtra("server_host", SERVER_HOST);
            gameIntent.putExtra("server_port", SERVER_PORT);
            startActivity(gameIntent);
        } catch (Exception error) {
            Log.e(TAG, "Falha iniciando jogo", error);
            setStartEnabled(true);
            Toast.makeText(this, "Não foi possível iniciar o jogo", Toast.LENGTH_LONG).show();
        }
    }

    private void writeServerSettings(String nickname) throws IOException {
        File base = DataInstaller.getTargetDirectory(this);
        if (base == null) throw new IOException("Diretório da data indisponível");
        File directory = new File(base, "SAMP");
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IOException("Impossible de créer la configuration de SAMP");
        }
        try (FileWriter writer = new FileWriter(new File(directory, "settings.ini"), false)) {
            writer.write("[client]\n");
            writer.write("name=" + nickname.replace("\n", "").replace("\r", "") + "\n");
            writer.write("host=" + SERVER_HOST + "\n");
            writer.write("port=" + SERVER_PORT + "\n");
            writer.write("password=\nversion=0.3.7\n\n[debug]\ndebug=false\nonline=true\n\n[gui]\nVoiceChatEnable=false\n");
        }
    }

    private void setupClickListeners() {
        if (startButton != null) startButton.setOnClickListener(view -> {
            view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(
                    () -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
            startGame();
        });
        if (authorText != null) authorText.setOnClickListener(view -> openExternal("https://t.me/kuzia15"));
        View planet = findViewById(R.id.planetButton);
        View youtube = findViewById(R.id.youtubeButton);
        View discord = findViewById(R.id.discordButton);
        if (planet != null) planet.setOnClickListener(view -> openExternal("https://www.google.com"));
        if (youtube != null) youtube.setOnClickListener(view -> openExternal("https://www.youtube.com"));
        if (discord != null) discord.setOnClickListener(view -> openExternal("https://discord.com"));
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception error) { Log.e(TAG, "Falha abrindo link", error); }
    }

    private static String quoteJs(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }

    private void setFullScreenMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }
        if (getSupportActionBar() != null) getSupportActionBar().hide();
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = activity.getCurrentFocus();
        if (inputManager != null && focus != null) inputManager.hideSoftInputFromWindow(focus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }

    private final class LauncherUpdateBridge {
        @JavascriptInterface public void start() { DataInstaller.prepare(MainActivity.this); }
        @JavascriptInterface public void retry() { runOnUiThread(() -> beginDataRepair("Tentative de réparation des données du jeu...")); }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
