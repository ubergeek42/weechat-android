package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;

import java.util.List;

abstract public class StrategyAbs implements Strategy {
    private final String name;
    private final List<String> hosts;
    private final int wantedBodySize;

    StrategyAbs(String name, List<String> hosts) {
        this(name, hosts, 0);
    }

    StrategyAbs(String name, List<String> hosts, int wantedBodySize) {
        this.name = name;
        this.hosts = hosts;
        this.wantedBodySize = wantedBodySize;
    }

    @Override public List<String> getHosts() {
        return hosts;
    }

    @Override public RequestType requestType() {
        return wantedBodySize > 0 ? RequestType.HTML : RequestType.IMAGE;
    }

    @Override public int wantedHtmlBodySize() {
        return wantedBodySize;
    }

    @Override public String getRequestUrlFromBody(CharSequence body) {
        throw new UnsupportedOperationException();
    }

    @NonNull @Override public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }
}
