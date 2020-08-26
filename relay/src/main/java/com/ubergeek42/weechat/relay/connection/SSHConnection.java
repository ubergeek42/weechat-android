package com.ubergeek42.weechat.relay.connection;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ServerHostKeyVerifier;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static com.ubergeek42.weechat.relay.connection.RelayConnection.CONNECTION_TIMEOUT;

public class SSHConnection implements IConnection {
    final private String hostname;
    final private int port;

    final private String sshUsername;
    final private String sshPassword;
    final private char[] sshKey;

    final private Connection connection;
    final private ServerHostKeyVerifier hostKeyVerifier;
    private LocalPortForwarder forwarder;

    public SSHConnection(String hostname, int port, String sshHostname, int sshPort, String sshUsername,
                         String sshPassword, byte[] sshKey, byte[] sshKnownHosts) throws IOException {
        this.hostname = hostname;
        this.port = port;
        this.sshUsername = sshUsername;
        this.sshPassword = sshPassword;
        this.sshKey = sshKey == null ? null : new String(sshKey, StandardCharsets.UTF_8).toCharArray();

        connection = new Connection(sshHostname, sshPort);
        connection.setCompression(true);

        char[] charSshKnownHosts = new String(sshKnownHosts, StandardCharsets.UTF_8).toCharArray();
        KnownHosts knownHosts = new KnownHosts(charSshKnownHosts);
        hostKeyVerifier = new SSHServerKeyVerifier(knownHosts);
    }

    @Override public Streams connect() throws IOException {
        ConnectionInfo connectionInfo = connection.connect(hostKeyVerifier, CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
        boolean useKeyFile = sshKey != null && sshKey.length > 0;

        if (useKeyFile) {
            connection.authenticateWithPublicKey(sshUsername, sshKey, sshPassword);
        } else {
            connection.authenticateWithPassword(sshUsername, sshPassword);
        }

        forwarder = connection.createLocalPortForwarder(3232, hostname, port);
        Socket forwardingSocket = new Socket("127.0.0.1", 3232);
        return new Streams(forwardingSocket.getInputStream(), forwardingSocket.getOutputStream());
    }

    @Override public void disconnect() {
        connection.close();
        if (forwarder != null) forwarder.close();
    }
}
