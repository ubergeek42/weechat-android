package com.ubergeek42.weechat.relay.connection;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.crypto.PEMDecoder;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static com.ubergeek42.weechat.relay.connection.RelayConnection.CONNECTION_TIMEOUT;

public class SSHConnection implements IConnection {
    public enum AuthenticationMethod {
        PASSWORD, KEY
    }

    final private String hostname;
    final private int port;

    final private String sshUsername;
    final private String sshPassword;
    private final AuthenticationMethod authenticationMethod;

    final private KeyPair keyPair;

    final private Connection connection;
    final private ServerHostKeyVerifier hostKeyVerifier;
    private LocalPortForwarder forwarder;

    public SSHConnection(String hostname, int port,
                         String sshHostname, int sshPort, String sshUsername,
                         AuthenticationMethod authenticationMethod,
                         String sshPassword,
                         byte[] sshKey, String sshPassphrase,
                         byte[] sshKnownHosts) throws IOException {
        this.hostname = hostname;
        this.port = port;
        this.sshUsername = sshUsername;

        this.authenticationMethod = authenticationMethod;
        if (authenticationMethod == AuthenticationMethod.KEY) {
            keyPair = getKeyPair(sshKey, sshPassphrase);
            this.sshPassword = null;
        } else {
            keyPair = null;
            this.sshPassword = sshPassword;
        }

        connection = new Connection(sshHostname, sshPort);
        connection.setCompression(true);
        //connection.enableDebugging(true, null);

        hostKeyVerifier = new SSHServerKeyVerifier(getKnownHosts(sshKnownHosts));
    }

    @Override public Streams connect() throws IOException {
        ConnectionInfo connectionInfo = connection.connect(hostKeyVerifier,
                CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);

        if (authenticationMethod == AuthenticationMethod.KEY) {
            if (!connection.authenticateWithPublicKey(sshUsername, keyPair))
                throw new IOException("Failed to authenticate with public key");
        } else {
            if (!connection.authenticateWithPassword(sshUsername, sshPassword))
                throw new IOException("Failed to authenticate with password");
        }

        int localPort = Utils.findAvailablePort();
        forwarder = connection.createLocalPortForwarder(localPort, hostname, port);
        Socket forwardingSocket = new Socket("127.0.0.1", localPort);
        return new Streams(forwardingSocket.getInputStream(), forwardingSocket.getOutputStream());
    }

    @Override public void disconnect() {
        connection.close();
        if (forwarder != null) forwarder.close();
    }

    public static KeyPair getKeyPair(byte[] sshKey, String sshPassword) throws IOException {
        char[] charKey = new String(sshKey, StandardCharsets.ISO_8859_1).toCharArray();
        return PEMDecoder.decode(charKey, sshPassword);
    }

    public static KnownHosts getKnownHosts(byte[] knownHosts) throws IOException {
        char[] charKnownHosts = new String(knownHosts, StandardCharsets.ISO_8859_1).toCharArray();
        return new KnownHosts(charKnownHosts);
    }

    // the above method is not importable from the app
    public static void validateKnownHosts(byte[] knownHosts) throws IOException {
        getKnownHosts(knownHosts);
    }
}
