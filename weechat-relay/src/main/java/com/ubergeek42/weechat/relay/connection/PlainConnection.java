/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import java.io.IOException;
import java.net.Socket;


public class PlainConnection extends AbstractConnection {

    private String server;
    private int port;

    public PlainConnection(String server, int port) {
        this.server = server;
        this.port = port;
    }

    @Override protected void doConnect() throws IOException {
        SocketChannelFactory f = new SocketChannelFactory();
        Socket socket = f.createSocket(server, port);
        in = f.getInputStream(socket);
        out = f.getOutputStream(socket);
    }
}
