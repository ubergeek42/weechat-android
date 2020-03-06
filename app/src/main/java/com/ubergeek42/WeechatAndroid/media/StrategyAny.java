package com.ubergeek42.WeechatAndroid.media;

import java.util.List;

public class StrategyAny extends StrategyOpenGraph {
    StrategyAny(String name, List<String> hosts, int wantedBodySize) {
        super(name, hosts, null, null, wantedBodySize);
    }

    @Override public RequestType requestType() {
        return RequestType.HTML_OR_IMAGE;
    }
}
