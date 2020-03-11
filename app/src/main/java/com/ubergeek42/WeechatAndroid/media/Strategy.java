package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.Nullable;

import java.util.List;

// validates and converts urls to more usable image urls
public interface Strategy {
    class CancelFurtherAttempts extends Exception {}

    enum Size {
        BIG,        // big image shown in notifications
        SMALL       // small image shown in chat or share dialog
    }

    // get all host this strategy is handling in no particular order. "*" can replace parts one or
    // more or all leftmost parts of the host. e.g. "one.two.com", "*.two.com", "*"
    List<String> getHosts();

    // returns whether the request url is that of image, html or either. if the result is html, it
    // is read using the hint from wantedHtmlBodySize and processed using getRequestUrlFromBody
    RequestType requestType();

    // if this strategy cat process html body, this returns the number of bytes we want to
    // request from the server. this is just a guideline, the actual number of transferred bytes
    // might be smaller or greater than this
    int wantedHtmlBodySize();

    // given an url, get the same or a different url to fetch. the new url might be an http+s url,
    // a direct image url constructed from a non-direct url, or a web page for further processing.
    // returns null if no url could be constructed.
    @Nullable String getRequestUrl(String url, Size size) throws CancelFurtherAttempts;

    // get the final image url given the body, or null if no image url could be constructed
    @Nullable String getRequestUrlFromBody(CharSequence body);
}
