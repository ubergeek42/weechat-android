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

import com.ubergeek42.weechat.Helper;
import com.ubergeek42.weechat.relay.connection.IConnection;
import com.ubergeek42.weechat.relay.messagehandler.LoginHandler;
import com.ubergeek42.weechat.relay.protocol.Data;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class to provide and manage a connection to a weechat relay server
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayConnection implements RelayConnectionHandler {
    final private static boolean DEBUG = false;
    private static Logger logger = LoggerFactory.getLogger("RelayConnection");
    private String password;

    private HashMap<String, LinkedHashSet<RelayMessageHandler>> messageHandlers = new  HashMap<String, LinkedHashSet<RelayMessageHandler>>();

    IConnection conn;
    LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<String>();

    /**
     * Sets up a connection to a weechat relay server
     * 
     * @param conn
     *            - An IConnection object to actually make the connection
     */
    public RelayConnection(IConnection conn, String password) {
        this.conn = conn;
        this.password = password;

        // Used to determine if we are actually logged in(weechat gives no indication otherwise)
        // TODO: hookup some timeout on this to say the connection failed if we receive no reply
        //       after some set amount of time
        LoginHandler loginHandler = new LoginHandler(conn);
        addHandler("checklogin", loginHandler);

        conn.addConnectionHandler(this);
    }

    public String getServer() {
        return "Weechat Server(Placeholder)";
    }

    /* Exposed for additional configuration (keepalive), may be null */
    public Socket getTCPSocket() {
        return conn.getTCPSocket();
    }

    public boolean isConnected() {
        return conn.isConnected();
    }

    /**
     * Connects to the server. On success isConnected() will return true.
     */
    public void connect() {
        if (conn.isConnected() || socketReader.isAlive()) {
            return;
        }
        conn.connect();
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        conn.disconnect();
        socketReader.interrupt();
        socketWriter.interrupt();
    }


    /**
     * Sends a message to the server
     *
     * @param id - id of the message
     * @param command - command to send
     * @param arguments - arguments for the command
     */
    public void sendMsg(String id, String command, String arguments) {
        String msg;
        if (id == null) {
            msg = String.format("%s %s", command, arguments);
        } else {
            msg = String.format("(%s) %s %s", id, command, arguments);
        }
        sendMsg(msg);
    }
    /**
     * Sends the specified message to the server(a newline will be added to the end)
     * 
     * @param msg - The message to send
     */
    public void sendMsg(String msg) {
        if (!conn.isConnected()) {
            return;
        }
        msg = msg + "\n";

        outbox.add(msg);
    }


    /**
     * Does post connection setup(Sends initial commands/etc)
     */
    private void postConnectionSetup() {
        sendMsg(null, "init", "password=" + password + ",compression=zlib");
        sendMsg("checklogin", "info", "version");

        socketReader.start();
        socketWriter.start();
    }

    private Thread socketWriter = new Thread(new Runnable() {
        @Override
        public void run() {
            while(conn.isConnected()) {
                try {
                    String msg = outbox.take();
                    conn.write(msg.getBytes());
                } catch (InterruptedException e) {
                    // Ensure that conn is disconnected before interrupting the thread
                }
            }
        }
    });

    /**
     * Reads data from the socket, breaks it into messages, and dispatches the handlers
     */
    private Thread socketReader = new Thread(new Runnable() {
        @Override
        public void run() {
            byte header[] = new byte[4];
            int header_pos = 0;
            byte data[] = null;
            int data_pos = 0;
            while (conn.isConnected()) {
                try {
                    if (data == null) {
                        int r = conn.read(header, header_pos, header.length - header_pos);
                        if (r > 0) {
                            header_pos += r;
                        } else if (r == -1) { // Stream closed
                            break;
                        }

                        if (header_pos == header.length) {
                            data = Helper.copyOfRange(header, 0, new Data(header).getUnsignedInt());
                            data_pos = header.length;
                            if (data_pos > data.length)
                                 data = null;
                            header_pos = 0;
                        } else {
                            continue;
                        }
                    }

                    int r = conn.read(data, data_pos, data.length - data_pos);
                    if (r > 0) {
                        data_pos += r;
                    } else if (r == -1) { // Stream closed
                        break;
                    }

                    if (data_pos == data.length) {
                        // logger.trace("socketReader got message, size: " + data.length);
                        RelayMessage wm = new RelayMessage(data);

                        handleMessage(wm);
                        // logger.trace("handleMessage took " + (System.currentTimeMillis()-start) +
                        // "ms(id: "+wm.getID()+")");

                        data = null;
                    }

                } catch (IOException e) {
                    if (conn.isConnected()) {
                        // We were still connected when it died!
                        e.printStackTrace();
                    } else {
                        // Socket closed..no big deal
                        e.printStackTrace();
                    }
                }
            }
            if (DEBUG) logger.debug("socketReader: disconnected, thread stopping");
            conn.disconnect();
        }
    });

    /**
     * Registers a handler to be called whenever a message is received
     * 
     * @param id
     *            - The string ID to handle(e.g. "_nicklist" or "_buffer_opened")
     * @param wmh
     *            - The object to receive the callback
     */
    public void addHandler(String id, RelayMessageHandler wmh) {
        LinkedHashSet<RelayMessageHandler> currentHandlers = messageHandlers.get(id);
        if (currentHandlers == null) {
            currentHandlers = new LinkedHashSet<RelayMessageHandler>();
        }
        currentHandlers.add(wmh);
        messageHandlers.put(id, currentHandlers);
    }

    /**
     * Signal any observers whenever we receive a message
     * 
     * @param msg
     *            - Message we received
     */
    private void handleMessage(RelayMessage msg) {
        String id = msg.getID();
        if (DEBUG) logger.debug("handling message {}", id);
        if (messageHandlers.containsKey(id)) {
            HashSet<RelayMessageHandler> handlers = messageHandlers.get(id);
            for (RelayMessageHandler rmh : handlers) {
                if (msg.getObjects().length == 0) {
                    rmh.handleMessage(null, id);
                } else {
                    for (RelayObject obj : msg.getObjects()) {
                        rmh.handleMessage(obj, id);
                    }
                }
            }
        } else {
            if (DEBUG) logger.debug("Unhandled message: {}", id);
        }
    }


    @Override
    public void onConnecting() {
        if (DEBUG) logger.debug("RelayConnection.onConnecting");
    }

    @Override
    public void onConnect() {
        if (DEBUG) logger.debug("onConnect()");
        postConnectionSetup();
    }

    @Override
    public void onAuthenticated() {
        if (DEBUG) logger.debug("onAuthenticated()");
    }

    @Override
    public void onBuffersListed() {}

    @Override
    public void onDisconnect() {
        if (DEBUG) logger.debug("onDisconnect()");

        socketReader.interrupt();
        socketWriter.interrupt();
    }

    @Override
    public void onError(String err, Object extraInfo) {
        if (DEBUG) logger.debug("onError()");
    }
}
