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
import com.bumptech.glide.request.transition.Transition;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.media.Cache;
import com.ubergeek42.WeechatAndroid.media.Engine;
import com.ubergeek42.WeechatAndroid.media.StrategyUrl;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

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

    private Layout narrowLayout = null;
    private Layout wideLayout = null;

    private Spannable text = null;
    private Layout layout = null;
    private Bitmap bitmap = null;
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
        layout = wideLayout = narrowLayout = null;
        bitmap = null;
        Glide.with(getContext()).clear(target);
        target = null;
    }

    public void setText(Line line) {
        if (text == line.spannable && layout.getPaint() == P.textPaint) return;
        reset();

        kitty.setPrefix(line.message);

        text = line.spannable;

        List<StrategyUrl> candidates = Engine.getPossibleMediaCandidates(getUrls());
        StrategyUrl url = candidates.isEmpty() ? null : candidates.get(0);
        Cache.Info info = url == null ? null : Cache.info(url);

        setLayout(info == Cache.Info.FETCHED_RECENTLY ? Which.NARROW : Which.WIDE);
        invalidate();

        if (url == null || info == Cache.Info.FAILED_RECENTLY) return;

        ensureLayout(Which.NARROW);
        kitty.info("fetching: %s", url);
        target = Glide.with(getContext())
                .asBitmap()
                .apply(Engine.defaultRequestOptions)
                .listener(Cache.listener)
                .load(url)
                .into(new Target(THUMBNAIL_WIDTH, getThumbnailHeight(narrowLayout)));
    }

    private class Target extends CustomTarget<Bitmap> {
        Target(int width, int height) {
            super(width, height);
        }

        @Override public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition transition) {
            setBitmap(resource);
        }

        @Override public void onLoadCleared(@Nullable Drawable placeholder) {
            setBitmap(null);
        }

        // the request seems to be attempted once again on minimizing/restoring the app
        // todo understand why/file a bug?
        @Override @Cat public void onLoadFailed(@Nullable Drawable errorDrawable) {
            Target local = target;
            Weechat.runOnMainThread(() -> Glide.with(getContext()).clear(local));
            setBitmap(null);
        }
    }

    private void setBitmap(@Nullable Bitmap bitmap) {
        if (this.bitmap == bitmap) return;
        this.bitmap = bitmap;
        setLayout(bitmap == null ? Which.WIDE : Which.NARROW);
        invalidate();
    }

    enum Which {WIDE, NARROW}
    private void ensureLayout(Which which) {
        if (which == Which.WIDE && wideLayout == null) wideLayout = makeLayout(text, P.weaselWidth);
        if (which == Which.NARROW && narrowLayout == null) narrowLayout = makeLayout(text, P.weaselWidth - THUMBNAIL_AREA_WIDTH);
    }

    private void setLayout(Which which) {
        ensureLayout(which);
        Layout newLayout = which == Which.WIDE ? wideLayout : narrowLayout;

        int oldHeight = layout == null ? -1 : layout.getHeight();
        if (oldHeight != newLayout.getHeight()) requestLayout();
        layout = newLayout;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // https://stackoverflow.com/questions/41779934/how-is-staticlayout-used-in-android
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = bitmap == null ? layout.getHeight() : Math.max(layout.getHeight(), THUMBNAIL_AREA_MIN_HEIGHT);
        setMeasuredDimension(P.weaselWidth, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        layout.draw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, P.weaselWidth - THUMBNAIL_WIDTH - THUMBNAIL_HORIZONTAL_MARGIN, THUMBNAIL_VERTICAL_MARGIN, null);
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
