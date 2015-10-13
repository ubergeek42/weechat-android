/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.WeechatAndroid.service;

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

import javax.net.ssl.SSLContext;
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

    // Load our keystore for storing SSL certificates
    void loadKeystore() {
        boolean createKeystore = false;

        try {
            sslKeystore = KeyStore.getInstance("BKS");
            sslKeystore.load(new FileInputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            logger.error("Error: " + e.getMessage());
        } catch (FileNotFoundException e) {
            logger.error("Error: " + e.getMessage());
            createKeystore = true;
        } catch (IOException e) {
            logger.error("Error: " + e.getMessage());
        }

        if (createKeystore) {
            createKeystore();
        }
    }

    void trustCertificate(X509Certificate cert) {
        if (cert!=null) {
            try {
                KeyStore.TrustedCertificateEntry x = new KeyStore.TrustedCertificateEntry(cert);
                sslKeystore.setEntry(cert.getSubjectDN().getName(), x, null);
            } catch (KeyStoreException e) {
                logger.error("Error: " + e.getMessage());
            }
            saveKeystore();
        }
    }

    private void createKeystore() {
        try {
            sslKeystore.load(null,null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore)null);
            // Copy current certs into our keystore so we can use it...
            // TODO: don't actually do this...
            X509TrustManager xtm = (X509TrustManager) tmf.getTrustManagers()[0];
            for (X509Certificate cert : xtm.getAcceptedIssuers()) {
                sslKeystore.setCertificateEntry(cert.getSubjectDN().getName(), cert);
            }
        } catch (Exception e) {
            logger.error("Error: " + e.getMessage());
        }

        saveKeystore();
    }

    private void saveKeystore() {
        try {
            sslKeystore.store(new FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (Exception e) {
            logger.error("Error: " + e.getMessage());
        }
    }


    public @Nullable SSLContext getSSLContext() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(sslKeystore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            return null;
        }
    }
}
