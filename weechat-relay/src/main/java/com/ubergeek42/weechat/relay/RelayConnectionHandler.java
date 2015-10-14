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
package com.ubergeek42.weechat.relay;

/**
 * Provides notifications about the connection with the server such as when a connection is made or
 * lost.
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 * 
 */
public interface RelayConnectionHandler {
    /**
     * Called when a connection to the server is in progress.
     */
    void onConnecting();

    /**
     * Called when a connection to the server is established, and commands can begin to be
     * sent/received. This occurs before authentication is finished.
     */
    void onConnected();

    /**
     * Called when a connection to the server is established, and login was successfully completed.
     * General purpose commands can be used now.
     */
    void onAuthenticated();

    /**
     * Called when a connection to the server is established, but connection was aborted before
     * authentication succeeded. Bad password? Mismatched connection type?
     */
    void onAuthenticationFailed();

    /**
     * Called when the initial list of buffers has been passed to the relay client. After this
     * method call client can assume normal workflow follows.
     */
    void onBuffersListed();

    /**
     * Called when the server is disconnected, either through error, timeout, or because the client
     * requested a disconnect.
     */
    void onDisconnected();

    /**
     * Called when there is an error with the connection, and provides a message as a string.
     *
     */
    void onException(Exception e);
}
