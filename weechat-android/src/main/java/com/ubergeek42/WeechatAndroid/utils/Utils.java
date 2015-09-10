package com.ubergeek42.WeechatAndroid.utils;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Utils {

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

    /** protocol must be changed each time anything that uses the following function changes
     ** needed to make sure nothing crashes if we cannot restore the data */
    public static final int SERIALIZATION_PROTOCOL_ID = 9;

    public static @Nullable Object deserialize(@Nullable String string) {
        if (string == null) return null;
        try {
            byte[] data = Base64.decode(string, Base64.DEFAULT);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object o = ois.readObject();
            ois.close();
            return o;
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// string cuts

    // replace multiline text with one string like "foo bar baz… (3 lines)"
    public static @NonNull String cutFirst(@NonNull String text) {
        int chunks = text.split("\\r\\n|\\r|\\n").length;
        String clean = text.replaceAll("\\r\\n|\\r|\\n", " ");
        clean = cut(clean);
        if (chunks > 1)
            clean += " (" + chunks + " lines)";
        return clean;
    }

    // cut string at 100 characters
    public static @NonNull String cut(@NonNull String text) {
        return (text.length() > 100) ?
                text.substring(0, Math.min(text.length(), 100)) + "…" : text;
    }
}
