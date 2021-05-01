package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.relay.Line;

import java.util.List;
import java.util.regex.Pattern;

class LineFilter {
    final private @Nullable List<String> nicks;
    final private @Nullable Pattern pattern;

    LineFilter(@Nullable List<String> nicks, @Nullable String regex) {
        this.nicks = nicks;
        this.pattern = regex == null ? null : Pattern.compile(regex);
    }

    boolean filters(Line line) {
        return (nicks == null || nicks.contains(line.nick)) &&
                (pattern == null || pattern.matcher(line.getMessageString()).find());
    }
}
