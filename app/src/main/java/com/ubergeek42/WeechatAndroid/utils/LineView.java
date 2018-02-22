// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;

import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;


public class LineView extends View {
    final private static @Root Kitty kitty = Kitty.make();

    private Spannable text = null;
    private Layout layout = null;

    public LineView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setText(Line line) {
        text = line.spannable;
        layout = makeLayout(text);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // https://stackoverflow.com/questions/41779934/how-is-staticlayout-used-in-android
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(P.weaselWidth, layout.getHeight());
    }

    @Override protected void onDraw(Canvas canvas) {
        layout.draw(canvas);
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

    private static Layout makeLayout(Spannable spannable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(spannable, 0, spannable.length(),
                    P.textPaint, P.weaselWidth)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                    .build();
        } else {
            return new StaticLayout(spannable, P.textPaint, P.weaselWidth,
                    ALIGNMENT, SPACING_MULTIPLIER, SPACING_ADDITION, INCLUDE_PADDING);
        }
    }
}
