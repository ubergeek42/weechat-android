package com.ubergeek42.WeechatAndroid.relay;

import android.text.Spannable;
import android.text.SpannableString;

import androidx.annotation.NonNull;

import com.ubergeek42.WeechatAndroid.utils.Linkify;
import com.ubergeek42.weechat.Color;

class TitleLine extends Line {
    private final Spannable spannable;

    TitleLine(String text) {
        super(-123, LineSpec.Type.Other, 0, "", "", null, false, false, LineSpec.DisplayAs.Unspecified, LineSpec.NotifyLevel.Low);
        spannable = new SpannableString(Color.stripEverything(text));
        Linkify.linkify(spannable);
    }

    @Override public @NonNull
    String getPrefixString() {
        return "";
    }

    @Override public @NonNull String getMessageString() {
        return spannable.toString();
    }

    @Override public @NonNull Spannable getSpannable() {
        return spannable;
    }
}
