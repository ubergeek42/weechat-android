package com.ubergeek42.WeechatAndroid.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509ExtendedKeyManager;

// the only purpose of this class is to throw an exception if the server asks for a certificate
// but we don't have a suitable one.

// note that some servers will ask for a certificate while not strictly requiring it. for
// instance, the easiest way to configure nginx & client certificates is to ask for the
// certificate for the whole website but only fail in certain locations. asking for a
// certificate only in some locations would require renegotiation. (the better approach
// would be to use a subdomain.) this use case is not covered here; we consider the situation
// where the website is asking for a certificate but we can't provide one a failure.

public class ThrowingKeyManagerWrapper extends X509ExtendedKeyManager {
    final private X509ExtendedKeyManager keyManager;

    public ThrowingKeyManagerWrapper(X509ExtendedKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public static void wrapKeyManagers(KeyManager[] managers) {
        for (int i = 0; i < managers.length; i++)
            if (managers[i] instanceof X509ExtendedKeyManager)
                managers[i] = new ThrowingKeyManagerWrapper((X509ExtendedKeyManager) managers[i]);
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
        final public @NonNull String[] keyType;
        final public @Nullable Principal[] issuers;

        public ClientCertificateMismatchException(@NonNull String[] keyType, @Nullable Principal[] issuers) {
            this.keyType = keyType;
            this.issuers = issuers;
        }
    }
}

