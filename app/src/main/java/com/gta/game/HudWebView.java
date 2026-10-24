package com.gta.game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

/**
 * WebView posée au-dessus du jeu. Elle ne consomme que les touches destinées au HUD HTML,
 * toutes les autres touches traversent vers le jeu.
 *
 * - HUD (hud/index.html) : les zones tactiles sont envoyées par le JS via SampHud.setTouchBounds().
 * - Pages plein écran (config, blazer, pharmacie, caméra) : tout est consommé.
 * - Téléphone Crestwood : seule la moitié droite de l'écran est consommée.
 */
public class HudWebView extends WebView {
    private boolean wheelOpen;
    private boolean fullscreenPage;
    private boolean vehicleMode;
    private boolean phonePage;
    private boolean cameraPage;
    private volatile boolean interactive;
    private volatile List<float[]> bounds = new ArrayList<>();

    public HudWebView(Context context) { super(context); configure(); }
    public HudWebView(Context context, AttributeSet attrs) { super(context, attrs); configure(); }
    public HudWebView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); configure(); }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure() {
        setClickable(true);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    public void setWheelOpen(boolean value) { wheelOpen = value; }
    public void setFullscreenPage(boolean value) { fullscreenPage = value; cameraPage = false; }
    public void setVehicleMode(boolean value) { vehicleMode = value; }
    public void setPhonePage(boolean value) { phonePage = value; cameraPage = false; }

    /** Caméra du téléphone : seules les zones du cadre téléphone (haut/bas) captent, le viseur laisse passer le tactile vers le jeu. */
    public void setCameraPage(boolean value) { cameraPage = value; }

    /** Vrai quand le chat, le menu rapide, l'éditeur de HUD, la carte d'identité... sont ouverts. */
    public void setInteractive(boolean value) { interactive = value; }

    /**
     * Format envoyé par le JS : "viewport=W,H;nom=gauche,haut,droite,bas;..." en pixels CSS.
     * On convertit en coordonnées normalisées (0..1) pour être indépendant de la densité.
     */
    public void setTouchBounds(String raw) {
        List<float[]> next = new ArrayList<>();
        if (raw != null) {
            float vw = 0f;
            float vh = 0f;
            List<float[]> rects = new ArrayList<>();
            for (String entry : raw.split(";")) {
                int eq = entry.indexOf('=');
                if (eq <= 0) continue;
                String name = entry.substring(0, eq).trim();
                String[] parts = entry.substring(eq + 1).split(",");
                try {
                    if ("viewport".equals(name) && parts.length >= 2) {
                        vw = Float.parseFloat(parts[0].trim());
                        vh = Float.parseFloat(parts[1].trim());
                    } else if (parts.length >= 4) {
                        rects.add(new float[] {
                                Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()),
                                Float.parseFloat(parts[2].trim()), Float.parseFloat(parts[3].trim())
                        });
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (vw > 0f && vh > 0f) {
                for (float[] r : rects) {
                    next.add(new float[] { r[0] / vw, r[1] / vh, r[2] / vw, r[3] / vh });
                }
            }
        }
        bounds = next;
    }

    /** Rend la WebView focalisable pour que le clavier Android puisse se brancher sur un <input>. */
    public void showKeyboard() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        clearFocus();
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (fullscreenPage) return super.onTouchEvent(event);
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());
        float x = event.getX() / width;
        float y = event.getY() / height;
        if (cameraPage) {
            for (float[] r : bounds) {
                if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) return super.onTouchEvent(event);
            }
            return false;
        }
        if (phonePage) {
            if (x >= 0.50f && y >= 0.05f) return super.onTouchEvent(event);
            return false;
        }
        if (interactive) return super.onTouchEvent(event);
        List<float[]> current = bounds;
        for (float[] r : current) {
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) return super.onTouchEvent(event);
        }
        return false;
    }
}
