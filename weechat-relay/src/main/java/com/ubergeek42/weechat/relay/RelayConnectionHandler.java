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
    public void onConnecting();

    /**
     * Called when a connection to the server is established, and commands can begin to be
     * sent/received.  This occurs before authentication is finished.
     */
    public void onConnect();

    /**
     * Called when a connection to the server is established, and login was successfully completed.
     * General purpose commands can be used now.
     */
    public void onAuthenticated();

    /**
     * Called when the initial list of buffers has been passed to the relay client. After this
     * method call client can assume normal workflow follows.
     */
    public void onBuffersListed();

    /**
     * Called when the server is disconnected, either through error, timeout, or because the client
     * requested a disconnect.
     */
    public void onDisconnect();

    /**
     * Called when there is an error with the connection, and provides a message as a string.
     * 
     * @param err - The error string
     * @param extraInfo - Additional data related to the error message(SSL Fingerprints, etc)
     */
    public void onError(String err, Object extraInfo);



}
