package com.ubergeek42.WeechatAndroid.media;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.utils.Utils;

import java.io.IOException;

import okhttp3.MediaType;

class Exceptions {
    static class CodeException extends IOException {
        final private int code;

        CodeException(int code, String message) {
            this(code, message, null);
        }

        CodeException(int code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        int getCode() {
            return code;
        }
    }

    static class HtmlBodyLacksRequiredDataException extends CodeException {
        HtmlBodyLacksRequiredDataException(CharSequence body) {
            super(Cache.ERROR_HTML_BODY_LACKS_REQUIRED_DATA, getHelpfulMessage(body));
        }

        private static String getHelpfulMessage(CharSequence body) {
            String message = "Couldn't get request url from body";
            if (BuildConfig.DEBUG) message += ": " + Utils.getLongStringSummary(body);
            return message;
        }
    }

    static class ContentLengthExceedsLimitException extends CodeException {
        ContentLengthExceedsLimitException(long contentLength, long maxBodySize) {
            super(Cache.ERROR_UNACCEPTABLE_FILE_SIZE,
                    "Content length of " + contentLength + " exceeds the maximum limit of " + maxBodySize);
        }
    }

    static class UnknownLengthStreamExceedsLimitException extends CodeException {
        UnknownLengthStreamExceedsLimitException(long maxBodySize) {
            super(Cache.ERROR_UNACCEPTABLE_FILE_SIZE,
                    "Stream of unspecified length exceeded the maximum limit of " + maxBodySize);
        }
    }

    static class BodySizeSmallerThanContentLengthException extends CodeException {
        BodySizeSmallerThanContentLengthException(long bodySize, long minBodySize) {
            super(Cache.ERROR_UNACCEPTABLE_FILE_SIZE,
                    "Body size of " + bodySize + " smaller than the minimum limit of " + minBodySize);
        }
    }

    static class UnacceptableMediaTypeException extends CodeException {
        UnacceptableMediaTypeException(RequestType requestType, MediaType mediaType) {
            super(Cache.ERROR_UNACCEPTABLE_MEDIA_TYPE,
                    "Requested: " + requestType.getShortDescription() + "; got: " + mediaType);
        }
    }
}
