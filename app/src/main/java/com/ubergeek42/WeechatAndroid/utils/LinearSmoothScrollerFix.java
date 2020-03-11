// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.graphics.PointF;
import androidx.recyclerview.widget.LinearSmoothScroller;

// if the position of the element we are scrolling to is *just* outside the RecyclerView, i.e.
// if it's not a child of it, an attempt to scroll to it may lead to overscrolling—the View appears
// in the middle of RecyclerView—and then everything is scrolled in the opposite direction, until
// the View touches the border of RecyclerView. The end result is fine, but animation looks pretty
// bad. the problem is in that the first scroll is performed *before* extra Views in the direction
// of the scroll are created. this class mitigates the problem, until it's fixed by google
class LinearSmoothScrollerFix extends LinearSmoothScroller {
    private final AnimatedRecyclerView recycler;

    LinearSmoothScrollerFix(AnimatedRecyclerView recycler) {
        super(recycler.getContext());
        this.recycler = recycler;
    }

    // called by android.support.v7.widget.RecyclerView.SmoothScroller.start
    @SuppressWarnings("ConstantConditions")
    @Override protected void onStart() {
        super.onStart();
        if (findViewByPosition(getTargetPosition()) == null) {
            PointF point = computeScrollVectorForPosition(getTargetPosition());
            if (point == null) return;
            recycler.scrollBy(0, point.y > 0 ? 1 : -1);
        }
    }

    //@Override protected void onSeekTargetStep(int dx, int dy, RecyclerView.State state, Action action) {
    //    super.onSeekTargetStep(dx, dy, state, action);
    //    kitty.trace("onSeekTargetStep(%s, %s, ...)", dx, dy);
    //}

    //private static final float MILLISECONDS_PER_INCH = 50f;       // default = 25f
    //@Override protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
    //    return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
    //}
}
