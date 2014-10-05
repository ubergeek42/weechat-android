package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import java.io.IOException;
import java.net.Socket;

public interface IConnection {
    void addConnectionHandler(RelayConnectionHandler relayConnection);

    enum STATE {
        CONNECTING,
        CONNECTED,
        AUTHENTICATED,
        DISCONNECTED,
    }

    public void connect();

    public void disconnect();

    public boolean isConnected();

    /* Exposed for additional configuration (keepalive), may be null */
    public Socket getTCPSocket();

    int read(byte[] bytes, int off, int len) throws IOException;

    void write(byte[] bytes);

    void notifyHandlersOfError(Exception e);

    void notifyHandlers(STATE s);
}
