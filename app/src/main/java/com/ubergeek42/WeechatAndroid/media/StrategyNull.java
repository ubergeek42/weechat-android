package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;

import java.util.List;

public class StrategyNull extends StrategyAbs {
    StrategyNull(List<String> hosts) {
        super("Null", hosts);
    }

    @NonNull @Override public String getRequestUrl(String url) throws CancelFurtherAttempts {
        throw new CancelFurtherAttempts();
    }
}
