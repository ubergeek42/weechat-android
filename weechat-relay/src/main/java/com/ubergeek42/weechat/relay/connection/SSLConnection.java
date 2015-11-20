/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;


public class SSLConnection extends AbstractConnection {

    private String server;
    private int port;
    SSLContext sslContext;
    private Socket sock;

    public SSLConnection(String server, int port, SSLContext sslContext) {
        this.server = server;
        this.port = port;
        this.sslContext = sslContext;
    }

    @Override protected void doConnect() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(server, port));
        sock = sslContext.getSocketFactory().createSocket(channel.socket(), server, port, true);
        out = sock.getOutputStream();
        in = sock.getInputStream();
    }

    @Override protected void doDisconnect() {
        super.doDisconnect();
        try {sock.close();} catch (IOException | NullPointerException ignored) {}
    }
}
