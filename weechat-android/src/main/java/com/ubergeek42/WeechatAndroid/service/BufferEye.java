package com.ubergeek42.WeechatAndroid.service;

/**
 * Created by sq on 25/07/2014.
 */
public interface BufferEye {

    public void onLinesChanged();

    public void onLinesListed();

    public void onPropertiesChanged();

    public void onBufferClosed();
}
