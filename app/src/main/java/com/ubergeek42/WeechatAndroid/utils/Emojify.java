package com.ubergeek42.WeechatAndroid.utils;


import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.FontRequestEmojiCompatConfig;
import android.support.v4.provider.FontRequest;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;

import com.ubergeek42.WeechatAndroid.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Our own Emojifyer
 * Rationale: replace all found Emojis with their utf8 chars an pipe them through the EmojiCompat lib...
 *
 * Based on the https://github.com/emojione/emojione/tree/master/lib/android files
 * also added the EmojiCompat lib into this class for backward compat
 * The emojimapping.csv is generated on an modified version of the emojione generator.
 * I have chosen a CSV to eliminate the need of complex parsers...
 */
public class Emojify {
    private static final HashMap<String, String> _shortNameToUnicode = new HashMap<>();
    private static final Pattern SHORTNAME_PATTERN = Pattern.compile(":([-+\\w]+):");

    public static Spannable emojify(@NonNull Spannable s) {
        s = shortnameToUnicode(s);
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
        loadMapping(context);
    }

    /**
     * Replace shortnames to unicode characters.
     */
    public static Spannable shortnameToUnicode(Spannable s) {
        Matcher matcher = SHORTNAME_PATTERN.matcher(s);
        while (matcher.find()) {
            String unicode = _shortNameToUnicode.get(matcher.group(1));
            if (unicode == null) {
                Log.d("Emojify", "NOPE NO EMOJI HERE FOR "+matcher.group(1));
                continue;
            }
            SpannableStringBuilder ssb = new SpannableStringBuilder(s);
            ssb.replace(matcher.start(),matcher.end(),unicode);
            s=ssb;
        }
        return s;
    }

    private static Spannable utf8ToCompat(Spannable s){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && EmojiCompat.get().getLoadState() == EmojiCompat.LOAD_STATE_SUCCEEDED){
            s = (Spannable) EmojiCompat.get().process(s);
        }
        return s;
    }

    private static void loadMapping(Context context) {
        if (_shortNameToUnicode.size() > 0)
            return;
        InputStream in = context.getResources().openRawResource(R.raw.emojimapping);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String readline;
        try {
            while ((readline = reader.readLine()) != null) {
                if (readline.startsWith("//"))
                    continue;
                String[] split = readline.split(";+");
                String name = split[0];
                int length_alias = Integer.parseInt(split[1]);
                String[] aliases = new String[length_alias];
                System.arraycopy(split, 2, aliases, 0, length_alias);
                int length_segments = Integer.parseInt(split[2+length_alias]);
                int[] segments = new int[length_segments];
                for (int i = 0; i < length_segments; i++) {
                    segments[i] = Integer.parseInt(split[3 + length_alias + i], 16);
                }
                String unicode = new String(segments, 0, length_segments);
                _shortNameToUnicode.put(name, unicode);
                for(String alias: aliases){
                    _shortNameToUnicode.put(alias, unicode);
                }
            }
            reader.close();
            in.close();
        } catch (IOException ignore) {
        }
    }
}
