// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.relay

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread

interface BufferEye {
    // server sent us all lines
    @WorkerThread fun onLinesListed()

    // 1 line added on bottom
    @WorkerThread fun onLineAdded()

    // indicates changed title
    @WorkerThread fun onTitleChanged()

    // buffer was closed in weechat
    @WorkerThread fun onBufferClosed()

    // all lines should be re-rendered due to font size change and such
    @MainThread fun onGlobalPreferencesChanged(numberChanged: Boolean)
}