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
import com.ubergeek42.weechat.relay.protocol.Data;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * Class to provide and manage a connection to a weechat relay server
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 */
public class RelayConnection {


    private String password = null;
    private String server = null;
    private int port;

    private Socket sock = null;
    private OutputStream outstream = null;
    private InputStream instream = null;

    private HashMap<String, HashSet<RelayMessageHandler>> messageHandlers = new HashMap<String, HashSet<RelayMessageHandler>>();
    private ArrayList<RelayConnectionHandler> connectionHandlers = new ArrayList<RelayConnectionHandler>();

    private boolean connected = false;
    private Thread currentConnection;



    /**
     * Sets whether to attempt reconnecting if disconnected
     *
     * @param autoreconnect
     *            - Whether to autoreconnect or not
     */
    public void setAutoReconnect(boolean autoreconnect) {
    }

    /**
     * @return The server we are connected to
     */
    public String getServer() {
        return server;
    }

    /**
     * Connects to the server. On success isConnected() will return true. On failure, prints a stack
     * trace... TODO: proper error handling(should throw an exception)
     */
    public void connect() {
        if (isConnected() || socketReader.isAlive()) {
            return;
        }

        currentConnection.start();
    }

    /**
     * @return Whether we have a connection to the server
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sets up a socket connection to a weechat relay server
     * 
     * @param server
     *            - server to connect to(ip or hostname)
     * @param port
     *            - port to connect on
     * @param password
     *            - password for the relay server
     */
    public RelayConnection(String server, int port, String password) {
        this.server = server;
        this.port = port;
        this.password = password;

        currentConnection =  new SocketConnectionThread();
    }


    /**
     * Sets up a SSL socket connection to a weechat relay server
     *
     * @param sslKeystore
     *            - KeyStore to use in verifying server
     * @param server
     *            - server to connect to(ip or hostname)
     * @param port
     *            - port to connect on
     * @param password
     *            - password for the relay server
     */
    public RelayConnection(KeyStore sslKeystore, String server, int port, String password) {
        this.server = server;
        this.port = port;
        this.password = password;
        //this.sslKeyStore = sslKeystore;
        currentConnection = new SocketConnectionThread(sslKeystore);
    }


    /**
     * Disconnects from the server, and cleans up
     */
    public void disconnect() {
        if (!connected) {
            return;
        }
        try {
            if (currentConnection.isAlive()) {
                currentConnection.interrupt();
            }

            if (connected) {
                outstream.write("quit\n".getBytes());
            }

            connected = false;
            if (instream != null) {
                instream.close();
                instream = null;
            }
            if (outstream != null) {
                outstream.close();
                outstream = null;
            }
            if (sock != null) {
                sock.close();
                sock = null;
            }

            if (socketReader.isAlive()) {
                // TODO: kill the thread if necessary
            }

            // Call any registered disconnect handlers
            for (RelayConnectionHandler wrch : connectionHandlers) {
                wrch.onDisconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads data from the socket, breaks it into messages, and dispatches the handlers
     */
    private Thread socketReader = new Thread(new Runnable() {
        @Override
        public void run() {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            while (sock != null && !sock.isClosed()) {
                byte b[] = new byte[1024];
                try {
                    int r = instream.read(b);
                    if (r > 0) {
                        buffer.write(b, 0, r);
                    } else if (r == -1) { // Stream closed
                        break;
                    }

                    while (buffer.size() >= 4) {
                        // Calculate length

                        // TODO: wasteful...
                        int length = new Data(buffer.toByteArray()).getUnsignedInt();

                        // Still have more message to read
                        if (buffer.size() < length) {
                            break;
                        }

                        // logger.trace("socketReader got message, size: " + length);
                        // We have a full message, so let's do something with it
                        byte[] bdata = buffer.toByteArray();
                        byte[] msgdata = Helper.copyOfRange(bdata, 0, length);
                        byte[] remainder = Helper.copyOfRange(bdata, length, bdata.length);
                        RelayMessage wm = new RelayMessage(msgdata);

                        System.currentTimeMillis();
                        handleMessage(wm);
                        // logger.trace("handleMessage took " + (System.currentTimeMillis()-start) +
                        // "ms(id: "+wm.getID()+")");

                        // Reset the buffer, and put back any additional data
                        buffer.reset();
                        buffer.write(remainder);
                    }

                } catch (IOException e) {
                    if (sock != null && !sock.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
            connected = false;
            sock = null;
            instream = null;
            outstream = null;
            // Call any registered disconnect handlers
            for (RelayConnectionHandler wrch : connectionHandlers) {
                wrch.onDisconnect();
            }
        }
    });

    /******************************************************************************************
     ********** Handler METHODS*************************************************************
     ****************************************************************************************/

    /**
     * Registers a handler to be called whenever a message is received
     * 
     * @param id
     *            - The string ID to handle(e.g. "_nicklist" or "_buffer_opened")
     * @param wmh
     *            - The object to receive the callback
     */
    public void addHandler(String id, RelayMessageHandler wmh) {
        HashSet<RelayMessageHandler> currentHandlers = messageHandlers.get(id);
        if (currentHandlers == null) {
            currentHandlers = new HashSet<RelayMessageHandler>();
        }
        currentHandlers.add(wmh);
        messageHandlers.put(id, currentHandlers);
    }


    /**
     * Register a connection handler to receive onConnected/onDisconnected events
     *
     * @param wrch - The connection handler
     */
    public void setConnectionHandler(RelayConnectionHandler wrch) {
        connectionHandlers.add(wrch);
    }

    /******************************************************************************************
     ********** MESSAGING METHODS*************************************************************
     ****************************************************************************************/



    /**
     * Sends the specified message to the server
     *
     * @param msg - The message to send
     */
    public void sendMsg(String msg) {
        if (!connected) {
            return;
        }
        msg = msg + "\n";

        final String message = msg;
        Runnable sender = new Runnable() {
            @Override
            public void run() {
                try {
                    outstream.write(message.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(sender).start();
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
     * Signal any observers whenever we receive a message
     *
     * @param msg
     *            - Message we received
     */
    private void handleMessage(RelayMessage msg) {
        String id = msg.getID();
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
        }
    }




    private class SocketConnectionThread extends Thread{
        /** SSL Settings */
        private SSLContext sslContext =null;
        /*
            Constructor for ssl socket
         */
        SocketConnectionThread(KeyStore sslKeyStore) {
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(sslKeyStore);
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());

            } catch (Exception e) {
                e.printStackTrace();
                for (RelayConnectionHandler wrch : connectionHandlers) {
                    wrch.onError(e.getMessage(), e);
                }

            }
        }
        /*
            Constructor for regular socket
         */
        SocketConnectionThread() {}

        public void run() {
            try {
                if(sslContext==null) {
                    sock = new Socket(server, port);
                } else {
                    SSLSocket sslSock = (SSLSocket) sslContext.getSocketFactory().createSocket(server, port);
                    sslSock.setKeepAlive(true);
                    sock = sslSock;
                }
                outstream = sock.getOutputStream();
                instream = sock.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            /*
                Does post connection setup(Sends initial commands/etc)
             */
            connected = true;
            sendMsg(null, "init", "password=" + password + ",compression=zlib");

            socketReader.start();

            // Call any registered connection handlers
            for (RelayConnectionHandler wrch : connectionHandlers) {
                wrch.onConnect();
            }
        }
    }
}
