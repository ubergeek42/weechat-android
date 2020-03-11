package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StrategyRegex extends StrategyAbs {
    final private Pattern regex;
    final private String replacementSmall;
    final private String replacementBig;

    StrategyRegex(String name, List<String> hosts, String regex, @Nullable String replacementSmall, @Nullable String replacementBig) {
        super(name, hosts);
        this.regex = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.replacementSmall = replacementSmall;
        this.replacementBig = replacementBig;
    }

    @Nullable @Override public String getRequestUrl(String url, Size size) {
        Matcher matcher = regex.matcher(url);
        if (!matcher.matches())
            return null;
        String replacement = size == Size.SMALL ? replacementSmall : replacementBig;
        return replacement != null ? matcher.replaceFirst(replacement) : url;
    }
}
