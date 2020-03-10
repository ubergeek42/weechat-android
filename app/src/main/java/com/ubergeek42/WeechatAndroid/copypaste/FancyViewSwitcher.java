package com.ubergeek42.WeechatAndroid.copypaste;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import static com.ubergeek42.WeechatAndroid.media.Engine.ANIMATION_DURATION;

public class FancyViewSwitcher extends FrameLayout {
    final private static @Root Kitty kitty = Kitty.make();

    float alpha = 0f;

    public FancyViewSwitcher(@NonNull Context context) {
        super(context);
    }

    public FancyViewSwitcher(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FancyViewSwitcher(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int a = getChildHeight(0, widthMeasureSpec, heightMeasureSpec);
        int b = getChildHeight(1, widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) (a + (b - a) * alpha));
    }

    int getChildHeight(int i, int widthMeasureSpec, int heightMeasureSpec) {
        View child = getChildAt(i);
        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
    }

    // time in ms when the current text have been first drawn on canvas
    private long firstDrawAt = HAVE_NOT_DRAWN;
    final private static long HAVE_NOT_DRAWN = -1;

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (firstDrawAt == HAVE_NOT_DRAWN) firstDrawAt = System.currentTimeMillis();
    }

    void reset() {
        firstDrawAt = HAVE_NOT_DRAWN;
        setCrossfade(0);
        cancelAnimation();
    }

    private void setCrossfade(float alpha) {
        this.alpha = alpha;
        getChildAt(0).setAlpha(1 - alpha);
        getChildAt(1).setAlpha(alpha);
        requestLayout();
    }

    ValueAnimator animator;

    void crossfadeTo(int i) {
        cancelAnimation();
        if (i == alpha || !shouldAnimateChange()) {
            setCrossfade(i);
        } else {
            animator = ValueAnimator.ofFloat(1 - i, i).setDuration(ANIMATION_DURATION);
            animator.addUpdateListener(animation -> setCrossfade((float) animation.getAnimatedValue()));
            animator.start();
        }
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
        return firstDrawAt != HAVE_NOT_DRAWN && System.currentTimeMillis() - firstDrawAt > 50;
    }

    @Override public boolean hasOverlappingRendering() {
        return false;
    }
}
