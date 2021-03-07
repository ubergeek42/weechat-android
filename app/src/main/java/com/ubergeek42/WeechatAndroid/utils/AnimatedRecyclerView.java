// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import com.ubergeek42.WeechatAndroid.views.OnJumpedUpWhileScrollingListener;
import com.ubergeek42.WeechatAndroid.views.ViewUtilsKt;

import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;

public class AnimatedRecyclerView extends RecyclerView implements OnJumpedUpWhileScrollingListener {

    private final static int DELAY = 10;
    private final static int DURATION = 300;
    private final static int SCROLL_DELAY = 100;

    // used to temporarily disable animator to avoid premature fade-in of views
    // note: setItemAnimator(null) can change the number of children
    private ItemAnimator animator;

    // number of lines already in adapter — these don't need to be animated
    // value of -1 indicates that no animation is requested
    private int linesAlreadyDisplayed = -1;

    // line to scroll to after animations complete
    // -1 indicates that no scroll has been suggested
    private int scrollToPosition = -1;

    public AnimatedRecyclerView(Context context) {
        this(context, null);
    }

    public AnimatedRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimatedRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        animator = getItemAnimator();
        manager.setStackFromEnd(true);
        setLayoutManager(manager);
        setHasFixedSize(true);
    }

    final private LinearLayoutManager manager = new LinearLayoutManagerFix(getContext(), LinearLayoutManager.VERTICAL, false) {
        // there probably should be a simpler way of doing this, but so far this method
        // is the only one i've found that is called reliably on laying out children
        @Override public void onLayoutCompleted(State state) {
            super.onLayoutCompleted(state);
            if (getChildCount() > 1) animateIfNeeded();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is called only when the number of items in adapter is > 1
    // this animates all children of the RecyclerView except the last `linesAlreadyDisplayed`
    @UiThread private void animateIfNeeded() {
        if (getAdapter() == null) return;
        if (linesAlreadyDisplayed < 0)
            return;

        if (getScrollState() != SCROLL_STATE_IDLE) {
            onAnimationsCancelled.run();
            return;
        }

        boolean found = false;
        int step = 0;

        int lastAnimatablePosition = getAdapter().getItemCount() - linesAlreadyDisplayed - 1;
        ViewHolder lastAnimatableViewHolder = (lastAnimatablePosition > 0) ? findViewHolderForAdapterPosition(lastAnimatablePosition) : null;
        linesAlreadyDisplayed = -1;

        if (lastAnimatableViewHolder != null) {
            setItemAnimator(null);

            View lastAnimatableView = lastAnimatableViewHolder.itemView;
            for (int i = getChildCount(); i >= 0; i--) {
                View v = getChildAt(i);
                if (!found && v == lastAnimatableView) found = true;
                if (found) animateView(v, step++);
            }
        }
        if (found) postDelayed(onAnimationsCancelled, (step - 1) * DELAY + DURATION);
        else onAnimationsCancelled.run();
    }

    final private Runnable onAnimationsCancelled = new Runnable() {
        @Override @UiThread public void run() {
            if (getItemAnimator() == null) setItemAnimator(animator);
            int position = scrollToPosition;
            if (position != -1) {
                postDelayed(() -> {
                    smoothScrollToPositionFix(position);
                    scrollToPosition = -1;
                }, SCROLL_DELAY);
            }
        }
    };

    @UiThread public void requestAnimation() {
        if (getAdapter() == null) return;
        linesAlreadyDisplayed = getAdapter().getItemCount() - 1;
    }

    // this is needed because awakenScrollbars is protected
    @UiThread public void flashScrollbar() {
        awakenScrollBars();
    }

    // use an external ObjectAnimator instead of view.animate() because
    // the latter leaves some residue in the ViewPropertyAnimator attached to the view
    @UiThread static private void animateView(View view, final int position) {
        Animation alpha = new AlphaAnimation(0f, 1f);
        alpha.setDuration(DURATION);
        alpha.setStartOffset(position * DELAY);
        view.startAnimation(alpha);
    }

    @Override public void setItemAnimator(@Nullable ItemAnimator animator) {
        assertThat(Looper.myLooper()).isEqualTo(Looper.getMainLooper());
        super.setItemAnimator(animator);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// smooth scroll to hot item
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @UiThread public void smoothScrollToPositionAfterAnimation(final int position) {
        if (linesAlreadyDisplayed < 0) smoothScrollToPositionFix(position);
        else scrollToPosition = position;
    }

    @UiThread private void smoothScrollToPositionFix(int position) {
        if (getLayoutManager() == null) return;
        ViewUtilsKt.jumpThenSmoothScrollCentering(this, position);
    }


    public void setOnJumpedUpWhileScrollingListener(OnJumpedUpWhileScrollingListener listener) {
        onJumpedUpWhileScrollingListener = listener;
    }

    private OnJumpedUpWhileScrollingListener onJumpedUpWhileScrollingListener = null;

    @Override public void onJumpedUpWhileScrolling() {
        if (onJumpedUpWhileScrollingListener != null) {
            onJumpedUpWhileScrollingListener.onJumpedUpWhileScrolling();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// disabling animation for some updates
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread public void disableAnimationForNextUpdate() {
        setItemAnimator(null);
    }

    @UiThread public void scheduleAnimationRestoring() {
        if (getItemAnimator() != null) return;
        post(() -> {if (getItemAnimator() == null) setItemAnimator(animator);});
    }

    @Override public void smoothScrollToPosition(int position) {
        if (getItemAnimator() != null) super.smoothScrollToPosition(position);
        else super.scrollToPosition(position);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// bottom/top detection
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean onBottom = true;
    public boolean getOnBottom() {
        return onBottom;
    }

    private boolean onTop = false;
    public boolean getOnTop() {
        return onTop;
    }

    @Override public void onScrolled(int dx, int dy) {
        if (dy == 0) return;
        if (getAdapter() == null) return;
        boolean lastVisible = manager.findLastVisibleItemPosition() == getAdapter().getItemCount() - 1;
        boolean firstVisible = manager.findFirstVisibleItemPosition() == 0;
        if (dy < 0 && !lastVisible) onBottom = false;
        else if (dy > 0 && lastVisible) onBottom = true;
        if (dy > 0 && !firstVisible) onTop = false;
        else if (dy < 0 && firstVisible) onTop = true;
    }

    public void recheckTopBottom() {
        if (getAdapter() == null) return;
        int firstCompletelyVisible = manager.findFirstCompletelyVisibleItemPosition();
        if (firstCompletelyVisible == RecyclerView.NO_POSITION) return;
        int lastVisible = manager.findLastVisibleItemPosition();
        onTop = firstCompletelyVisible == 0;
        onBottom = lastVisible == getAdapter().getItemCount() - 1;
    }
}