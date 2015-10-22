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

import com.ubergeek42.weechat.relay.connection.Connection;
import com.ubergeek42.weechat.relay.protocol.Info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayConnection implements Connection, Connection.Observer {
    private static Logger logger = LoggerFactory.getLogger("RelayConnection");

    final private static String ID_VERSION = "version";
    final private static String ID_LIST_BUFFERS = "listbuffers";

    private Connection.Observer observer;

    private String password;
    Connection connection;

    public RelayConnection(Connection connection, String password) {
        this.connection = connection;
        this.password = password;
        connection.setObserver(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void sendMessage(String id, String cmd, String args) {
        sendMessage((id == null) ? cmd + " " + args : "(" + id + ") " + cmd + " " + args);
    }

    @Override public void sendMessage(String string) {
        if (!string.endsWith("\n")) string += "\n";
        connection.sendMessage(string);
    }

    @Override public void setObserver(Observer observer) {
        this.observer = observer;
    }

    @Override public STATE getState() {
        return null;
    }

    public void connect() {
        logger.debug("connect()");
        connection.connect();
    }

    public void disconnect() {
        logger.debug("disconnect()");
        sendMessage("quit");
        connection.disconnect();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override public void onStateChanged(STATE state) {
        observer.onStateChanged(state);
        if (state == STATE.CONNECTED) authenticate();
    }

    // ALWAYS followed by onStateChanged(STATE.SHUT_DOWN). might be StreamClosed
    @Override public void onException(Exception e) {
        observer.onException(e);
    }

    @Override public void onMessage(RelayMessage message) {
        String id = message.getID();
        logger.debug("onMessage(id = {})", id);

        if (ID_VERSION.equals(id)) {
            logger.debug("WeeChat version: {}", ((Info) message.getObjects()[0]).getValue());
            onStateChanged(STATE.AUTHENTICATED);
        }

        observer.onMessage(message);

        // ID_LIST_BUFFERS must get requested after onAuthenticated() (BufferList does that)
        if (ID_LIST_BUFFERS.equals(id)) onStateChanged(STATE.BUFFERS_LISTED);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// auth

    private void authenticate() {
        sendMessage(null, "init", "password=" + password + ",compression=zlib");
        sendMessage(ID_VERSION, "info", "version");
    }

}
