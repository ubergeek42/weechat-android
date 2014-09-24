package com.ubergeek42.WeechatAndroid.utils;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.widget.ImageView;

public class Utils {

    public static void setImageDrawableWithFade(final ImageView imageView, final Drawable drawable, int duration) {
        Drawable current = imageView.getDrawable();

        if ((current != null) && (current instanceof TransitionDrawable))
            current = ((LayerDrawable) current).getDrawable(1);

        if (current != null) {
            TransitionDrawable transitionDrawable = new TransitionDrawable(new Drawable[]{current, drawable});
            transitionDrawable.setCrossFadeEnabled(true);
            imageView.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(duration);
        } else {
            imageView.setImageDrawable(drawable);
        }
    }
}
