package com.ubergeek42.WeechatAndroid.utils;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509ExtendedKeyManager;

public class ThrowingKeyManagerWrapper extends X509ExtendedKeyManager {
    final private X509ExtendedKeyManager keyManager;

    public ThrowingKeyManagerWrapper(X509ExtendedKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override public String[] getClientAliases(String keyType, Principal[] issuers) {
        return keyManager.getClientAliases(keyType, issuers);
    }

    @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        String alias = keyManager.chooseClientAlias(keyType, issuers, socket);
        if (alias == null) throw new ClientCertificateMismatchException(keyType, issuers);
        return alias;
    }

    @Override public String[] getServerAliases(String keyType, Principal[] issuers) {
        return keyManager.getServerAliases(keyType, issuers);
    }

    @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return keyManager.chooseServerAlias(keyType, issuers, socket);
    }

    @Override public X509Certificate[] getCertificateChain(String alias) {
        return keyManager.getCertificateChain(alias);
    }

    @Override public PrivateKey getPrivateKey(String alias) {
        return keyManager.getPrivateKey(alias);
    }

    public static class ClientCertificateMismatchException extends RuntimeException {
        final public String[] keyType;
        final public Principal[] issuers;

        public ClientCertificateMismatchException(String[] keyType, Principal[] issuers) {
            this.keyType = keyType;
            this.issuers = issuers;
        }
    }
}

