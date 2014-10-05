package com.ubergeek42.weechat.relay.connection;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class SSLConnection extends AbstractConnection {
    /** SSL Settings */
    private KeyStore sslKeyStore;

    public SSLConnection(String server, int port) {
        this.server = server;
        this.port = port;
        this.connector = sslConnector;

    }

    public void setSSLKeystore(KeyStore ks) {
        sslKeyStore = ks;
    }
    /**
     * Connects to the server(Via SSL) in a new thread, so we can interrupt it if we want to cancel the
     * connection
     */

    private Thread sslConnector = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(sslKeyStore);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
                SocketChannel channel = SocketChannel.open();
                channel.connect(new InetSocketAddress(server, port));
                SSLSocketFactory socketFactory = sslContext.getSocketFactory();
                sock = socketFactory.createSocket(channel.socket(), server, port, true);
                tcpSock = channel.socket();

                out_stream = sock.getOutputStream();
                in_stream = sock.getInputStream();
                connected = true;
                notifyHandlers(STATE.CONNECTED);
            } catch (ClosedByInterruptException e) {
                // Thread interrupted during connect.
            } catch (Exception e) {
                e.printStackTrace();
                notifyHandlersOfError(e);
            }
        }
    });
}
