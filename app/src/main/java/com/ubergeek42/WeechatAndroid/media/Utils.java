package com.ubergeek42.WeechatAndroid.media;

import static com.ubergeek42.WeechatAndroid.utils.LoggingKt.jwarn;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;


public class Utils {
    // this is used to prevent random crashes when trying to load images while the activity somehow
    // got destroyed.
    // see: https://github.com/ubergeek42/weechat-android/issues/470
    // see: https://github.com/bumptech/glide/issues/803
    public static boolean isContextValidForGlide(@Nullable Context context) {
        if (context == null) {
            jwarn("isContextValidForGlide(): null context!");
            return false;
        }

        if (context instanceof Activity) {
            boolean finishing = ((Activity) context).isFinishing();
            boolean destroyed = ((Activity) context).isDestroyed();

            if (finishing || destroyed) {
                jwarn(() -> String.format("isContextValidForGlide(): activity in a bad state! " +
                        "finishing: %s, destroyed: %s ", finishing, destroyed));
                return false;
            }
        }

        return true;
    }
}
