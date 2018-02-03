package com.ubergeek42.WeechatAndroid.utils;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.service.P;

public class ToolbarController implements ViewTreeObserver.OnGlobalLayoutListener {
    private final Toolbar toolbar;
    private final View root;
    private final View mainView;
    private final int toolbarHeight;

    private boolean shown = true;
    private boolean keyboardVisible = false;

    public ToolbarController(AppCompatActivity activity) {
        this.toolbar = activity.findViewById(R.id.toolbar);
        this.mainView = activity.findViewById(R.id.main_viewpager);
        this.root = activity.findViewById(android.R.id.content);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        toolbarHeight = toolbar.getLayoutParams().height;
    }

    private int _dy = 0;
    public void onScroll(int dy, boolean touchingTop, boolean touchingBottom) {
        if (!canAutoHide()) return;
        if (keyboardVisible) return;
        _dy = ((_dy < 0) != (dy < 0)) ? dy : _dy + dy;
        if (_dy < -P._200dp || (_dy < 0 && touchingTop)) hide();
        else if (_dy > P._200dp || (_dy > 0 && touchingBottom)) show();
    }

    public void onPageChangedOrSelected() {
        _dy = 0;
        show();
    }

    private void onSoftwareKeyboardStateChanged(boolean visible) {
        if (!canAutoHide()) return;
        if (keyboardVisible == visible) return;
        keyboardVisible = visible;
        if (visible) hide();
        else show();
    }

    private void setContentIsOffset(boolean offset) {
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mainView.getLayoutParams();
        final int value = offset ? toolbarHeight : 0;
        if (params.topMargin != value) {
            params.setMargins(0, value, 0, 0);
            mainView.setLayoutParams(params);
        }
    }

    private boolean canAutoHide() {
        // Offset the content if the action bar is always shown, so the top text and button
        // remain visible
        setContentIsOffset(!P.autoHideActionbar);
        if (P.autoHideActionbar)
            return true;
        show();
        return false;
    }

    private void show() {
        if (shown) return;
        shown = true;
        toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void hide() {
        if (!shown) return;
        shown = false;
        toolbar.animate().translationY(-toolbar.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onGlobalLayout() {
        if (!canAutoHide()) return;
        // if more than 300 pixels, its probably a keyboard...
        int heightDiff = root.getRootView().getHeight() - root.getHeight();
        if (heightDiff > 300)
            onSoftwareKeyboardStateChanged(true);
        else if (heightDiff < 300)
            onSoftwareKeyboardStateChanged(false);
    }
}