package com.ubergeek42.WeechatAndroid.utils;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.widget.ImageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;

public class Utils {

    final private static Logger logger = LoggerFactory.getLogger("Utils");

    public static void setImageDrawableWithFade(final @NonNull ImageView imageView,
                                                final @NonNull Drawable drawable, int duration) {
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

    //////////////////////////////////////////////////////////////////////////////////////////////// serialization

    public static @Nullable Object deserialize(@Nullable String string) {
        if (string == null) return null;
        try {
            byte[] data = Base64.decode(string, Base64.DEFAULT);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object o = ois.readObject();
            ois.close();
            return o;
        } catch (Exception e) {
            logger.error("deserialize()", e);
            return null;
        }
    }

    public static @Nullable String serialize(@Nullable Serializable serializable) {
        if (serializable == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(serializable);
            oos.close();
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            logger.error("serialize()", e);
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// string cuts

    public static @NonNull String unCrLf(@NonNull String text) {
        return text.replaceAll("\\r\\n|\\r|\\n", "⏎ ");
    }

    // cut string at 100 characters
    public static @NonNull String cut(@NonNull String text, int at) {
        return (text.length() > at) ?
                text.substring(0, Math.min(text.length(), at)) + "…" : text;
    }

    public static boolean isAllDigits(@Nullable String s) {
        if (s == null || s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i)))
                return false;
        return true;
    }

    @SuppressLint("SimpleDateFormat")
    public static boolean isValidTimestampFormat(@Nullable String s) {
        if (s == null)
            return false;
        try {
            new SimpleDateFormat(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isAnyOf(String left, String ... rights) {
        for (String right : rights)
            if (left.equals(right))
                return true;
        return false;
    }
}
