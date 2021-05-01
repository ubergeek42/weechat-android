package com.ubergeek42.WeechatAndroid.media;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.utils.Utils;

import java.io.IOException;

import okhttp3.MediaType;

class Exceptions {
    static class CodeException extends IOException {
        final private int code;

        CodeException(int code) {
            this.code = code;
        }

        int getCode() {
            return code;
        }
    }

    static class HtmlBodyLacksRequiredDataException extends CodeException {
        final CharSequence body;

        HtmlBodyLacksRequiredDataException(CharSequence body) {
            super(Cache.ERROR_HTML_BODY_LACKS_REQUIRED_DATA);
            this.body = body;
        }

        @Override @Nullable public String getMessage() {
            String message = "Couldn't get request url from body";
            if (BuildConfig.DEBUG) {
                int idx = body.toString().indexOf("og:image");
                message += idx == -1 ?
                        " (no 'og:image' found): " + Utils.getLongStringSummary(body) :
                        " ('og:image' found but regex failed): " + Utils.getLongStringExcerpt(body, idx + 5);
            }
            return message;
        }
    }

    static class ContentLengthExceedsLimitException extends CodeException {
        final long contentLength;
        final long maxBodySize;
        
        ContentLengthExceedsLimitException(long contentLength, long maxBodySize) {
            super(Cache.ERROR_UNACCEPTABLE_FILE_SIZE);
            this.contentLength = contentLength;
            this.maxBodySize = maxBodySize;
        }

        @Override @Nullable public String getMessage() {
            return "Content length of " + contentLength + " exceeds the maximum limit of " + maxBodySize;
        }
    }

    static class UnknownLengthStreamExceedsLimitException extends CodeException {
        final long maxBodySize;

        UnknownLengthStreamExceedsLimitException(long maxBodySize) {
            super(Cache.ERROR_UNACCEPTABLE_FILE_SIZE);
            this.maxBodySize = maxBodySize;
        }
        
        @Override @Nullable public String getMessage() {
            return "Stream of unspecified length exceeded the maximum limit of " + maxBodySize;
        }
    }

    static class BodySizeSmallerThanContentLengthException extends CodeException {
        final long bodySize;
        final long minBodySize;

        BodySizeSmallerThanContentLengthException(long bodySize, long minBodySize) {
            super(Cache.ERROR_UNACCEPTABLE_FILE_SIZE);
            this.bodySize = bodySize;
            this.minBodySize = minBodySize;
        }

        @Override @Nullable public String getMessage() {
            return "Body size of " + bodySize + " smaller than the minimum limit of " + minBodySize;
        }
    }

    static class UnacceptableMediaTypeException extends CodeException {
        final RequestType requestType;
        final MediaType mediaType;
        
        UnacceptableMediaTypeException(RequestType requestType, MediaType mediaType) {
            super(Cache.ERROR_UNACCEPTABLE_MEDIA_TYPE);
            this.requestType = requestType;
            this.mediaType = mediaType;
        }

        @Override @Nullable public String getMessage() {
            return "Wanted: " + requestType.getShortDescription() + "; got: " + mediaType;
        }
    }

    static class HttpException extends CodeException {
        final String reasonPhrase;
        
        HttpException(int statusCode, String reasonPhrase) {
            super(statusCode);
            this.reasonPhrase = reasonPhrase;
        }

        @Override @Nullable public String getMessage() {
            return "HTTP error " + getCode() + ": " + ensureText(reasonPhrase);
        }
    }

    static class MalformedUrlException extends CodeException {
        final String url;

        MalformedUrlException(String url) {
            super(Cache.ERROR_MALFORMED_URL);
            this.url = url;
        }

        @Override @Nullable public String getMessage() {
            return "Malformed URL: " + url;
        }
    }

    private static String ensureText(String string) {
        return TextUtils.isEmpty(string) ? "<empty>" : string;
    }

    static class SslRequiredException extends CodeException {
        SslRequiredException() {
            super(Cache.ERROR_SSL_REQUIRED);
        }

        @Override @Nullable public String getMessage() {
            return "SSL required";
        }
    }

    static class RedirectToNullStrategyException extends CodeException {
        final String url;

        RedirectToNullStrategyException(String url) {
            super(Cache.ERROR_REDIRECT_TO_NULL_STRATEGY);
            this.url = url;
        }

        @Override @Nullable public String getMessage() {
            return "Redirected to an address that has a null strategy: " + url;
        }
    }
}
