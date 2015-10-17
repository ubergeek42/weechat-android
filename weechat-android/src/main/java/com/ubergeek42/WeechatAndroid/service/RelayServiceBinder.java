/*******************************************************************************
 * Copyright 2012 Keith Johnson
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid.service;

import android.os.Binder;
import android.support.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.BuildConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functions that are available to clients of the relay service
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 * 
 */
public class RelayServiceBinder extends Binder {
    private static Logger logger = LoggerFactory.getLogger("RelayServiceBinder");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    private RelayService service;

    public RelayServiceBinder(RelayService service) {
        this.service = service;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void connect() {
        service.startThreadedConnectLoop();
    }

    public void disconnect() {
        service.startThreadedDisconnect();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public @Nullable Buffer getBufferByFullName(@Nullable String fullName) {
        return BufferList.findByFullName(fullName);
    }

    /** Send a message to the server(expected to be formatted appropriately) */
    public void sendMessage(String string) {
        service.connection.sendMessage(string);
    }
}
