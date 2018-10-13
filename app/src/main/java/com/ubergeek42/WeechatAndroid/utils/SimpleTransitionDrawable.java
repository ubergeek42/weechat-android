package com.ubergeek42.WeechatAndroid.utils;


import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.Nullable;

public class SimpleTransitionDrawable extends Drawable {

    private static final int TRANSITION_STARTING = 0;
    private static final int TRANSITION_RUNNING = 1;
    private static final int TRANSITION_NONE = 2;

    private int state = TRANSITION_NONE;

    private long startTimeMillis;
    private int duration;

    private @Nullable Drawable source;
    private Drawable target;

    public void setTarget(Drawable target) {
        this.source = this.target;
        this.target = target;
        target.setBounds(0, 0, target.getIntrinsicWidth(), target.getIntrinsicHeight());
    }

    public void startTransition(int durationMillis) {
        duration = durationMillis;
        state = TRANSITION_STARTING;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        int alpha;

        switch (state) {
            case TRANSITION_STARTING:
                startTimeMillis = SystemClock.uptimeMillis();
                alpha = 0;
                state = TRANSITION_RUNNING;
                break;

            case TRANSITION_RUNNING:
                float normalized = (float) (SystemClock.uptimeMillis() - startTimeMillis) / duration;
                alpha = (int) (0xff * Math.min(normalized, 1.0f));
                break;

            default:
                if (target == null) return;
                alpha = 0xff;
                break;
        }

        if (source != null && alpha < 0xff) {
            source.setAlpha(0xff - alpha);
            source.draw(canvas);
        }

        if (alpha > 0) {
            target.setAlpha(alpha);
            target.draw(canvas);
        }

        if (alpha == 0xff) {
            state = TRANSITION_NONE;
            return;
        }

        invalidateSelf();
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(ColorFilter colorFilter) {}

    @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
}

