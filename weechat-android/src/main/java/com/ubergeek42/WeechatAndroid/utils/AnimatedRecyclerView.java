/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.support.annotation.UiThread;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.ubergeek42.WeechatAndroid.service.P;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimatedRecyclerView extends RecyclerView {

    private static Logger logger = LoggerFactory.getLogger("AnimatedRecyclerView");

    private final static int DELAY = 10;
    private final static int DURATION = 300;
    private final static int SCROLL_DELAY = 200;
    private final static float TRANSLATION_Y = -P._50dp;

    // set this to false to prevent scrolling by user
    // needed since animating items that are not currently displayed in RecyclerView is hard
    private boolean scrollable = true;

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
        final LinearLayoutManager manager = new LinearLayoutManagerFix(getContext(), LinearLayoutManager.VERTICAL, false) {
            // there probably should be a simpler way of doing this, but so far this method
            // is the only one i've found that is called reliably on laying out children
            @Override public void onLayoutCompleted(State state) {
                super.onLayoutCompleted(state);
                if (getChildCount() > 1) animateIfNeeded();
            }
        };
        manager.setStackFromEnd(true);
        setLayoutManager(manager);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // this is used to prevent user scrolling
    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        return !scrollable || super.dispatchTouchEvent(ev);
    }

    // this is called only when the number of items in adapter is > 1
    // this animates all children of the RecyclerView except the last `linesAlreadyDisplayed`
    @UiThread private void animateIfNeeded() {
        //logger.trace("animateIfNeeded(): linesAlreadyDisplayed={}", linesAlreadyDisplayed);
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
            scrollable = false;
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

    private Runnable onAnimationsCancelled = new Runnable() {
        @Override @UiThread public void run() {
            scrollable = true;
            if (getItemAnimator() == null) setItemAnimator(animator);
            if (scrollToPosition != -1) {
                postDelayed(new Runnable() {
                    @Override public void run() {
                        smoothScrollToPositionFix(scrollToPosition);
                        scrollToPosition = -1;
                    }
                }, SCROLL_DELAY);
            }
        }
    };

    @UiThread public void requestAnimation() {
        linesAlreadyDisplayed = getAdapter().getItemCount() - 1;
    }

    // this is needed because awakenScrollbars is protected
    @UiThread public void flashScrollbar() {
        awakenScrollBars();
    }

    // use an external ObjectAnimator instead of view.animate() because
    // the latter leaves some residue in the ViewPropertyAnimator attached to the view
    @UiThread static private void animateView(View view, final int position) {
        view.setTranslationY(TRANSLATION_Y);
        view.setAlpha(0.0f);

        AnimatorSet animations = new AnimatorSet();
        animations.playTogether(ObjectAnimator.ofFloat(view, "alpha", 1.0f, 1.0f), ObjectAnimator.ofFloat(view, "translationY", 0f));
        animations.setDuration(DURATION);
        animations.setStartDelay(position * DELAY);
        animations.start();
    }

    @UiThread public void smoothScrollToPositionAfterAnimation(final int position) {
        //logger.trace("smoothScrollToPosition({}): scrollable={}, animator={}", position, scrollable, animator);
        if (linesAlreadyDisplayed < 0 && scrollable) {
            smoothScrollToPositionFix(position);
        } else {
            scrollToPosition = position;
        }
    }

    @UiThread private void smoothScrollToPositionFix(int position) {
        LinearSmoothScrollerFix scroller = new LinearSmoothScrollerFix(this);
        scroller.setTargetPosition(position);
        getLayoutManager().startSmoothScroll(scroller);
    }

    private boolean scrollingToSomethingThatMustBeVisible = false;
    @UiThread void onScrollingToSomethingThatMustBeVisible() {
        //logger.trace("onScrollingToSomethingThatMustBeVisible()");
        scrollingToSomethingThatMustBeVisible = true;
    }

    @UiThread public boolean getIfScrollingToSomethingThatMustBeVisibleAndResetIt() {
        //logger.trace("getIfScrollingToSomethingThatMustBeVisibleAndResetIt() -> {}", scrollingToSomethingThatMustBeVisible);
        boolean ret = scrollingToSomethingThatMustBeVisible;
        scrollingToSomethingThatMustBeVisible = false;
        return ret;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread public void disableAnimationForNextUpdate() {
        setItemAnimator(null);
    }

    @UiThread public void scheduleAnimationRestoring() {
        if (getItemAnimator() != null) return;
        post(new Runnable() {
            @Override public void run() {
                if (getItemAnimator() != null) return;
                setItemAnimator(animator);
            }
        });
    }

    @Override public void smoothScrollToPosition(int position) {
        if (getItemAnimator() != null) super.smoothScrollToPosition(position);
        else super.scrollToPosition(position);
    }
}