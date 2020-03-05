package com.ubergeek42.WeechatAndroid.media;

import android.text.Html;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrategyOpenGraph extends StrategyAbs {
    final private static Pattern OG = Pattern.compile(
            "<meta" +                                               // tag
            "\\s[^>]{0,50}?" +                                      // a space and optionally other parameters
            "property=(['\"])og:image(?::(?:secure_)?url)?\\1" +    // all possible image url combos
            "\\s[^>]{0,50}?" +                                      // space, etc
            "content=(['\"])(https://\\S+?)\\2",                    // an https url
            Pattern.CASE_INSENSITIVE);
    final private @Nullable Pattern regex;

    StrategyOpenGraph(String name, List<String> hosts, @Nullable String regex, int wantedBodySize) {
        super(name, hosts, wantedBodySize);
        this.regex = regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Override public @Nullable String getRequestUrl(String url) {
        if (regex == null)
            return url;
        Matcher matcher = regex.matcher(url);
        return matcher.matches() ? url : null;
    }

    @Override public @Nullable String getRequestUrlFromBody(CharSequence body) {
        Matcher matcher = OG.matcher(body);
        if (matcher.find()) {
            String escapedUrl = matcher.group(3);
            return Html.fromHtml(escapedUrl).toString();
        }
        return null;
    }
}
