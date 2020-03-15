package com.ubergeek42.WeechatAndroid.media;

import java.util.List;

class StrategyNull extends Strategy {
    StrategyNull(List<String> hosts) {
        super("Null", hosts);
    }

    @Override Url make(String url, Size size) throws CancelFurtherAttempts {
        throw new Strategy.CancelFurtherAttempts();
    }
}
