package com.ubergeek42.WeechatAndroid.media;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

public class Utils {
    final private static @Root Kitty kitty = Kitty.make();

    // this is used to prevent random crashes when trying to load images while the activity somehow
    // got destroyed.
    // see: https://github.com/ubergeek42/weechat-android/issues/470
    // see: https://github.com/bumptech/glide/issues/803
    public static boolean isContextValidForGlide(@Nullable Context context) {
        if (context == null) {
            kitty.warn("isContextValidForGlide(): null context!");
            return false;
        }

        if (context instanceof Activity) {
            boolean finishing = ((Activity) context).isFinishing();
            boolean destroyed = ((Activity) context).isDestroyed();

            if (finishing || destroyed) {
                kitty.warn("isContextValidForGlide(): activity in a bad state! " +
                        "finishing: %s, destroyed: %s ", finishing, destroyed);
                return false;
            }
        }

        return true;
    }
}
