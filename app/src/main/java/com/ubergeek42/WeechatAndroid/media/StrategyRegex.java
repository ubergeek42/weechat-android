package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StrategyRegex extends StrategyAbs {
    final private Pattern regex;
    final private String replacement;

    StrategyRegex(String name, List<String> hosts, String regex, String replacement) {
        super(name, hosts);
        this.regex = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.replacement = replacement;
    }

    @Override public @Nullable String getRequestUrl(String url) {
        Matcher matcher = regex.matcher(url);
        return matcher.matches() ? matcher.replaceFirst(replacement) : null;
    }
}
