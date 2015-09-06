package com.ubergeek42.WeechatAndroid.utils;

import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class ToolbarController  {
    Toolbar toolbar;
    ActionBar actionBar;
    boolean shown = true;
    boolean keyboardVisible = false;

    public ToolbarController(Toolbar toolbar, ActionBar actionBar) {
        this.toolbar = toolbar;
        this.actionBar = actionBar;
    }

    public void onUserScroll(int bottomHidden, int prevBottomHidden) {
        if (bottomHidden > prevBottomHidden) hide();
        else if (bottomHidden < prevBottomHidden) show();
    }

    public void onPageChangedOrSelected() {
        show();
    }

    public void onSoftwareKeyboardStateChanged(boolean visible) {
        if (keyboardVisible == visible) return;
        keyboardVisible = visible;
        if (visible) hide();
        else show();
    }


    private void show() {
        if (shown || keyboardVisible) return;
        shown = true;
        if (android.os.Build.VERSION.SDK_INT >= 14)
            toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
        else
            actionBar.show();
    }

    private void hide() {
        if (!shown) return;
        shown = false;
        if (android.os.Build.VERSION.SDK_INT >= 14)
            toolbar.animate().translationY(-toolbar.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
        else
            actionBar.hide();
    }
}