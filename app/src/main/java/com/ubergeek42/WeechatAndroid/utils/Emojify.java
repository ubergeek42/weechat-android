package com.ubergeek42.WeechatAndroid.utils;


import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.FontRequestEmojiCompatConfig;
import android.support.v4.provider.FontRequest;
import android.text.Spannable;

import com.ubergeek42.WeechatAndroid.R;

/**
 * Our own Emojifyer
 * Rationale: pipe Emojis through the EmojiCompat lib...
 */
public class Emojify {

    public static Spannable emojify(@NonNull Spannable s) {
        return utf8ToCompat(s);
    }


    public static void init(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            FontRequest fontRequest = new FontRequest(
                    "com.google.android.gms.fonts",
                    "com.google.android.gms",
                    "Noto Color Emoji Compat",
                    R.array.com_google_android_gms_fonts_certs);
            EmojiCompat.Config config = new FontRequestEmojiCompatConfig(context, fontRequest);
            EmojiCompat.init(config);
        }
    }

    private static Spannable utf8ToCompat(Spannable s){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && EmojiCompat.get().getLoadState() == EmojiCompat.LOAD_STATE_SUCCEEDED){
            s = (Spannable) EmojiCompat.get().process(s);
        }
        return s;
    }
}
