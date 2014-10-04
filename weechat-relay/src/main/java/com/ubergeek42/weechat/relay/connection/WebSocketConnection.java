package com.ubergeek42.weechat.relay.connection;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class WebSocketConnection extends AbstractConnection {
    private WebSocketClient wsclient;
    private URI uri;

    private PipedInputStream in_stream;
    private PipedOutputStream weechatIn;
    /** SSL Settings */
    private KeyStore sslKeyStore;
    private boolean useSSL;

    public WebSocketConnection(String server, int port, boolean useSSL) {
        this.server = server;
        this.port = port;
        this.useSSL = useSSL;
        try {
            if (useSSL) {
                this.uri = new URI("wss://" + this.server + ":" + this.port+"/weechat");
            } else {
                this.uri = new URI("ws://" + this.server + ":" + this.port+"/weechat");
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        System.out.println(this.uri);
        this.connector = wsConnector;

        in_stream = new PipedInputStream();
        weechatIn = new PipedOutputStream();
        try {
            weechatIn.connect(in_stream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        wsclient = new WebSocketClient(uri, new Draft_17()) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                connected = true;
                System.out.println(this.getReadyState());

                notifyHandlers(STATE.CONNECTED);
            }

            @Override
            public void onMessage(String message) {
                throw new RuntimeException("Unexpected string message from websocket");
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                byte[] a = bytes.array();
                try {
                    weechatIn.write(a);
                    weechatIn.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                connected = false;
            }

            @Override
            public void onError(Exception ex) {
                System.out.println("Error");
                System.out.println(ex);
                connected = false;
                // TODO: print error exception here
                notifyHandlers(STATE.ERROR);
            }
        };
    }
    public void setSSLKeystore(KeyStore ks) {
        sslKeyStore = ks;
    }

    private Thread wsConnector = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                if (useSSL) {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(sslKeyStore);

                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
                    wsclient.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(sslContext));
                }

                wsclient.connectBlocking();
                System.out.println("Connection finished");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            }
        }
    });

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        PipedInputStream in = in_stream;
        if (in == null)
            return -1;
        return in.read(bytes, off, len);
    }

    @Override
    public void write(byte[] bytes) {
        wsclient.send(bytes);
    }

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
            write("quit\n".getBytes());

            // Close all of our streams/sockets
            connected = false;
            wsclient.close();
            if (in_stream != null) {
                in_stream.close();
                in_stream = null;
            }
            if (weechatIn != null) {
                weechatIn.close();
                weechatIn = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Call any registered disconnect handlers
        notifyHandlers(STATE.DISCONNECTED);
    }
}
