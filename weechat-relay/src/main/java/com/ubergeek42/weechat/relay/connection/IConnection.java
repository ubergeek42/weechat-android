package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import java.io.IOException;

/**
 * Created by ubergeek on 4/18/14.
 */
public interface IConnection {
    void addConnectionHandler(RelayConnectionHandler relayConnection);

    enum STATE {
        CONNECTING,
        CONNECTED,
        AUTHENTICATED,
        DISCONNECTED,
        ERROR,
    }
    public void connect();
    public void disconnect();
    public boolean isConnected();

    int read(byte[] bytes, int off, int len) throws IOException;
    void write(byte[] bytes);

    void notifyHandlers(STATE s);
}
