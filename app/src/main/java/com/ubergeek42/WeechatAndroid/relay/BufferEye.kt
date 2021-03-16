// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.relay;

import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;


public interface BufferEye {
    // server sent us all lines
    @WorkerThread void onLinesListed();

    // 1 line added on bottom
    @WorkerThread void onLineAdded();

    // indicates changed title, local variables, number, etc
    @WorkerThread void onTitleChanged();

    // buffer was closed in weechat
    @WorkerThread void onBufferClosed();

    // all lines should be re-rendered due to font size change and such
    @MainThread void onGlobalPreferencesChanged(boolean numberChanged);
}
