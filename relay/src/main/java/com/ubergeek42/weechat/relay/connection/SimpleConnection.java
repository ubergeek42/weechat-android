package com.ubergeek42.weechat.relay.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

public class SimpleConnection implements IConnection {

    final private String hostname;
    final private int port;
    final private HostnameVerifier verifier;

    final private Socket socket;

    public SimpleConnection(String hostname, int port, SSLSocketFactory sslSocketFactory,
                            HostnameVerifier verifier) throws IOException {
        this.hostname = hostname;
        this.port = port;
        this.verifier = verifier;
        socket = sslSocketFactory == null ? new Socket() : sslSocketFactory.createSocket();
    }

    @Override public Streams connect() throws IOException {
        socket.connect(new InetSocketAddress(hostname, port));
        Utils.verifyHostname(verifier, socket, hostname);
        return new Streams(socket.getInputStream(), socket.getOutputStream());
    }

    @Override public void disconnect() throws IOException {
        socket.close();
    }
}
