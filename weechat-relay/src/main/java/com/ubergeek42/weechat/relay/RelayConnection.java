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

import com.ubergeek42.weechat.relay.connection.AbstractConnection.StreamClosed;
import com.ubergeek42.weechat.relay.connection.Connection;
import com.ubergeek42.weechat.relay.protocol.Info;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ubergeek42.weechat.relay.connection.Connection.STATE;

public class RelayConnection implements Connection.Observer {
    private static Logger logger = LoggerFactory.getLogger("RelayConnection");

    final private static String ID_VERSION = "version";
    final private static String ID_LIST_BUFFERS = "listbuffers";

    private RelayMessageHandler messageHandler;
    private RelayConnectionHandler connectionHandler;

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

    public void sendMessage(String string) {
        if (!string.endsWith("\n")) string += "\n";
        connection.sendMessage(string);
    }

    public boolean isAlive() {
        return connection.getState() == STATE.CONNECTING || connection.getState() == STATE.CONNECTED;
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

    public void setConnectionHandler(RelayConnectionHandler handler) {
        connectionHandler = handler;
    }

    public void setMessageHandler(RelayMessageHandler handler) {
        messageHandler = handler;
    }

    @Override public void onStateChanged(STATE state) {
        switch (state) {
            case CONNECTING: connectionHandler.onConnecting(); break;
            case CONNECTED: connectionHandler.onConnected(); break;
            case DISCONNECTED: connectionHandler.onDisconnected(); break;
        }
        if (state == STATE.CONNECTED) authenticate();
    }

    // ALWAYS followed by onStateChanged(STATE.SHUT_DOWN). might be StreamClosed
    @Override public void onException(Exception e) {
        if (e instanceof StreamClosed && connection.getState() == STATE.CONNECTING)
            connectionHandler.onAuthenticationFailed();
        else
            connectionHandler.onError(e.getMessage(), e);
    }

    @Override public void onMessage(RelayMessage message) {
        String id = message.getID();
        logger.debug("onMessage(id = {})", id);

        if (ID_VERSION.equals(id)) {
            logger.debug("WeeChat version: {}", ((Info) message.getObjects()[0]).getValue());
            connectionHandler.onAuthenticated();
        }

        RelayObject[] objects = message.getObjects();
        if (objects.length == 0)
            messageHandler.handleMessage(null,id);
        else
            for (RelayObject object : objects)
                messageHandler.handleMessage(object, id);

        // ID_LIST_BUFFERS must get requested after onAuthenticated() (BufferList does that)
        if (ID_LIST_BUFFERS.equals(id)) connectionHandler.onBuffersListed();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// auth

    private void authenticate() {
        sendMessage(null, "init", "password=" + password + ",compression=zlib");
        sendMessage(ID_VERSION, "info", "version");
    }
}
