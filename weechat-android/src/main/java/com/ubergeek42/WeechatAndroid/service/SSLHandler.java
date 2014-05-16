package com.ubergeek42.WeechatAndroid.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class SSLHandler {
    private static Logger logger = LoggerFactory.getLogger(SSLHandler.class);
    private static final String KEYSTORE_PASSWORD = "weechat-android";

    File keystoreFile;
    KeyStore sslKeystore;


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
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            e.printStackTrace();
        } catch (CertificateException e) {
            // Ideally never happens
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            logger.debug("Keystore not found, creating...");
            createKeystore = true;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
                e.printStackTrace();
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
            e.printStackTrace();
        }

        saveKeystore();
    }

    private void saveKeystore() {
        try {
            sslKeystore.store(new FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
