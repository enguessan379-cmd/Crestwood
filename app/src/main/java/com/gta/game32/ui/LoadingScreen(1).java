package com.gta.game32.ui;

import android.app.Activity;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.gta.game.R;

public class LoadingScreen {

    private Activity activity;
    private ConstraintLayout mainLayout;

    public LoadingScreen(Activity activity)
    {
        this.activity = activity;

        mainLayout = (ConstraintLayout) activity.getLayoutInflater().inflate(R.layout.loadingscreen, null);
        activity.addContentView(mainLayout, new ConstraintLayout.LayoutParams(-1, -1));
    }

    public void hide()
    {
        mainLayout.setVisibility(View.INVISIBLE);
    }
}
