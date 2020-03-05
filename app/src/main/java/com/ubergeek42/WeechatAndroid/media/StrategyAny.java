package com.ubergeek42.WeechatAndroid.media;

import java.util.List;

public class StrategyAny extends StrategyOpenGraph {
    StrategyAny(List<String> hosts, int wantedBodySize) {
        super("Any", hosts, null, wantedBodySize);
    }

    @Override public RequestType requestType() {
        return RequestType.HTML_OR_IMAGE;
    }
}
