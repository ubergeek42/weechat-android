package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Key;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;

import okhttp3.Request;
import okhttp3.Response;

// validates and converts urls to more usable image urls
abstract public class Strategy {
    static class CancelFurtherAttempts extends Exception {}

    private final String name;
    private final List<String> hosts;

    // image size: big images are shown in notifications and small images are shown in chat/share
    public enum Size {
        BIG,
        SMALL
    }

    Strategy(String name, List<String> hosts) {
        this.name = name;
        this.hosts = hosts;
    }

    // get all host this strategy is handling in no particular order. "*" can replace parts one or
    // more or all leftmost parts of the host. e.g. "one.two.com", "*.two.com", "*"
    List<String> getHosts() {
        return hosts;
    }

    // make an instance of Url that can fetch an image of the desired Size. returns null if the
    // Strategy can't handle that url. throws CancelFurtherAttempts if no more attempts to deal
    // with this url should be made
    abstract @Nullable Strategy.Url make(String url, Size size) throws CancelFurtherAttempts;

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public abstract class Url implements Key {
        // first request to be made. the response, if successful, will be passed to getNextRequest()
        abstract @NonNull Request getFirstRequest();

        // parses response and returns one of two things:
        // * a new Request that should be performed
        // * null if the current response is satisfactory and stream can be used
        abstract @Nullable Request getNextRequest(@NonNull Response response, InputStream stream) throws IOException;

        // a string that uniquely identifies this request. see documentation for Key
        abstract String getCacheKey();

        ////////////////////////////////////////////////////////////////////////////////////////////

        @Override public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
            messageDigest.update(getCacheKey().getBytes(CHARSET));
        }

        @Override public boolean equals(@Nullable Object o) {
            if (o instanceof Url) {
                Url other = (Url) o;
                return getCacheKey().equals(other.getCacheKey());
            }
            return false;
        }

        @Override public int hashCode() {
            return getCacheKey().hashCode();
        }

        @NonNull @Override public String toString() {
            return getClass().getSimpleName() + "(" + Strategy.this.name + ": " + getCacheKey() + ")";
        }
    }

    @NonNull @Override public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }
}
