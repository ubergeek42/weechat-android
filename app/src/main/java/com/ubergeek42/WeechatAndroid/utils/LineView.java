// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.ubergeek42.WeechatAndroid.media.Engine;
import com.ubergeek42.WeechatAndroid.media.StrategyUrl;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.ArrayList;
import java.util.List;


public class LineView extends View {
    final private @Root Kitty kitty = Kitty.make();

    final private static int THUMBNAIL_WIDTH = 250;
    final private static int THUMBNAIL_MIN_HEIGHT = 120;        // todo set to the height of 2 lines of text?
    final private static int THUMBNAIL_MAX_HEIGHT = THUMBNAIL_WIDTH * 2;
    final private static int THUMBNAIL_HORIZONTAL_MARGIN = 8;
    final private static int THUMBNAIL_VERTICAL_MARGIN = 4;

    final private static int THUMBNAIL_AREA_WIDTH = THUMBNAIL_WIDTH + THUMBNAIL_HORIZONTAL_MARGIN * 2;
    final private static int THUMBNAIL_AREA_MIN_HEIGHT = THUMBNAIL_MIN_HEIGHT + THUMBNAIL_VERTICAL_MARGIN * 2;

    private Spannable text = null;
    private Layout layout = null;
    private Bitmap[] bitmaps = null;
    private Target target;
    public LineView(Context context) {
        super(context, null);
    }

    public LineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void reset() {
        text = null;
        layout = null;
        bitmaps = null;
        Glide.with(getContext()).clear(target);
        target = null;
    }

    public void setText(Line line) {
        if (text == line.spannable && layout.getPaint() == P.textPaint) return;
        reset();

        kitty.setPrefix(line.message);

        text = line.spannable;

        Layout wideLayout = makeLayout(text, P.weaselWidth);
        setLayout(wideLayout);
        invalidate();

        List<StrategyUrl> candidates = Engine.getPossibleMediaCandidates(getUrls());

        if (candidates.isEmpty()) return;

        StrategyUrl url = candidates.get(0);
        Layout narrowLayout = makeLayout(text, P.weaselWidth - THUMBNAIL_AREA_WIDTH);

        kitty.info("fetching: %s", url);
        target = Glide.with(getContext())
                .asBitmap()
                .apply(Engine.defaultRequestOptions)
                .load(url)
                .into(new Target(THUMBNAIL_WIDTH, getThumbnailHeight(narrowLayout), wideLayout, narrowLayout));
    }

    private class Target extends CustomTarget<Bitmap> {
        final Layout wideLayout;
        final Layout narrowLayout;

        Target(int width, int height, Layout wideLayout, Layout narrowLayout) {
            super(width, height);
            this.wideLayout = wideLayout;
            this.narrowLayout = narrowLayout;
        }

        @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition transition) {
            bitmaps = new Bitmap[]{resource};
            setLayout(narrowLayout);
            invalidate();
        }

        @Override public void onLoadCleared(@Nullable Drawable placeholder) {
            if (bitmaps == null) return;
            setLayout(wideLayout);
            bitmaps = null;
            invalidate();
        }

        @Override @Cat public void onLoadFailed(@Nullable Drawable errorDrawable) {}
    }

    private void setLayout(Layout newLayout) {
        int oldHeight = layout == null ? -1 : layout.getHeight();
        if (oldHeight != newLayout.getHeight()) requestLayout();
        layout = newLayout;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // https://stackoverflow.com/questions/41779934/how-is-staticlayout-used-in-android
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Utils.isEmpty(bitmaps) ? layout.getHeight() : Math.max(layout.getHeight(), THUMBNAIL_AREA_MIN_HEIGHT);
        setMeasuredDimension(P.weaselWidth, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        layout.draw(canvas);
        if (!Utils.isEmpty(bitmaps)) {
            canvas.drawBitmap(bitmaps[0], P.weaselWidth - THUMBNAIL_WIDTH - THUMBNAIL_HORIZONTAL_MARGIN, THUMBNAIL_VERTICAL_MARGIN, null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // see android.text.method.LinkMovementMethod.onTouchEvent
    // todo call performClick()?
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int line = layout.getLineForVertical((int) event.getY());
            int off = layout.getOffsetForHorizontal(line, event.getX());
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static Layout.Alignment ALIGNMENT = Layout.Alignment.ALIGN_NORMAL;
    private final static float SPACING_MULTIPLIER = 1f;
    private final static float SPACING_ADDITION = 0f;
    private final static boolean INCLUDE_PADDING = false;

    private static Layout makeLayout(Spannable spannable, int width) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(spannable, 0, spannable.length(),
                    P.textPaint, width)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                    .build();
        } else {
            //noinspection deprecation
            return new StaticLayout(spannable, P.textPaint, width,
                    ALIGNMENT, SPACING_MULTIPLIER, SPACING_ADDITION, INCLUDE_PADDING);
        }
    }

    private static int getThumbnailHeight(Layout layout) {
        int height = layout.getHeight() - THUMBNAIL_VERTICAL_MARGIN * 2;
        if (height < THUMBNAIL_MIN_HEIGHT) height = THUMBNAIL_MIN_HEIGHT;
        if (height > THUMBNAIL_MAX_HEIGHT) height = THUMBNAIL_MAX_HEIGHT;
        return height;
    }
}
