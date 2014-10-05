package com.ubergeek42.weechat.relay.connection;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;

public class PlainConnection extends AbstractConnection {

    public PlainConnection(String server, int port) {
        this.server = server;
        this.port = port;

        this.connector = plainConnector;

    }

    private Thread plainConnector = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.connect(new InetSocketAddress(server, port));
                sock = channel.socket();
                tcpSock = sock;
                out_stream = sock.getOutputStream();
                in_stream = sock.getInputStream();
                connected = true;
                notifyHandlers(STATE.CONNECTED);
            } catch (ClosedByInterruptException e) {
                // Thread interrupted during connect.
            } catch (Exception e) {
                // TODO: better error handling
                e.printStackTrace();
                notifyHandlersOfError(e);
            }
        }
    });
}
