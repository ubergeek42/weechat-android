package com.ubergeek42.weechat.relay.connection;


import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.crypto.Base64;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.trilead.ssh2.KnownHosts.HOSTKEY_HAS_CHANGED;
import static com.trilead.ssh2.KnownHosts.HOSTKEY_IS_NEW;
import static com.trilead.ssh2.KnownHosts.HOSTKEY_IS_OK;

public class SSHServerKeyVerifier implements ServerHostKeyVerifier {
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class VerifyException extends IOException {
        final public String hostname;

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
        final public String algorithm;
        final public byte[] key;

        public HostKeyNotVerifiedException(String host, String algorithm, byte[] key) {
            super(host);
            this.algorithm = algorithm;
            this.key = key;
        }

        public static String makeSha2Fingerprint(byte[] bytes) throws NoSuchAlgorithmException {
            byte[] sha256 = MessageDigest.getInstance("SHA256").digest(bytes);
            return new String(Base64.encode(sha256));
        }

        @Override public String getMessage() {
            String fingerprint;
            try {
                fingerprint = makeSha2Fingerprint(key);
            } catch (Exception e) {
                e.printStackTrace();
                fingerprint = "n/a";
            }

            return "Hostname " + hostname + " is known, but could not be verified. " +
                    "Chosen algorithm: " + algorithm + "; SHA256 host key fingerprint: " + fingerprint;
        }
    }
}
