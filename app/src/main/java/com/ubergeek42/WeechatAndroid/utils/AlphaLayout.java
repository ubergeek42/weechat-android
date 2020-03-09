package com.ubergeek42.WeechatAndroid.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.ubergeek42.WeechatAndroid.service.P;

// a class that mimics Layout but one that can draw with alpha by drawing into a Bitmap first.
class AlphaLayout {
    final private Layout layout;
    final private Paint alphaPaint = new Paint();

    private Bitmap bitmap;

    private AlphaLayout(Layout layout) {
        this.layout = layout;
    }

    void draw(Canvas canvas, float alpha) {
        ensureBitmap();
        alphaPaint.setAlpha((int) (alpha * 255));
        canvas.drawBitmap(bitmap, 0, 0, alphaPaint);
    }

    void ensureBitmap() {
        if (bitmap != null) return;
        bitmap = Bitmap.createBitmap(layout.getWidth(), layout.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        layout.draw(c);
    }

    void clearBitmap() {
        bitmap = null;
    }

    Paint getAlphaPaint() {
        return alphaPaint;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static Layout.Alignment ALIGNMENT = Layout.Alignment.ALIGN_NORMAL;
    private final static float SPACING_MULTIPLIER = 1f;
    private final static float SPACING_ADDITION = 0f;
    private final static boolean INCLUDE_PADDING = false;

    static AlphaLayout make(Spannable spannable, int width) {
        Layout layout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                StaticLayout.Builder.obtain(spannable, 0, spannable.length(),
                        P.textPaint, width)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                        .build() :
                new StaticLayout(spannable, P.textPaint, width,
                        ALIGNMENT, SPACING_MULTIPLIER, SPACING_ADDITION, INCLUDE_PADDING);
        return new AlphaLayout(layout);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    TextPaint getPaint() {return layout.getPaint();}
    int getHeight() {return layout.getHeight();}
    void draw(Canvas canvas) {layout.draw(canvas);}
    int getLineForVertical(int vertical) {return layout.getLineForVertical(vertical);}
    int getOffsetForHorizontal(int line, float horiz) {return layout.getOffsetForHorizontal(line, horiz);}
}
