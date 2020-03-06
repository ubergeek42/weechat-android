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
            if (BuildConfig.DEBUG) message += ": " + Utils.getLongStringSummary(body);
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
            return "Requested: " + requestType.getShortDescription() + "; got: " + mediaType;
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

    private static String ensureText(String string) {
        return TextUtils.isEmpty(string) ? "<empty>" : string;
    }
}
