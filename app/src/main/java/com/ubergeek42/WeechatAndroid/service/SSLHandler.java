// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.utils.CertificateDialog;
import com.ubergeek42.WeechatAndroid.utils.ThrowingKeyManagerWrapper;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class SSLHandler {
    final private static @Root Kitty kitty = Kitty.make();

    private static final String KEYSTORE_PASSWORD = "weechat-android";
    // best-effort RDN regex, matches CN="foo,bar",OU=... and CN=foobar,OU=...
    private static final Pattern RDN_PATTERN = Pattern.compile("CN\\s*=\\s*((?:\"[^\"]*\")|(?:[^\",]*))");

    private File keystoreFile;
    private KeyStore sslKeystore;

    private SSLHandler(File keystoreFile) {
        this.keystoreFile = keystoreFile;
        loadKeystore();
    }

    private static @Nullable SSLHandler sslHandler = null;

    public static @NonNull SSLHandler getInstance(@NonNull Context context) {
        if (sslHandler == null) {
            File f = new File(context.getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks");
            sslHandler = new SSLHandler(f);
        }
        return sslHandler;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("SSLCertificateSocketFactoryGetInsecure")
    public static Result checkHostnameAndValidity(@NonNull String host, int port) {
        X509Certificate[] certificatesChain = null;
        try {
            SSLSocketFactory factory = SSLCertificateSocketFactory.getInsecure(0, null);
            try (SSLSocket ssl = (SSLSocket) factory.createSocket(host, port)) {
                ssl.startHandshake();
                SSLSession session = ssl.getSession();
                certificatesChain = (X509Certificate[]) session.getPeerCertificates();
                certificatesChain = appendIssuer(certificatesChain);
                for (X509Certificate certificate : certificatesChain)
                    certificate.checkValidity();
                if (!getHostnameVerifier().verify(host, session))
                    throw new SSLPeerUnverifiedException("Cannot verify hostname: " + host);
            }
        } catch (CertificateException | IOException e) {
            return new Result(e, certificatesChain);
        }
        return new Result(null, certificatesChain);
    }

    public static class Result {
        public final @Nullable Exception exception;
        public final @Nullable X509Certificate[] certificateChain;

        Result(@Nullable Exception exception, @Nullable X509Certificate[] certificateChain) {
            this.exception = exception;
            this.certificateChain = certificateChain;
        }
    }

    // servers can omit sending root CAs. this retrieves the root CA from the system store
    // and adds it to the chain. see https://stackoverflow.com/a/42168597/1449683
    public static X509Certificate[] appendIssuer(X509Certificate[] certificates) {
        X509TrustManager systemManager = UserTrustManager.buildTrustManger(null);
        if (systemManager == null) return certificates;
        X509Certificate rightmost = certificates[certificates.length - 1];
        for (X509Certificate issuer : systemManager.getAcceptedIssuers()) {
            try {
                rightmost.verify(issuer.getPublicKey());
                certificates = Arrays.copyOf(certificates, certificates.length + 1);
                certificates[certificates.length - 1] = issuer;
                return certificates;
            } catch (Exception ignored) {}
        }
        return certificates;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static int SAN_DNSNAME = 2;
    final private static int SAN_IPADDRESS = 7;

    // fall back to parsing CN only if SAN extension is not present. Android P no longer does this.
    // https://developer.android.com/about/versions/pie/android-9.0-changes-all#certificate-common-name
    public static Set<String> getCertificateHosts(X509Certificate certificate) throws Exception {
        final Set<String> hosts = new HashSet<>();

        Collection<List<?>> san = certificate.getSubjectAlternativeNames();

        if (san == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                Matcher matcher = RDN_PATTERN.matcher(certificate.getSubjectDN().getName());
                if (matcher.find())
                    hosts.add(matcher.group(1));
            }
        } else {
            for (List<?> pair : san) {
                if (Utils.isAnyOf((Integer) pair.get(0), SAN_DNSNAME, SAN_IPADDRESS))
                    hosts.add(pair.get(1).toString());
            }
        }
        return hosts;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public int getUserCertificateCount() {
        try {
            return sslKeystore.size();
        } catch (KeyStoreException e) {
            kitty.error("getUserCertificateCount()", e);
            return 0;
        }
    }

    public void trustCertificate(@NonNull X509Certificate cert) {
        try {
            KeyStore.TrustedCertificateEntry x = new KeyStore.TrustedCertificateEntry(cert);
            sslKeystore.setEntry(cert.getSubjectDN().getName(), x, null);
            kitty.debug("Trusting:\n" + CertificateDialog.buildCertificateDescription(Weechat.applicationContext, cert));
        } catch (KeyStoreException e) {
            kitty.error("trustCertificate()", e);
        }
        saveKeystore();
    }

    SSLSocketFactory getSSLSocketFactory() {
        SSLCertificateSocketFactory sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0, null);
        sslSocketFactory.setKeyManagers(getKeyManagers());
        sslSocketFactory.setTrustManagers(UserTrustManager.build(sslKeystore));
        return sslSocketFactory;
    }

    static public boolean isChainTrustedBySystem(X509Certificate[] certificates) {
        X509TrustManager systemManger = UserTrustManager.buildTrustManger(null);
        if (systemManger == null) return false;
        try {
            systemManger.checkServerTrusted(certificates, "GENERIC");
            return true;
        } catch (CertificateException e) {
            return false;
        }
    }

    // see android.net.SSLCertificateSocketFactory#verifyHostname
    static HostnameVerifier getHostnameVerifier() {
        return HttpsURLConnection.getDefaultHostnameVerifier();
    }

    @CheckResult public boolean removeKeystore() {
        if (keystoreFile.delete()) {
            sslHandler = null;
            return true;
        }
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // Load our keystore for storing SSL certificates
    private void loadKeystore() {
        try {
            sslKeystore = KeyStore.getInstance("BKS");
            sslKeystore.load(new FileInputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            if (e instanceof FileNotFoundException) createKeystore();
            else kitty.error("loadKeystore()", e);
        }
    }

    private void createKeystore() {
        try {
            sslKeystore.load(null, null);
        } catch (Exception e) {
            kitty.error("createKeystore()", e);
        }
        saveKeystore();
    }

    private void saveKeystore() {
        try {
            sslKeystore.store(new FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            kitty.error("saveKeystore()", e);
        }
    }

    private static class UserTrustManager implements X509TrustManager {
        static final X509TrustManager systemTrustManager = buildTrustManger(null);
        private final X509TrustManager userTrustManager;

        private static TrustManager[] build(KeyStore sslKeystore) {
            return new TrustManager[] { new UserTrustManager(sslKeystore) };
        }

        static X509TrustManager buildTrustManger(@Nullable KeyStore store) {
            try {
                TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(store);
                return (X509TrustManager) tmf.getTrustManagers()[0];
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                return null;
            }
        }

        private UserTrustManager(KeyStore userKeyStore) {
            this.userTrustManager = buildTrustManger(userKeyStore);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
            try {
                systemTrustManager.checkClientTrusted(x509Certificates, s);
                kitty.debug("Client is trusted by system");
            } catch (CertificateException e) {
                kitty.debug("Client is NOT trusted by system, trying user");
                userTrustManager.checkClientTrusted(x509Certificates, s);
                kitty.debug("Client is trusted by user");
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                throws CertificateException {
            try {
                userTrustManager.checkServerTrusted(x509Certificates, s);
                kitty.debug("Server is trusted by user");
            } catch (CertificateException e) {
                kitty.debug("Server is NOT trusted by user; pin " + (P.pinRequired ?
                        "REQUIRED -- failing" : "not required -- trying system"));
                if (P.pinRequired) throw e;

                systemTrustManager.checkServerTrusted(x509Certificates, s);
                kitty.debug("Server is trusted by system");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] system = systemTrustManager.getAcceptedIssuers();
            X509Certificate[] user = userTrustManager.getAcceptedIssuers();
            X509Certificate[] result = Arrays.copyOf(system, system.length + user.length);
            System.arraycopy(user, 0, result, system.length, user.length);
            return result;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public final static String KEYSTORE_ALIAS_PREFIX = "tls-client-cert-0.";
    private @Nullable KeyManager[] cachedKeyManagers = null;

    public void setClientCertificate(@Nullable byte[] bytes, String password) throws
            KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException,
            UnrecoverableKeyException {
        cachedKeyManagers = null;

        KeyStore pkcs12Keystore = KeyStore.getInstance("PKCS12");
        pkcs12Keystore.load(bytes == null ? null : new ByteArrayInputStream(bytes), password.toCharArray());

        KeyStore androidKeystore = KeyStore.getInstance("AndroidKeyStore");
        androidKeystore.load(null);

        for (String alias : Collections.list(androidKeystore.aliases())) {
            if (alias.startsWith(KEYSTORE_ALIAS_PREFIX)) androidKeystore.deleteEntry(alias);
        }

        // the store can also have certificate entries but we are not interested in those
        for (String alias : Collections.list(pkcs12Keystore.aliases())) {
            if (pkcs12Keystore.isKeyEntry(alias)) {
                Key key = pkcs12Keystore.getKey(alias, password.toCharArray());
                Certificate[] certs = pkcs12Keystore.getCertificateChain(alias);
                androidKeystore.setKeyEntry(KEYSTORE_ALIAS_PREFIX + alias, key, new char[0], certs);
            }
        }
    }

    private @Nullable KeyManager[] getKeyManagers() {
        if (cachedKeyManagers == null) {
            try {
                KeyStore androidKeystore = KeyStore.getInstance("AndroidKeyStore");
                androidKeystore.load(null);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
                keyManagerFactory.init(androidKeystore, null);
                cachedKeyManagers = keyManagerFactory.getKeyManagers();

                // this makes managers throw an exception if appropriate certificates can't be found
                ThrowingKeyManagerWrapper.wrapKeyManagers(cachedKeyManagers);
            } catch (Exception e) {
                kitty.error("getKeyManagers()", e);
            }
        }
        return cachedKeyManagers;
    }
}
