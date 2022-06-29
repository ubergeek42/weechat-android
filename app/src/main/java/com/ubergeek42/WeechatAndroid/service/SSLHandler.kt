// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.ubergeek42.WeechatAndroid.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.X509TrustManagerExtensions
import android.os.Build
import androidx.annotation.CheckResult
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.WeechatAndroid.upload.suppress
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.deleteAndroidKeyStoreEntriesWithPrefix
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils.putKeyEntriesIntoAndroidKeyStoreWithPrefix
import com.ubergeek42.WeechatAndroid.utils.ThrowingKeyManagerWrapper
import com.ubergeek42.WeechatAndroid.utils.isAnyOf
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.RememberingTrustManager
import com.ubergeek42.weechat.SslAxolotl
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.UnsupportedOperationException
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
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


@Root private val kitty: Kitty = Kitty.make()


private const val SAN_DNSNAME = 2
private const val SAN_IPADDRESS = 7


const val KEYSTORE_ALIAS_PREFIX = "tls-client-cert-0."
private const val KEYSTORE_PASSWORD = "weechat-android"

// best-effort RDN regex, matches CN="foo,bar",OU=... and CN=foobar,OU=...
private val RDN_PATTERN = "CN\\s*=\\s*(\"[^\"]*\"|[^\",]*)".toRegex()


class SSLHandler private constructor(private val userKeystoreFile: File) {
    private val userKeystore = KeyStore.getInstance("BKS")
    init { loadUserKeystore() }
    private var userTrustManager = buildTrustManger(userKeystore)

    private fun loadUserKeystore() {
        suppress<Exception> {
            try {
                userKeystore.load(FileInputStream(userKeystoreFile), KEYSTORE_PASSWORD.toCharArray())
            } catch(_: FileNotFoundException) {
                userKeystore.load(null, null)
                saveUserKeystore()
            }
        }
    }

    private fun saveUserKeystore() {
        suppress<Exception> {
            userKeystore.store(FileOutputStream(userKeystoreFile), KEYSTORE_PASSWORD.toCharArray())
        }
    }

    @CheckResult fun removeUserKeystore(): Boolean {
        return if (userKeystoreFile.delete()) {
            cachedSslHandler = null
            true
        } else {
            false
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun getUserCertificateCount() = try {
        userKeystore.size()
    } catch (e: KeyStoreException) {
        kitty.error("getUserCertificateCount()", e)
        0
    }

    fun trustCertificate(cert: X509Certificate) {
        suppress<KeyStoreException> {
            val description = CertificateDialog.buildCertificateDescription(applicationContext, cert)
            kitty.debug("Trusting:\n$description")
            userKeystore.setEntry(cert.subjectDN.name, KeyStore.TrustedCertificateEntry(cert), null)
            userTrustManager = buildTrustManger(userKeystore)
        }
        saveUserKeystore()
    }

    fun makeSslAxolotl(): SslAxolotl {
        val sslContext = SSLContext.getInstance("TLS")
        val customTrustManager = CustomTrustManager(userTrustManager)
        sslContext.init(getKeyManagers(), arrayOf(customTrustManager), null)
        return SslAxolotl(sslContext.socketFactory, customTrustManager, hostnameVerifier)
    }

    companion object {
        private var cachedSslHandler: SSLHandler? = null

        @JvmStatic fun getInstance(context: Context): SSLHandler {
            cachedSslHandler?.let { return it }
            val userKeystoreFile = File(context.getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks")
            return SSLHandler(userKeystoreFile).also { cachedSslHandler = it }
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
private class CustomTrustManager(val userTrustManager: X509TrustManager)
        : X509TrustManager, RememberingTrustManager {
    override var lastServerOfferedCertificateChain: Array<X509Certificate>? = null
    override var lastAuthType: String? = null

    override fun checkClientTrusted(x509Certificates: Array<X509Certificate>, authType: String) {
        throw UnsupportedOperationException()
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(x509Certificates: Array<X509Certificate>, authType: String) {
        lastServerOfferedCertificateChain = x509Certificates
        lastAuthType = authType

        try {
            userTrustManager.checkServerTrusted(x509Certificates, authType)
            kitty.debug("Server is trusted by user")
        } catch (e: CertificateException) {
            kitty.debug("Server is NOT trusted by user (authType: $authType); pin "
                    + if (P.pinRequired) "REQUIRED -- failing" else "not required -- trying system")
            if (P.pinRequired) throw e
            systemTrustManager.checkServerTrusted(x509Certificates, authType)
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


// Given peer certificate chain, there might be several paths to different trust anchors.
// The leaf certificate might be a trust anchor; a shorter path to a trust anchor might not include
// all the certificates in the given chain; some of the certificates might be out of order.
// This will produce a cleaned-up chain that includes trust anchor,
// or throw if the chain is not trusted by system.
@Throws(CertificateException::class)
fun getSystemTrustedCertificateChain(
    serverOfferedCertificateChain: Array<X509Certificate>,
    serverOfferedAuthType: String,
    serverHost: String
): Array<X509Certificate> {
    return X509TrustManagerExtensions(systemTrustManager).checkServerTrusted(
        serverOfferedCertificateChain, serverOfferedAuthType, serverHost
    ).toTypedArray()
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


// Historical methods, for shits (no giggles)

// One of the issue with these two is that using GENERIC auth type is wrong
// and may produce a different result compared to the actual TrustManager check

// // Given peer certificate chain, there might be several paths to different trust anchors.
// // The leaf certificate might be a trust anchor; a shorter path to a trust anchor might not include
// // all the certificates in the given chain; some of the certificates might be out of order.
// // X509TrustManagerExtensions.checkServerTrusted will produce a cleaned-up chain
// // that includes trust anchor, but of course it will only work if the chain can be verified.
// // Otherwise, return server certificates as is, or null if the handshake fails.
// @SuppressLint("SSLCertificateSocketFactoryGetInsecure") @Suppress("UNCHECKED_CAST")
// fun getCertificateChain(host: String, port: Int): Array<X509Certificate>? {
//     val insecureSslFactory = SSLCertificateSocketFactory.getInsecure(0, null)
//     val socket = insecureSslFactory.createSocket(host, port) as javax.net.ssl.SSLSocket
//
//     val peerCertificateChain = try {
//         socket.startHandshake()
//         socket.session.peerCertificates as Array<X509Certificate>
//     } catch (e: Exception) {
//         return null
//     }
//
//     return try {
//         X509TrustManagerExtensions(systemTrustManager).checkServerTrusted(
//             peerCertificateChain, "GENERIC", host
//         ).toTypedArray()
//     } catch (e: Exception) {
//         peerCertificateChain
//     }
// }


// fun isChainTrustedBySystem(certificates: Array<X509Certificate?>?): Boolean {
//     return try {
//         systemTrustManager.checkServerTrusted(certificates, "GENERIC")
//         true
//     } catch (e: CertificateException) {
//         false
//     }
// }
