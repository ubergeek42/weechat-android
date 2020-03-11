package com.ubergeek42.WeechatAndroid.utils;

import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.P;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

public class ToolbarController implements ViewTreeObserver.OnGlobalLayoutListener {
    private final WeechatActivity activity;
    private final Toolbar toolbar;
    private final View root;
    private final View mainView;
    private final int toolbarHeight;

    private boolean shown = true;
    private boolean keyboardVisible = false;

    public ToolbarController(WeechatActivity activity) {
        this.activity = activity;
        this.toolbar = activity.findViewById(R.id.toolbar);
        this.mainView = activity.findViewById(R.id.main_viewpager);
        this.root = activity.findViewById(android.R.id.content);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this);
        toolbarHeight = toolbar.getLayoutParams().height;
    }

    public void detach() {
        root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    private int _dy = 0;
    public void onScroll(int dy, boolean touchingTop, boolean touchingBottom) {
        if (canNotAutoHide()) return;
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
        if (canNotAutoHide() || !activity.isChatInputFocused()) return;
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

    private boolean canNotAutoHide() {
        // Offset the content if the action bar is always shown, so the top text and button
        // remain visible
        setContentIsOffset(!P.autoHideActionbar);
        if (P.autoHideActionbar) return false;
        show();
        return true;
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

    private int initialSystemAreaHeight = -1;

    // windowHeight is the height of the activity that includes the height of the status bar and the
    // navigation bar. if the activity is split, this height seems to be only including the system
    // bar that the activity is “touching”. this height doesn't include the keyboard height per se,
    // but if the activity changes size due to the keyboard, this number remains the same.
    // activityHeight is the height of the activity not including any of the system stuff.
    @Override public void onGlobalLayout() {
        if (canNotAutoHide()) return;
        int windowHeight = root.getRootView().getHeight();
        int activityHeight = root.getHeight();
        int systemAreaHeight = windowHeight - activityHeight;

        // note the initial system area (assuming keyboard closed) and return. we should be getting
        // a few more calls to this method without any changes to the height numbers
        if (initialSystemAreaHeight == -1) {
            initialSystemAreaHeight = systemAreaHeight;
            return;
        }

        assertThat(windowHeight).isGreaterThan(0);
        assertThat(initialSystemAreaHeight).isGreaterThan(0);

        // weed out some insanity that's happening when the window is in split screen mode. it seems
        // that while resizing some elements can temporarily have the height 0.
        if (systemAreaHeight < initialSystemAreaHeight) return;
        if (activityHeight == 0) return;

        boolean keyboardVisible = systemAreaHeight - initialSystemAreaHeight > 20;
        onSoftwareKeyboardStateChanged(keyboardVisible);
    }
}