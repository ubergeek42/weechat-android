package com.ubergeek42.WeechatAndroid.media;

import okhttp3.MediaType;
import okhttp3.Request;

enum RequestType {
    HTML("text/html", "text/html"),
    IMAGE("image/bpm, image/gif, image/jpeg, image/png, image/webp, image/heif", "image/..."),
    HTML_OR_IMAGE(IMAGE.acceptHeader + ", " + HTML.acceptHeader + ";q=0.5", "image/..., text/html");

    final private String acceptHeader;
    final private String shortDescription;

    RequestType(String acceptHeader, String shortDescription) {
        this.acceptHeader = acceptHeader;
        this.shortDescription = shortDescription;
    }

    String getAcceptHeader() {
        return acceptHeader;
    }

    String getShortDescription() {
        return shortDescription;
    }

    boolean matches(MediaType responseType) {
        if (responseType == null) return false;
        String typeWithoutParams = responseType.type() + "/" + responseType.subtype();
        return getAcceptHeader().contains(typeWithoutParams);
    }

    Request.Builder makeRequest(String url) throws Exceptions.MalformedUrlException {
        Request.Builder builder = new Request.Builder().header("Accept", getAcceptHeader());
        try {
            builder.url(url);

            // While using an invalid URL such as `http://...` will throw IllegalArgumentException,
            // OkHttp will happily accept a similarly invalid url `http://…`, normalizing it to `http://...`.
            // Using such an URL crashes the dispatcher. So we check if the host contains empty labels.
            // TODO remove when fixed in OkHttp; see https://github.com/square/okhttp/issues/7301
            if (builder.getUrl$okhttp().host().contains("..")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new Exceptions.MalformedUrlException(url);
        }
        return builder;
    }
}
