/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;


public class PlainConnection extends AbstractConnection {

    private String server;
    private int port;

    public PlainConnection(String server, int port) {
        this.server = server;
        this.port = port;
    }

    @Override protected void doConnect() throws IOException {
            //SocketChannel channel = SocketChannel.open();
            //channel.connect(new InetSocketAddress(server, port));
            //Socket sock = channel.socket();
            Socket sock = new Socket(server, port);
            out = sock.getOutputStream();
            in = sock.getInputStream();
    }
}
