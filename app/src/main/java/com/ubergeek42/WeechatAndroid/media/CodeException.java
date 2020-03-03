package com.ubergeek42.WeechatAndroid.media;

import java.io.IOException;

@SuppressWarnings({"WeakerAccess", "unused"})
class CodeException extends IOException {
    final private int code;

    CodeException(int code) {
        this(code, null);
    }

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
