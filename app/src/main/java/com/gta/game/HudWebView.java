package com.gta.game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/** WebView sobre o jogo que só consome toques destinados ao HUD HTML. */
public class HudWebView extends WebView {
    private boolean wheelOpen;
    private boolean fullscreenPage;
    private boolean vehicleMode;
    private boolean phonePage;

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
    public void setFullscreenPage(boolean value) { fullscreenPage = value; }
    public void setVehicleMode(boolean value) { vehicleMode = value; }
    public void setPhonePage(boolean value) { phonePage = value; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (fullscreenPage) return super.onTouchEvent(event);
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());
        float x = event.getX() / width;
        float y = event.getY() / height;
        boolean leftButtons = x <= 0.20f && y >= 0.20f && y <= 0.64f;
        boolean interactionWheel = wheelOpen && x >= 0.20f && x <= 0.82f && y >= 0.08f && y <= 0.92f;
        boolean vehicleControls = vehicleMode && x >= 0.68f && y >= 0.52f;
        boolean phoneControls = phonePage && x >= 0.50f && y >= 0.05f;
        if (leftButtons || interactionWheel || vehicleControls || phoneControls) return super.onTouchEvent(event);
        return false;
    }
}
