package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

public abstract class AbstractConnection implements IConnection {
    String server = null;
    int port = 0;

    Socket sock = null;
    OutputStream out_stream = null;
    InputStream in_stream = null;
    boolean connected = false;
    Object errordata = null;

    ArrayList<RelayConnectionHandler> connectionHandlers = new ArrayList<RelayConnectionHandler>();
    Thread connector = null;

    @Override
    public boolean isConnected() {
        return (sock!= null && !sock.isClosed() && connected);
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return in_stream.read(bytes);
    }

    @Override
    public void write(byte[] bytes) {
        try {
            out_stream.write(bytes);
        } catch (IOException e) {
            // TODO: better this part
            e.printStackTrace();
        }
    }

    @Override
    public void connect() {
        notifyHandlers(STATE.CONNECTING);

        if (connector.isAlive()) {
            // do nothing
            return;
        }
        connector.start();
    }

    /**
     * Disconnects from the server, and cleans up
     */
    @Override
    public void disconnect() {
        if (!connected) {
            return;
        }
        try {
            // If we're in the process of connecting, kill the thread and let us die
            if (connector.isAlive()) {
                connector.interrupt();
                //connector.stop(); // FIXME: deprecated, should probably find a better way to do this
            }

            // If we're connected, tell weechat we're going away
            if (connected) {
                out_stream.write("quit\n".getBytes());
            }

            // Close all of our streams/sockets
            connected = false;
            if (in_stream != null) {
                in_stream.close();
                in_stream = null;
            }
            if (out_stream != null) {
                out_stream.close();
                out_stream = null;
            }
            if (sock != null) {
                sock.close();
                sock = null;
            }

            // Call any registered disconnect handlers
            notifyHandlers(STATE.DISCONNECTED);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Register a connection handler to receive onConnected/onDisconnected events
     *
     * @param rch - The connection handler
     */
    @Override
    public void addConnectionHandler(RelayConnectionHandler rch) {
        connectionHandlers.add(rch);
    }

    @Override
    public void notifyHandlers(IConnection.STATE s) {
        for (RelayConnectionHandler rch: connectionHandlers) {
            switch (s) {
                case CONNECTING:
                    rch.onConnecting();
                    break;
                case CONNECTED:
                    rch.onConnect();
                    break;
                case AUTHENTICATED:
                    rch.onAuthenticated();
                    break;
                case DISCONNECTED:
                    rch.onDisconnect();
                    break;
                case ERROR:
                    rch.onError("unknown error", errordata);
                    break;
            }
        }
    }
}
