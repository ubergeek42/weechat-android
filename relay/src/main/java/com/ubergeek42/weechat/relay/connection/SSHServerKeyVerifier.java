package com.ubergeek42.weechat.relay.connection;


import com.trilead.ssh2.ExtendedServerHostKeyVerifier;
import com.trilead.ssh2.KnownHosts;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.trilead.ssh2.KnownHosts.HOSTKEY_HAS_CHANGED;
import static com.trilead.ssh2.KnownHosts.HOSTKEY_IS_NEW;
import static com.trilead.ssh2.KnownHosts.HOSTKEY_IS_OK;

class SSHServerKeyVerifier extends ExtendedServerHostKeyVerifier {
    final KnownHosts knownHosts;

    public SSHServerKeyVerifier(KnownHosts knownHosts) {
        this.knownHosts = knownHosts;
    }

    @Override public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
        switch (knownHosts.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey)) {
            case HOSTKEY_IS_OK: return true;
            case HOSTKEY_IS_NEW: throw new IOException("No entries found for this hostname and this type of host key");
            case HOSTKEY_HAS_CHANGED: throw new IOException("Hostname is known, but with another key of the same type (man-in-the-middle attack?)");
        }
        throw new RuntimeException("this should not happen");
    }

    @Override public List<String> getKnownKeyAlgorithmsForHost(String hostname, int port) {
        return Arrays.asList(knownHosts.getPreferredServerHostkeyAlgorithmOrder(hostname));
    }

    @Override public void removeServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) {
        // not implemented
    }

    @Override public void addServerHostKey(String hostname, int port, String keyAlgorithm, byte[] serverHostKey) {
        // not implemented
    }
}
