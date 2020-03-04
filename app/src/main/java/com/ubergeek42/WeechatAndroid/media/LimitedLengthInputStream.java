package com.ubergeek42.WeechatAndroid.media;

import androidx.annotation.NonNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedLengthInputStream extends FilterInputStream {
    private long readByteCount = 0;
    final private long minByteCount;
    final private long maxByteCount;

    LimitedLengthInputStream(InputStream in, long minByteCount, long maxByteCount) {
        super(in);
        this.minByteCount = minByteCount;
        this.maxByteCount = maxByteCount;
    }

    @Override public int read() throws IOException {
        int data = super.read();
        check(data != -1 ? 1 : -1);
        return data;
    }

    @Override public int read(@NonNull byte[] b, int off, int len) throws IOException {
        int read = super.read(b, off, len);
        check(read);
        return read;
    }

    private void check(int read) throws IOException {
        if (read < 0) {
            if (readByteCount < minByteCount)
                throw new Exceptions.BodySizeSmallerThanContentLengthException(readByteCount, minByteCount);
        } else {
            readByteCount += read;
            if (readByteCount > maxByteCount)
                throw new Exceptions.UnknownLengthStreamExceedsLimitException(maxByteCount);
        }
    }
}
