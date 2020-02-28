package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Key;

import java.security.MessageDigest;

// a cacheable url with an associated strategy on how to deal with it
public class StrategyUrl implements Key {
    final private Strategy strategy;
    final private String originalUrl;

    private StrategyUrl(Strategy strategy, String originalUrl) {
        this.strategy = strategy;
        this.originalUrl = originalUrl;
    }

    static StrategyUrl make(Strategy strategy, String originalUrl) {
        String requestUrl = strategy.getRequestUrl(originalUrl);
        return requestUrl == null ? null : new StrategyUrl(strategy, originalUrl);
    }

    String getOriginalUrl() {
        return originalUrl;
    }

    Strategy getStrategy() {
        return strategy;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(getCacheKey().getBytes(CHARSET));
    }

    @Override public boolean equals(@Nullable Object o) {
        if (o instanceof StrategyUrl) {
            StrategyUrl other = (StrategyUrl) o;
            return getCacheKey().equals(other.getCacheKey());
        }
        return false;
    }

    @Override public int hashCode() {
        return getCacheKey().hashCode();
    }

    private String getCacheKey() {
        return originalUrl;
    }

    @NonNull @Override public String toString() {
        return "StrategyUrl(" + originalUrl + ")";
    }
}
