package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.util.Preconditions;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;

class StrategyRegex extends Strategy {
    final private @Nullable Pattern regex;
    final private @Nullable String replacementSmall;
    final private @Nullable String replacementBig;

    StrategyRegex(String name, List<String> hosts, @Nullable String regex,
                  @Nullable String replacementSmall, @Nullable String replacementBig) {
        super(name, hosts);
        this.regex = regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.replacementSmall = replacementSmall;
        this.replacementBig = replacementBig;
    }

    @Nullable @Override Url make(String originalUrl, Size size) {
        String modifiedUrl = originalUrl;
        if (regex != null) {
            Matcher matcher = regex.matcher(originalUrl);
            if (!matcher.matches())
                return null;
            String replacement = size == Size.SMALL ? replacementSmall : replacementBig;
            modifiedUrl = replacement != null ? matcher.replaceFirst(replacement) : originalUrl;
        }
        return new Url(originalUrl, modifiedUrl);
    }

    private class Url extends Strategy.Url {
        final String originalUrl;
        final String modifiedUrl;

        private Url(String originalUrl, String modifiedUrl) {
            this.originalUrl = originalUrl;
            this.modifiedUrl = modifiedUrl;
        }

        @Override String getCacheKey() {
            return modifiedUrl;
        }

        @NonNull @Override Request getFirstRequest() {
            return RequestType.IMAGE.makeRequest(modifiedUrl).build();
        }

        @Nullable @Override Request getNextRequest(@NonNull Response response, InputStream stream) throws Exceptions.UnacceptableMediaTypeException {
            MediaType responseType = Preconditions.checkNotNull(response.body()).contentType();
            if (!RequestType.IMAGE.matches(responseType))
                throw new Exceptions.UnacceptableMediaTypeException(RequestType.IMAGE, responseType);
            return null;
        }
    }
}
