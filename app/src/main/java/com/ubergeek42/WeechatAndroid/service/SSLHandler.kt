// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
package com.ubergeek42.WeechatAndroid.service

import com.ubergeek42.WeechatAndroid.service.SSLHandler
import com.ubergeek42.WeechatAndroid.dialogs.CertificateDialog
import com.ubergeek42.WeechatAndroid.Weechat
import android.net.SSLCertificateSocketFactory
import com.ubergeek42.WeechatAndroid.service.SSLHandler.UserTrustManager
import kotlin.Throws
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.AndroidKeyStoreUtils
import com.ubergeek42.WeechatAndroid.utils.ThrowingKeyManagerWrapper
import com.ubergeek42.cats.Kitty
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.CheckResult
import com.ubergeek42.WeechatAndroid.utils.Utils
import com.ubergeek42.cats.Root
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import java.util.regex.Pattern
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class SSLHandler private constructor(private val keystoreFile: File) {
    private var sslKeystore: KeyStore? = null

    class Result internal constructor(val exception: Exception?,
                                      val certificateChain: Array<X509Certificate>?)

    ////////////////////////////////////////////////////////////////////////////////////////////////
    val userCertificateCount: Int
        get() = try {
            sslKeystore!!.size()
        } catch (e: KeyStoreException) {
            kitty.error("getUserCertificateCount()", e)
            0
        }

    fun trustCertificate(cert: X509Certificate) {
        try {
            val x = KeyStore.TrustedCertificateEntry(cert)
            sslKeystore!!.setEntry(cert.subjectDN.name, x, null)
            kitty.debug("""
    Trusting:
    ${CertificateDialog.buildCertificateDescription(Weechat.applicationContext, cert)}
    """.trimIndent())
        } catch (e: KeyStoreException) {
            kitty.error("trustCertificate()", e)
        }
        saveKeystore()
    }

    val sSLSocketFactory: SSLSocketFactory
        get() {
            val sslSocketFactory =
                SSLCertificateSocketFactory.getDefault(0, null) as SSLCertificateSocketFactory
            sslSocketFactory.setKeyManagers(keyManagers)
            sslSocketFactory.setTrustManagers(UserTrustManager.build(sslKeystore))
            return sslSocketFactory
        }

    @CheckResult fun removeKeystore(): Boolean {
        if (keystoreFile.delete()) {
            sslHandler = null
            return true
        }
        return false
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Load our keystore for storing SSL certificates
    private fun loadKeystore() {
        try {
            sslKeystore = KeyStore.getInstance("BKS")
            sslKeystore.load(FileInputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray())
        } catch (e: KeyStoreException) {
            if (e is FileNotFoundException) createKeystore() else kitty.error("loadKeystore()", e)
        } catch (e: NoSuchAlgorithmException) {
            if (e is FileNotFoundException) createKeystore() else kitty.error("loadKeystore()", e)
        } catch (e: CertificateException) {
            if (e is FileNotFoundException) createKeystore() else kitty.error("loadKeystore()", e)
        } catch (e: IOException) {
            if (e is FileNotFoundException) createKeystore() else kitty.error("loadKeystore()", e)
        }
    }

    private fun createKeystore() {
        try {
            sslKeystore!!.load(null, null)
        } catch (e: Exception) {
            kitty.error("createKeystore()", e)
        }
        saveKeystore()
    }

    private fun saveKeystore() {
        try {
            sslKeystore!!.store(FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray())
        } catch (e: KeyStoreException) {
            kitty.error("saveKeystore()", e)
        } catch (e: NoSuchAlgorithmException) {
            kitty.error("saveKeystore()", e)
        } catch (e: CertificateException) {
            kitty.error("saveKeystore()", e)
        } catch (e: IOException) {
            kitty.error("saveKeystore()", e)
        }
    }

    private class UserTrustManager private constructor(userKeyStore: KeyStore?) : X509TrustManager {
        private val userTrustManager: X509TrustManager?
        @Throws(CertificateException::class)
        override fun checkClientTrusted(x509Certificates: Array<X509Certificate>, s: String) {
            try {
                systemTrustManager!!.checkClientTrusted(x509Certificates, s)
                kitty.debug("Client is trusted by system")
            } catch (e: CertificateException) {
                kitty.debug("Client is NOT trusted by system, trying user")
                userTrustManager!!.checkClientTrusted(x509Certificates, s)
                kitty.debug("Client is trusted by user")
            }
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(x509Certificates: Array<X509Certificate>, s: String) {
            try {
                userTrustManager!!.checkServerTrusted(x509Certificates, s)
                kitty.debug("Server is trusted by user")
            } catch (e: CertificateException) {
                kitty.debug("Server is NOT trusted by user; pin " + if (P.pinRequired) "REQUIRED -- failing" else "not required -- trying system")
                if (P.pinRequired) throw e
                systemTrustManager!!.checkServerTrusted(x509Certificates, s)
                kitty.debug("Server is trusted by system")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            val system = systemTrustManager!!.acceptedIssuers
            val user = userTrustManager!!.acceptedIssuers
            val result = Arrays.copyOf(system, system.size + user.size)
            System.arraycopy(user, 0, result, system.size, user.size)
            return result
        }

        companion object {
            val systemTrustManager = buildTrustManger(null)
            fun build(sslKeystore: KeyStore?): Array<TrustManager> {
                return arrayOf(UserTrustManager(sslKeystore))
            }

            fun buildTrustManger(store: KeyStore?): X509TrustManager? {
                return try {
                    val tmf =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    tmf.init(store)
                    tmf.trustManagers[0] as X509TrustManager
                } catch (e: NoSuchAlgorithmException) {
                    null
                } catch (e: KeyStoreException) {
                    null
                }
            }
        }

        init {
            userTrustManager = buildTrustManger(userKeyStore)
        }
    }

    private var cachedKeyManagers: Array<KeyManager>? = null
    @Throws(KeyStoreException::class,
            CertificateException::class,
            NoSuchAlgorithmException::class,
            IOException::class,
            UnrecoverableKeyException::class) fun setClientCertificate(bytes: ByteArray?,
                                                                       password: String) {
        cachedKeyManagers = null
        val pkcs12Keystore = KeyStore.getInstance("PKCS12")
        pkcs12Keystore.load(if (bytes == null) null else ByteArrayInputStream(bytes),
                            password.toCharArray())
        AndroidKeyStoreUtils.deleteAndroidKeyStoreEntriesWithPrefix(KEYSTORE_ALIAS_PREFIX)
        AndroidKeyStoreUtils.putKeyEntriesIntoAndroidKeyStoreWithPrefix(pkcs12Keystore,
                                                                        password,
                                                                        KEYSTORE_ALIAS_PREFIX)
    }

    // this makes managers throw an exception if appropriate certificates can't be found
    private val keyManagers: Array<KeyManager>?
        private get() {
            if (cachedKeyManagers == null) {
                try {
                    val androidKeystore = AndroidKeyStoreUtils.getAndroidKeyStore()
                    val keyManagerFactory = KeyManagerFactory.getInstance("X509")
                    keyManagerFactory.init(androidKeystore, null)
                    cachedKeyManagers = keyManagerFactory.keyManagers

                    // this makes managers throw an exception if appropriate certificates can't be found
                    ThrowingKeyManagerWrapper.wrapKeyManagers(cachedKeyManagers)
                } catch (e: Exception) {
                    kitty.error("getKeyManagers()", e)
                }
            }
            return cachedKeyManagers
        }

    companion object {
        @Root
        private val kitty: Kitty = Kitty.make()
        private const val KEYSTORE_PASSWORD = "weechat-android"

        // best-effort RDN regex, matches CN="foo,bar",OU=... and CN=foobar,OU=...
        private val RDN_PATTERN = Pattern.compile("CN\\s*=\\s*((?:\"[^\"]*\")|(?:[^\",]*))")
        private var sslHandler: SSLHandler? = null
        @JvmStatic fun getInstance(context: Context): SSLHandler {
            if (sslHandler == null) {
                val f = File(context.getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks")
                sslHandler = SSLHandler(f)
            }
            return sslHandler!!
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////
        @SuppressLint("SSLCertificateSocketFactoryGetInsecure")
        fun checkHostnameAndValidity(host: String, port: Int): Result {
            var certificatesChain: Array<X509Certificate>? = null
            try {
                val factory = SSLCertificateSocketFactory.getInsecure(0, null)
                factory.createSocket(host, port) as javax.net.ssl.SSLSocket?. use { ssl ->
                    ssl.startHandshake()
                    val session: SSLSession = ssl.getSession()
                    certificatesChain = session.peerCertificates as Array<X509Certificate>
                    certificatesChain = appendIssuer(certificatesChain)
                    for (certificate in certificatesChain!!) certificate.checkValidity()
                    if (!hostnameVerifier.verify(host, session)) throw SSLPeerUnverifiedException(
                        "Cannot verify hostname: $host")
                }
            } catch (e: CertificateException) {
                return Result(e, certificatesChain)
            } catch (e: IOException) {
                return Result(e, certificatesChain)
            }
            return Result(null, certificatesChain)
        }

        // servers can omit sending root CAs. this retrieves the root CA from the system store
        // and adds it to the chain. see https://stackoverflow.com/a/42168597/1449683
        fun appendIssuer(certificates: Array<X509Certificate>?): Array<X509Certificate>? {
            var certificates = certificates
            val systemManager = UserTrustManager.buildTrustManger(null) ?: return certificates
            val rightmost = certificates!![certificates.size - 1]
            for (issuer in systemManager.acceptedIssuers) {
                try {
                    rightmost.verify(issuer.publicKey)
                    certificates = Arrays.copyOf(certificates, certificates!!.size + 1)
                    certificates[certificates.size - 1] = issuer
                    return certificates
                } catch (ignored: Exception) {
                }
            }
            return certificates
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////
        private const val SAN_DNSNAME = 2
        private const val SAN_IPADDRESS = 7

        // fall back to parsing CN only if SAN extension is not present. Android P no longer does this.
        // https://developer.android.com/about/versions/pie/android-9.0-changes-all#certificate-common-name
        @JvmStatic @Throws(Exception::class)
        fun getCertificateHosts(certificate: X509Certificate): Set<String> {
            val hosts: MutableSet<String> = HashSet()
            val san = certificate.subjectAlternativeNames
            if (san == null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    val matcher = RDN_PATTERN.matcher(certificate.subjectDN.name)
                    if (matcher.find()) hosts.add(matcher.group(1))
                }
            } else {
                for (pair in san) {
                    if (Utils.isAnyOf((pair[0] as Int?)!!, SAN_DNSNAME, SAN_IPADDRESS)) hosts.add(
                        pair[1].toString())
                }
            }
            return hosts
        }

        @JvmStatic fun isChainTrustedBySystem(certificates: Array<X509Certificate?>?): Boolean {
            val systemManager = UserTrustManager.buildTrustManger(null) ?: return false
            return try {
                systemManager.checkServerTrusted(certificates, "GENERIC")
                true
            } catch (e: CertificateException) {
                false
            }
        }

        // see android.net.SSLCertificateSocketFactory#verifyHostname
        @JvmStatic val hostnameVerifier: HostnameVerifier
            get() = HttpsURLConnection.getDefaultHostnameVerifier()

        ////////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////
        const val KEYSTORE_ALIAS_PREFIX = "tls-client-cert-0."
    }

    init {
        loadKeystore()
    }
}