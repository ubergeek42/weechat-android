// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.media.Cache;
import com.ubergeek42.WeechatAndroid.media.Engine;
import com.ubergeek42.WeechatAndroid.media.StrategyUrl;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.List;

import static com.ubergeek42.WeechatAndroid.media.Engine.*;

public class LineView extends View {
    final private @Root Kitty kitty = Kitty.make();

    final private static int THUMBNAIL_AREA_WIDTH = THUMBNAIL_WIDTH + THUMBNAIL_HORIZONTAL_MARGIN * 2;
    final private static int THUMBNAIL_AREA_MIN_HEIGHT = THUMBNAIL_MIN_HEIGHT + THUMBNAIL_VERTICAL_MARGIN * 2;

    private AlphaLayout narrowLayout = null;
    private AlphaLayout wideLayout = null;

    private Spannable text = null;
    private Bitmap image = null;
    private Target target;

    private enum State {TEXT_ONLY, WITH_IMAGE, ANIMATING}
    enum LayoutType {WIDE, NARROW}

    private State state = State.TEXT_ONLY;

    private static int counter = 0;

    public LineView(Context context) {
        this(context, null);
    }

    public LineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // setLayerType(LAYER_TYPE_HARDWARE, null);
        kitty.setPrefix(String.valueOf(counter++));
    }

    private void reset() {
        text = null;
        wideLayout = narrowLayout = null;
        image = null;
        animatedValue = 0f;
        firstDrawAt = HAVE_NOT_DRAWN;
        state = State.TEXT_ONLY;
        if (animator != null) animator.cancel();
        animator = null;
        Glide.with(getContext()).clear(target);     // will call the listener!
        target = null;
    }

    public void setText(Line line) {
        if (text == line.spannable && getCurrentLayout().getPaint() == P.textPaint) return;
        reset();

        text = line.spannable;

        List<StrategyUrl> candidates = Engine.getPossibleMediaCandidates(getUrls());
        StrategyUrl url = candidates.isEmpty() ? null : candidates.get(0);
        Cache.Info info = url == null ? null : Cache.info(url);

        setLayout(info == Cache.Info.FETCHED_RECENTLY ? LayoutType.NARROW : LayoutType.WIDE);
        invalidate();

        if (url == null || info == Cache.Info.FAILED_RECENTLY) return;

        ensureLayout(LayoutType.NARROW);
        target = Glide.with(getContext())
                .asBitmap()
                .apply(Engine.defaultRequestOptions)
                .listener(Cache.bitmapListener)
                .load(url)
                .into(new Target(THUMBNAIL_WIDTH, getThumbnailHeight()));
    }

    private class Target extends CustomTarget<Bitmap> {
        Target(int width, int height) {
            super(width, height);
        }

        @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition transition) {
            setImage(resource);
        }

        @Override public void onLoadCleared(@Nullable Drawable placeholder) {
            setImage(null);
        }

        // the request seems to be attempted once again on minimizing/restoring the app. to avoid
        // that, clear target soon, but not on current thread as the library doesn't allow it
        @Override public void onLoadFailed(@Nullable Drawable errorDrawable) {
            Target local = target;
            Weechat.runOnMainThread(() -> Glide.with(getContext()).clear(local));
            setImage(null);
        }
    }

    private void setImage(@Nullable Bitmap image) {
        if (this.image == image && !(image == null && state == State.WITH_IMAGE)) return;
        this.image = image;
        if (text == null) return;   // text can be null if called from reset(), in this case don't proceed
        if (shouldAnimateChange()) {
            animateChange();
        } else {
            setLayout(image == null ? LayoutType.WIDE : LayoutType.NARROW);
            invalidate();
        }
    }

    private AlphaLayout getCurrentLayout() {
        return (state == State.WITH_IMAGE) ? narrowLayout : wideLayout;
    }

    private void ensureLayout(LayoutType layoutType) {
        if (layoutType == LayoutType.WIDE && wideLayout == null) wideLayout = AlphaLayout.make(text, P.weaselWidth);
        if (layoutType == LayoutType.NARROW && narrowLayout == null) narrowLayout = AlphaLayout.make(text, P.weaselWidth - THUMBNAIL_AREA_WIDTH);
    }

    private void setLayout(LayoutType layoutType) {
        ensureLayout(layoutType);
        int oldHeight = getCurrentLayout() == null ? -1 : getCurrentLayout().getHeight();
        this.state = layoutType == LayoutType.WIDE ? State.TEXT_ONLY : State.WITH_IMAGE;
        if (getCurrentLayout() != null && oldHeight != getCurrentLayout().getHeight()) requestLayout();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(P.weaselWidth, getViewHeight(state));
    }

    private int getViewHeight(State state) {
        if (state == State.TEXT_ONLY) return wideLayout.getHeight();
        if (state == State.WITH_IMAGE) return Math.max(narrowLayout.getHeight(), THUMBNAIL_AREA_MIN_HEIGHT);

        int wideHeight = getViewHeight(State.TEXT_ONLY);
        int narrowHeight = getViewHeight(State.WITH_IMAGE);
        return wideHeight + ((int) ((narrowHeight - wideHeight) * animatedValue));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (state == State.ANIMATING) {
            wideLayout.draw(canvas, 1f - animatedValue);
            narrowLayout.draw(canvas, animatedValue);
        } else {
            getCurrentLayout().draw(canvas);
        }
        if (image != null) canvas.drawBitmap(image,
                P.weaselWidth - THUMBNAIL_WIDTH - THUMBNAIL_HORIZONTAL_MARGIN,
                THUMBNAIL_VERTICAL_MARGIN,
                state == State.ANIMATING ? narrowLayout.getAlphaPaint() : null);
        if (firstDrawAt == HAVE_NOT_DRAWN) firstDrawAt = System.currentTimeMillis();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // see android.text.method.LinkMovementMethod.onTouchEvent
    // todo call performClick()?
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int line = getCurrentLayout().getLineForVertical((int) event.getY());
            int off = getCurrentLayout().getOffsetForHorizontal(line, event.getX());
            ClickableSpan[] links = text.getSpans(off, off, ClickableSpan.class);
            if (links.length > 0) {
                links[0].onClick(this);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public URLSpan[] getUrls() {
        return text.getSpans(0, text.length(), URLSpan.class);
    }

    private int getThumbnailHeight() {
        int height = narrowLayout.getHeight() - THUMBNAIL_VERTICAL_MARGIN * 2;
        if (height < THUMBNAIL_MIN_HEIGHT) height = THUMBNAIL_MIN_HEIGHT;
        if (height > THUMBNAIL_MAX_HEIGHT) height = THUMBNAIL_MAX_HEIGHT;
        return height;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////// animation
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // a value that is used to animate view size and the crossfade. 0f corresponds to the initial
    // text-only layout, 1f to the layout with image. can go both directions
    private float animatedValue = 0f;

    // time in ms when the current text have been first drawn on canvas
    private long firstDrawAt = HAVE_NOT_DRAWN;

    private @Nullable ValueAnimator animator;
    final private static long HAVE_NOT_DRAWN = -1;

    private void animateChange() {
        prepareForAnimation();
        boolean hasImage = image != null;
        boolean needsRelayout = getViewHeight(State.TEXT_ONLY) != getViewHeight(State.WITH_IMAGE);
        float from = hasImage ? 0f : 1f;
        float to = hasImage ? 1f : 0f;
        animator = ValueAnimator.ofFloat(from, to).setDuration(ANIMATION_DURATION);
        animator.addUpdateListener(animation -> {
            animatedValue = (float) animation.getAnimatedValue();
            if (animatedValue == to) {
                state = hasImage ? State.WITH_IMAGE : State.TEXT_ONLY;
                narrowLayout.clearBitmap();
                wideLayout.clearBitmap();
            }
            if (needsRelayout) requestLayout();
            invalidate();
        });
        animator.start();
        state = State.ANIMATING;
    }

    public void cancelAnimation() {
        if (animator != null) animator.cancel();
    }

    // animate layout change—but only if the view is visible and has been visible for some minimum
    // period of time, to avoid too much animation. see https://stackoverflow.com/a/12428154/1449683
    final private Rect _rect = new Rect();
    private boolean shouldAnimateChange() {
        if (!isAttachedToWindow() || getParent() == null)
             return false;
        ((View) getParent()).getHitRect(_rect);
        if (!getLocalVisibleRect(_rect))
            return false;
        long time = System.currentTimeMillis();
        return firstDrawAt != HAVE_NOT_DRAWN && time - firstDrawAt > 50;
    }

    private void prepareForAnimation() {
        ensureLayout(LayoutType.WIDE);
        ensureLayout(LayoutType.NARROW);
        wideLayout.ensureBitmap();
        narrowLayout.ensureBitmap();
    }
}