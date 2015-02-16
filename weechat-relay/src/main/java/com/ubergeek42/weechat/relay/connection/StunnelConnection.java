package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by ubergeek on 4/18/14.
 */
public class StunnelConnection extends AbstractConnection {
    /** Stunnel Settings */
    private String stunnelCert;
    private String stunnnelKeyPass;

    public StunnelConnection(String server, int port) {
        this.server = server;
        this.port = port;
        this.connector = stunnelConnector;
    }
    /**
     * Set the certificate to use when connecting to stunnel
     *
     * @param path
     *            - Path to the certificate
     */
    public void setStunnelCert(String path) {
        stunnelCert = path;
    }

    /**
     * Password to open the stunnel key
     *
     * @param pass
     */
    public void setStunnelKey(String pass) {
        stunnnelKeyPass = pass;
    }

    /**
     * Connects to the server(via stunnel) in a new thread, so we can interrupt it if we want to
     * cancel the connection
     */

    private Thread stunnelConnector = new Thread(new Runnable() {
        @Override
        public void run() {
            SSLContext context = null;
            KeyStore keyStore = null;
            TrustManagerFactory tmf = null;
            KeyStore keyStoreCA = null;
            KeyManagerFactory kmf = null;
            try {

                FileInputStream pkcs12in = new FileInputStream(new File(stunnelCert));

                context = SSLContext.getInstance("TLS");

                // Local client certificate and key and server certificate
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(pkcs12in, stunnnelKeyPass.toCharArray());

                // Build a TrustManager, that trusts only the server certificate
                keyStoreCA = KeyStore.getInstance("BKS");
                keyStoreCA.load(null, null);
                keyStoreCA.setCertificateEntry("Server", keyStore.getCertificate("Server"));
                tmf = TrustManagerFactory.getInstance("X509");
                tmf.init(keyStoreCA);

                // Build a KeyManager for Client auth
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, null);
                context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            } catch (Exception e) {
                e.printStackTrace();
                notifyHandlersOfError(e);
                return;
            }

            SSLSocketFactory socketFactory = context.getSocketFactory();
            try {
                SocketChannel channel = SocketChannel.open();
                channel.connect(new InetSocketAddress(server, port));
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

    //TODO: override disconnect/other methods to make sure the tunnel is closed/cleaned up nicely
}
