package com.ubergeek42.weechat.relay.connection;


import com.trilead.ssh2.ExtendedServerHostKeyVerifier;
import com.trilead.ssh2.KnownHosts;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.trilead.ssh2.KnownHosts.HOSTKEY_HAS_CHANGED;
import static com.trilead.ssh2.KnownHosts.HOSTKEY_IS_NEW;
import static com.trilead.ssh2.KnownHosts.HOSTKEY_IS_OK;
import static com.trilead.ssh2.KnownHosts.createHexFingerprint;

public class SSHServerKeyVerifier extends ExtendedServerHostKeyVerifier {
    final KnownHosts knownHosts;

    public SSHServerKeyVerifier(KnownHosts knownHosts) {
        this.knownHosts = knownHosts;
    }

    @Override public boolean verifyServerHostKey(String hostname, int port,
            String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
        switch (knownHosts.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey)) {
            case HOSTKEY_IS_OK: return true;
            case HOSTKEY_IS_NEW: throw new UnknownHostKeyException(hostname);
            case HOSTKEY_HAS_CHANGED: throw new HostKeyNotVerifiedException(hostname,
                    serverHostKeyAlgorithm, serverHostKey);
        }
        throw new RuntimeException("this should not happen");
    }

    @Override public List<String> getKnownKeyAlgorithmsForHost(String hostname, int port) {
        String[] algorithms = knownHosts.getPreferredServerHostkeyAlgorithmOrder(hostname);
        return algorithms == null ? null : Arrays.asList(algorithms);
    }

    @Override public void removeServerHostKey(String hostname, int port,
                                              String serverHostKeyAlgorithm, byte[] serverHostKey) {
        // not implemented
    }

    @Override public void addServerHostKey(String hostname, int port,
                                           String keyAlgorithm, byte[] serverHostKey) {
        // not implemented
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class VerifyException extends IOException {
        final String hostname;

        public VerifyException(String hostname) {
            this.hostname = hostname;
        }
    }

    public static class UnknownHostKeyException extends VerifyException {
        public UnknownHostKeyException(String host) {
            super(host);
        }

        @Override public String getMessage() {
            return "Hostname " + hostname + " is not present in known hosts";
        }
    }

    public static class HostKeyNotVerifiedException extends VerifyException {
        final String algorithm;
        final byte[] key;

        public HostKeyNotVerifiedException(String host, String algorithm, byte[] key) {
            super(host);
            this.algorithm = algorithm;
            this.key = key;
        }

        @Override public String getMessage() {
            String fingerprint;
            try {
                fingerprint = createHexFingerprint(algorithm, key);
            } catch (Exception e) {
                e.printStackTrace();
                fingerprint = "n/a";
            }

            return "Hostname " + hostname + " is known, but could not be verified. " +
                    "Chosen algorithm: " + algorithm + "; MD5 host key fingerprint: " + fingerprint;
        }
    }
}
