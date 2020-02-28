package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HostUtils {
    private static final Pattern HOST = Pattern.compile("^https?://([^/]+)", Pattern.CASE_INSENSITIVE);
    static @Nullable String getHost(String url) {
        Matcher matcher = HOST.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    // given a host in form "a.b.c", yields:
    // a.b.c
    // *.b.c
    //   *.c
    //     *
    static class HostIterable implements Iterable<String> {
        String host;

        HostIterable(String host) {
            this.host = host;
        }

        @Override public @NonNull Iterator<String> iterator() {
            return new Iterator<String>() {
                int index = -2;

                @Override public boolean hasNext() {
                    return index != -1;
                }

                @Override public String next() {
                    if (index == -2) {
                        index = 0;
                        return host;
                    }

                    index = host.indexOf(".", index);
                    if (index == -1) {
                        return "*";
                    } else {
                        host = host.substring(index + 1);
                        return "*." + host;
                    }
                }
            };
        }
    }
}
