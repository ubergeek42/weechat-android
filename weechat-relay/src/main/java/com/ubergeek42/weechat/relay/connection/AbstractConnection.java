package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

public abstract class AbstractConnection implements IConnection {
    final private static boolean DEBUG = false;
    private static Logger logger = LoggerFactory.getLogger("AbstractConnection");

    String server = null;
    int port = 0;

    Socket sock = null;
    Socket tcpSock = null;
    OutputStream out_stream = null;
    InputStream in_stream = null;
    volatile boolean connected = false;

    ArrayList<RelayConnectionHandler> connectionHandlers = new ArrayList<RelayConnectionHandler>();
    Thread connector = null;

    @Override
    public boolean isConnected() {
        Socket s = sock;
        return (s != null && !s.isClosed() && connected);
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        InputStream in = in_stream;
        if (in == null)
            return -1;
        return in.read(bytes, off, len);
    }

    @Override
    public void write(byte[] bytes) {
        OutputStream out = out_stream;
        if (out == null)
            return;
        try {
            out.write(bytes);
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
        // If we're in the process of connecting, kill the thread and let us die
        if (connector.isAlive()) {
            connector.interrupt();
        }

        if (!connected) {
            return;
        }

            // If we're connected, tell weechat we're going away
        try {
            out_stream.write("quit\n".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close all of our streams/sockets
        connected = false;
        if (in_stream != null) {
            try {
                in_stream.close();
            } catch (IOException e) {}
            in_stream = null;
        }
        if (out_stream != null) {
            try {
                out_stream.close();
            } catch (IOException e) {}
            out_stream = null;
        }
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {}
            sock = null;
        }
        tcpSock = null;

        // Call any registered disconnect handlers
        notifyHandlers(STATE.DISCONNECTED);
    }

    @Override
    public Socket getTCPSocket() {
        return tcpSock;
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
            }
        }
    }

    @Override
    public void notifyHandlersOfError(Exception e) {
        for (RelayConnectionHandler rch : connectionHandlers) {
            rch.onError(e.getMessage(), e);
        }
    }
}
