package com.ubergeek42.WeechatAndroid.media;

import okhttp3.MediaType;

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
}
