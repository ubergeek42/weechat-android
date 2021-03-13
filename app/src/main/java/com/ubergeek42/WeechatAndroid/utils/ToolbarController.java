package com.ubergeek42.WeechatAndroid.utils;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.views.SystemAreaHeightExaminer;
import com.ubergeek42.WeechatAndroid.views.SystemAreaHeightObserver;


public class ToolbarController implements SystemAreaHeightObserver {
    private final WeechatActivity activity;
    private final View toolbarContainer;
    private final View mainView;
    private final int toolbarHeight;

    private final SystemAreaHeightExaminer systemAreaHeightExaminer;

    private boolean shown = true;
    public boolean keyboardVisible = false;

    public ToolbarController(WeechatActivity activity) {
        this.activity = activity;
        this.toolbarContainer = activity.findViewById(R.id.toolbar_container);
        this.mainView = activity.findViewById(R.id.main_viewpager);
        toolbarHeight = toolbarContainer.getLayoutParams().height;

        systemAreaHeightExaminer = SystemAreaHeightExaminer.obtain();
        systemAreaHeightExaminer.setObserver(this);
        systemAreaHeightExaminer.onActivityCreated(activity);
    }

    public void detach() {
        systemAreaHeightExaminer.onActivityDestroyed(activity);
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
        if (canNotAutoHide() || !activity.isChatInputOrSearchInputFocused()) return;
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
        toolbarContainer.animate().translationY(0).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void hide() {
        if (!shown) return;
        shown = false;
        toolbarContainer.animate().translationY(-toolbarContainer.getBottom()).setInterpolator(new AccelerateInterpolator()).start();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private int initialSystemAreaHeight = -1;

    @Override public void onSystemAreaHeightChanged(int systemAreaHeight) {
        if (canNotAutoHide()) return;

        // note the initial system area (assuming keyboard closed) and return. we should be getting
        // a few more calls to this method without any changes to the height numbers
        if (initialSystemAreaHeight == -1) {
            initialSystemAreaHeight = systemAreaHeight;
            return;
        }

        // weed out some insanity that's happening when the window is in split screen mode. it seems
        // that while resizing some elements can temporarily have the height 0.
        if (systemAreaHeight < initialSystemAreaHeight) return;

        boolean keyboardVisible = systemAreaHeight - initialSystemAreaHeight > 20;
        onSoftwareKeyboardStateChanged(keyboardVisible);
    }
}

//    // windowHeight is the height of the activity that includes the height of the status bar and the
//    // navigation bar. if the activity is split, this height seems to be only including the system
//    // bar that the activity is “touching”. this height doesn't include the keyboard height per se,
//    // but if the activity changes size due to the keyboard, this number remains the same.
//    // activityHeight is the height of the activity not including any of the system stuff.
//    @Override public void onGlobalLayout() {
//        if (canNotAutoHide()) return;
//
//        // on android 7, if changing the day/night theme in settings, the activity can be recreated
//        // right away but with a wrong window height. so we wait until it's actually resumed
//        if (activity.getLifecycle().getCurrentState() != Lifecycle.State.RESUMED) return;
//
//        int windowHeight = root.getRootView().getHeight();
//        int activityHeight = root.getHeight();
//        int systemAreaHeight = windowHeight - activityHeight;
//
//        System.out.println("windowHeight " + windowHeight + " activityHeight " + activityHeight + " systemAreaHeight " + systemAreaHeight);
//
//
//        if (windowHeight <= 0) return;
//        if (systemAreaHeight <= 0) return;
//
//        // note the initial system area (assuming keyboard closed) and return. we should be getting
//        // a few more calls to this method without any changes to the height numbers
//        if (initialSystemAreaHeight == -1) {
//            initialSystemAreaHeight = systemAreaHeight;
//            return;
//        }
//
//        // weed out some insanity that's happening when the window is in split screen mode. it seems
//        // that while resizing some elements can temporarily have the height 0.
//        if (systemAreaHeight < initialSystemAreaHeight) return;
//        if (activityHeight == 0) return;
//
//        boolean keyboardVisible = systemAreaHeight - initialSystemAreaHeight > 20;
//        onSoftwareKeyboardStateChanged(keyboardVisible);
//    }