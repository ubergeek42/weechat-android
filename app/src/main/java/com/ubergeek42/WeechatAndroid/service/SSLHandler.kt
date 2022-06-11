// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.SSLCertificateSocketFactory
import android.os.Build
import androidx.annotation.CheckResult
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.deleteAndroidKeyStoreEntriesWithPrefix
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.putKeyEntriesIntoAndroidKeyStoreWithPrefix
import com.ubergeek42.WeechatAndroid.utils.ThrowingKeyManagerWrapper
import com.ubergeek42.WeechatAndroid.utils.isAnyOf
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


@Root private val kitty: Kitty = Kitty.make()


private const val SAN_DNSNAME = 2
private const val SAN_IPADDRESS = 7


const val KEYSTORE_ALIAS_PREFIX = "tls-client-cert-0."
private const val KEYSTORE_PASSWORD = "weechat-android"

// best-effort RDN regex, matches CN="foo,bar",OU=... and CN=foobar,OU=...
private val RDN_PATTERN = "CN\\s*=\\s*(\"[^\"]*\"|[^\",]*)".toRegex()


class SSLHandler private constructor(private val keystoreFile: File) {
    private var sslKeystore = KeyStore.getInstance("BKS").also { loadKeystore() }

    private fun loadKeystore() {
        try {
            sslKeystore.load(FileInputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray())
        } catch (e: Exception) {
            if (e is FileNotFoundException) {
                createKeystore()
            } else {
                kitty.error("loadKeystore()", e)
            }
        }
    }

    private fun createKeystore() {
        try {
            sslKeystore.load(null, null)
        } catch (e: Exception) {
            kitty.error("createKeystore()", e)
        }
        saveKeystore()
    }

    private fun saveKeystore() {
        try {
            sslKeystore.store(FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray())
        } catch (e: Exception) {
            kitty.error("saveKeystore()", e)
        }
    }

    @CheckResult fun removeKeystore(): Boolean {
        return if (keystoreFile.delete()) {
            sslHandler = null
            true
        } else {
            false
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getUserCertificateCount() = try {
        sslKeystore.size()
    } catch (e: KeyStoreException) {
        kitty.error("getUserCertificateCount()", e)
        0
    }

    fun trustCertificate(cert: X509Certificate) {
        try {
            sslKeystore.setEntry(cert.subjectDN.name, KeyStore.TrustedCertificateEntry(cert), null)
            val description = CertificateDialog.buildCertificateDescription(applicationContext, cert)
            kitty.debug("Trusting:\n$description")
        } catch (e: KeyStoreException) {
            kitty.error("trustCertificate()", e)
        }
        saveKeystore()
    }

    fun getSSLSocketFactory(): SSLSocketFactory {
        val sslSocketFactory = SSLCertificateSocketFactory.getDefault(0, null) as SSLCertificateSocketFactory
        sslSocketFactory.setKeyManagers(getKeyManagers())
        sslSocketFactory.setTrustManagers(arrayOf(SystemThenUserTrustManager(sslKeystore)))
        return sslSocketFactory
    }



    companion object {
        private var sslHandler: SSLHandler? = null

        @JvmStatic fun getInstance(context: Context): SSLHandler {
            if (sslHandler == null) {
                val file = File(context.getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks")
                sslHandler = SSLHandler(file)
            }
            return sslHandler!!
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////// client cert
////////////////////////////////////////////////////////////////////////////////////////////////////


private var cachedKeyManagers: Array<KeyManager>? = null


@Throws(KeyStoreException::class,
        CertificateException::class,
        NoSuchAlgorithmException::class,
        IOException::class,
        UnrecoverableKeyException::class)
fun setClientCertificate(bytes: ByteArray?, password: String) {
    val pkcs12Keystore = KeyStore.getInstance("PKCS12")
    pkcs12Keystore.load(if (bytes == null) null else ByteArrayInputStream(bytes), password.toCharArray())
    deleteAndroidKeyStoreEntriesWithPrefix(KEYSTORE_ALIAS_PREFIX)
    putKeyEntriesIntoAndroidKeyStoreWithPrefix(pkcs12Keystore, password, KEYSTORE_ALIAS_PREFIX)
    cachedKeyManagers = null
}


private fun getKeyManagers(): Array<KeyManager>? {
    return if (cachedKeyManagers != null) {
        cachedKeyManagers
    } else {
        try {
            val keyManagerFactory = KeyManagerFactory.getInstance("X509")
            keyManagerFactory.init(AndroidKeyStoreUtils.getAndroidKeyStore(), null)
            val keyManagers = keyManagerFactory.keyManagers

            // this makes managers throw an exception if appropriate certificates can't be found
            ThrowingKeyManagerWrapper.wrapKeyManagers(keyManagers)

            cachedKeyManagers = keyManagers
            keyManagers
        } catch (e: Exception) {
            kitty.error("getKeyManagers()", e)
            null
        }
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


@SuppressLint("CustomX509TrustManager")
private class SystemThenUserTrustManager(userKeyStore: KeyStore?) : X509TrustManager {
    private val userTrustManager = buildTrustManger(userKeyStore)

    @Throws(CertificateException::class)
    override fun checkClientTrusted(x509Certificates: Array<X509Certificate>, s: String) {
        try {
            systemTrustManager.checkClientTrusted(x509Certificates, s)
            kitty.debug("Client is trusted by system")
        } catch (e: CertificateException) {
            kitty.debug("Client is NOT trusted by system, trying user")
            userTrustManager.checkClientTrusted(x509Certificates, s)
            kitty.debug("Client is trusted by user")
        }
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(x509Certificates: Array<X509Certificate>, s: String) {
        try {
            userTrustManager.checkServerTrusted(x509Certificates, s)
            kitty.debug("Server is trusted by user")
        } catch (e: CertificateException) {
            kitty.debug("Server is NOT trusted by user; pin " + if (P.pinRequired) "REQUIRED -- failing" else "not required -- trying system")
            if (P.pinRequired) throw e
            systemTrustManager.checkServerTrusted(x509Certificates, s)
            kitty.debug("Server is trusted by system")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return systemTrustManager.acceptedIssuers + userTrustManager.acceptedIssuers
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


class CheckHostnameAndValidityResult(
    val exception: Exception?,
    val certificateChain: Array<X509Certificate>?
)


@SuppressLint("SSLCertificateSocketFactoryGetInsecure")
fun checkHostnameAndValidity(host: String, port: Int): CheckHostnameAndValidityResult {
    var certificatesChain: Array<X509Certificate>? = null

    try {
        val factory = SSLCertificateSocketFactory.getInsecure(0, null)
        (factory.createSocket(host, port) as javax.net.ssl.SSLSocket).use { sslSocket ->
            sslSocket.startHandshake()
            val session = sslSocket.session
            certificatesChain = session.peerCertificates as Array<X509Certificate>
            certificatesChain = appendIssuer(certificatesChain as Array<X509Certificate>)

            for (certificate in certificatesChain!!) certificate.checkValidity()

            if (!hostnameVerifier.verify(host, session)) {
                throw SSLPeerUnverifiedException("Cannot verify hostname: $host")
            }
        }
    } catch (e: CertificateException) {
        return CheckHostnameAndValidityResult(e, certificatesChain)
    } catch (e: IOException) {
        return CheckHostnameAndValidityResult(e, certificatesChain)
    }
    return CheckHostnameAndValidityResult(null, certificatesChain)
}


// servers can omit sending root CAs. this retrieves the root CA from the system store
// and adds it to the chain. see https://stackoverflow.com/a/42168597/1449683
private fun appendIssuer(certificateChain: Array<X509Certificate>): Array<X509Certificate> {
    val rightmostCertificate = certificateChain[certificateChain.size - 1]

    systemTrustManager.acceptedIssuers.forEach { issuer ->
        try {
            rightmostCertificate.verify(issuer.publicKey)
            return certificateChain + arrayOf(issuer)
        } catch (ignored: Exception) {}
    }

    return certificateChain
}



// fall back to parsing CN only if SAN extension is not present. Android P no longer does this.
// https://developer.android.com/about/versions/pie/android-9.0-changes-all#certificate-common-name
@Throws(Exception::class)
fun getCertificateHosts(certificate: X509Certificate): Set<String> {
    val subjectAlternativeNames = certificate.subjectAlternativeNames

    if (subjectAlternativeNames != null) {
        return subjectAlternativeNames
                .filter { pair -> (pair[0] as? Int).isAnyOf(SAN_DNSNAME, SAN_IPADDRESS) }
                .map { pair -> pair[1].toString() }
                .toSet()
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        RDN_PATTERN.find(certificate.subjectDN.name)?.also { matchResult ->
            return setOf(matchResult.groupValues[1])
        }
    }

    return emptySet()
}


fun isChainTrustedBySystem(certificates: Array<X509Certificate?>?): Boolean {
    return try {
        systemTrustManager.checkServerTrusted(certificates, "GENERIC")
        true
    } catch (e: CertificateException) {
        false
    }
}


// see android.net.SSLCertificateSocketFactory#verifyHostname
val hostnameVerifier: HostnameVerifier
    get() = HttpsURLConnection.getDefaultHostnameVerifier()


// Kotlin refactor change: throw instead of returning null!
@Throws(NoSuchAlgorithmException::class, KeyStoreException::class)
fun buildTrustManger(store: KeyStore?): X509TrustManager {
    return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .also { it.init(store) }
            .trustManagers[0] as X509TrustManager
}


val systemTrustManager = buildTrustManger(null)
