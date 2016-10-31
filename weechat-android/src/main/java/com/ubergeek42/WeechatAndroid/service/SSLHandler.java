/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.service;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class SSLHandler {
    private static Logger logger = LoggerFactory.getLogger("SSLHandler");
    private static final String KEYSTORE_PASSWORD = "weechat-android";

    private File keystoreFile;
    private KeyStore sslKeystore;

    public SSLHandler(File keystoreFile) {
        this.keystoreFile = keystoreFile;
        loadKeystore();
    }

    public static @Nullable SSLHandler sslHandler = null;

    public static @NonNull SSLHandler getInstance(@NonNull Context context) {
        if (sslHandler == null) {
            File f = new File(context.getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks");
            sslHandler = new SSLHandler(f);
        }
        return sslHandler;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public int getUserCertificateCount() {
        try {
            return sslKeystore.size();
        } catch (KeyStoreException e) {
            logger.error("getUserCertificateCount()", e);
            return 0;
        }
    }

    public void trustCertificate(@NonNull X509Certificate cert) {
        try {
            KeyStore.TrustedCertificateEntry x = new KeyStore.TrustedCertificateEntry(cert);
            sslKeystore.setEntry(cert.getSubjectDN().getName(), x, null);
        } catch (KeyStoreException e) {
            logger.error("trustCertificate()", e);
        }
        saveKeystore();
    }

    public @Nullable SSLContext getSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, UserTrustManager.build(sslKeystore), new SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.error("getSSLContext()", e);
            return null;
        }
    }

    public SSLSocketFactory getSSLSocketFactory() {
        SSLCertificateSocketFactory sslSocketFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory.getDefault(0, null);
        sslSocketFactory.setTrustManagers(UserTrustManager.build(sslKeystore));
        return sslSocketFactory;
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
            else logger.error("loadKeystore()", e);
        }
    }

    private void createKeystore() {
        try {
            sslKeystore.load(null, null);
        } catch (Exception e) {
            logger.error("createKeystore()", e);
        }
        saveKeystore();
    }

    private void saveKeystore() {
        try {
            sslKeystore.store(new FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            logger.error("saveKeystore()", e);
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
                logger.debug("Client is trusted by system");
            } catch (CertificateException e) {
                logger.debug("Client is NOT trusted by system, trying user");
                userTrustManager.checkClientTrusted(x509Certificates, s);
                logger.debug("Client is trusted by user");
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
            try {
                systemTrustManager.checkServerTrusted(x509Certificates, s);
                logger.debug("Server is trusted by system");
            } catch (CertificateException e) {
                logger.debug("Server is NOT trusted by system, trying user");
                userTrustManager.checkServerTrusted(x509Certificates, s);
                logger.debug("Server is trusted by user");
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
}
