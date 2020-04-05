package com.ubergeek42.WeechatAndroid.media;

import java.util.List;

class StrategyNull extends Strategy {
    StrategyNull(String name, List<String> hosts) {
        super(name, hosts);
    }

    @Override Url make(String url, Size size) throws CancelFurtherAttempts {
        throw new Strategy.CancelFurtherAttempts();
    }
}
