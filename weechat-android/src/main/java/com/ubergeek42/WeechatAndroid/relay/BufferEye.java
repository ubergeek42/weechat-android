/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.relay;

import android.support.annotation.UiThread;

public interface BufferEye {

    // 1 line added on bottom
    void onLineAdded();

    // all lines should be re-rendered due to font size change and such
    @UiThread void onGlobalPreferencesChanged(boolean numberChanged);

    // server sent us all lines
    void onLinesListed();

    void onPropertiesChanged();

    void onBufferClosed();
}
