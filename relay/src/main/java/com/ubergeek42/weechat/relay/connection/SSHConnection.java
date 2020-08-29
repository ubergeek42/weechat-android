package com.ubergeek42.weechat.relay.connection;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.crypto.PEMDecoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import static com.ubergeek42.weechat.relay.connection.RelayConnection.CONNECTION_TIMEOUT;

public class SSHConnection implements IConnection {
    final public static String KEYSTORE_ALIAS = "ssh-connection-key-0";
    final public static byte[] STORED_IN_KEYSTORE_MARKER = new byte[]{13, 37};

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
                         byte[] serializedSshKey,
                         byte[] sshKnownHosts) throws Exception {
        this.hostname = hostname;
        this.port = port;
        this.sshUsername = sshUsername;

        this.authenticationMethod = authenticationMethod;
        if (authenticationMethod == AuthenticationMethod.KEY) {
            keyPair = serializedSshKey == STORED_IN_KEYSTORE_MARKER ?
                    getKeyPairFromKeyStore() : getKeyPair(serializedSshKey);
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

    public static KeyPair makeKeyPair(byte[] sshKey, String sshPassword) throws IOException {
        char[] charKey = new String(sshKey, StandardCharsets.ISO_8859_1).toCharArray();
        return PEMDecoder.decode(charKey, sshPassword);
    }

    public static KeyPair getKeyPair(byte[] serializedSshKey) throws IOException, ClassNotFoundException {
        return (KeyPair) deserialize(serializedSshKey);
    }

    public static KeyPair getKeyPairFromKeyStore() throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        PrivateKey privateKey = (PrivateKey) ks.getKey(KEYSTORE_ALIAS, null);
        PublicKey publicKey = ks.getCertificate(KEYSTORE_ALIAS).getPublicKey();
        return new KeyPair(publicKey, privateKey);
    }

    public static KnownHosts getKnownHosts(byte[] knownHosts) throws IOException {
        char[] charKnownHosts = new String(knownHosts, StandardCharsets.ISO_8859_1).toCharArray();
        return new KnownHosts(charKnownHosts);
    }

    // the above method is not importable from the app
    public static void validateKnownHosts(byte[] knownHosts) throws IOException {
        getKnownHosts(knownHosts);
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }
}
