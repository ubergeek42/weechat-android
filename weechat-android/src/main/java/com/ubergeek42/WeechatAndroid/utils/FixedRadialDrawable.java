package com.ubergeek42.WeechatAndroid.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** a drawable that draws an opaque radial shader (colors: doge -> black)
 ** starting from bottom middle. radius is 1.7x the width
 ** it is necessary because—drums—android's built-in radial drawable is just broken. my ass. */
public class FixedRadialDrawable extends Drawable {
    private Paint paint;

    public FixedRadialDrawable() {
        super();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setDither(true);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        RadialGradient gradient = new RadialGradient(
                bounds.width()/2,
                bounds.height(),
                bounds.width() * 1.7f,
                0xFF1B1F22, Color.BLACK,
                android.graphics.Shader.TileMode.CLAMP);
        paint.setShader(gradient);
    }

     @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(0, 0, getBounds().width(), getBounds().height(), paint);
    }
}
