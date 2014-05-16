package com.ubergeek42.weechat.relay.connection;

import java.net.Socket;

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
                sock = new Socket(server, port);
                out_stream = sock.getOutputStream();
                in_stream = sock.getInputStream();
                connected = true;
                notifyHandlers(STATE.CONNECTED);
            } catch (Exception e) {
                // TODO: better error handling
                e.printStackTrace();
                notifyHandlers(STATE.ERROR);
            }
        }
    });
}
