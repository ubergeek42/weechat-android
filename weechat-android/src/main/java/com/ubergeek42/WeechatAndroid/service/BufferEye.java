package com.ubergeek42.WeechatAndroid.service;

public interface BufferEye {

    void onLinesChanged();

    void onLinesListed();

    void onPropertiesChanged();

    void onBufferClosed();
}
