package com.ubergeek42.weechat

import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory


interface RememberingTrustManager {
    val lastServerOfferedCertificateChain: Array<X509Certificate>?
    val lastAuthType: String?
}


class SslAxolotl(
    val sslSocketFactory: SSLSocketFactory,
    val rememberingTrustManager: RememberingTrustManager,
    val hostnameVerifier: HostnameVerifier,
) {
    class ExceptionWrapper(
        val lastServerOfferedCertificateChain: Array<X509Certificate>?,
        val lastAuthType: String?,
        exception: Exception
    ) : Exception(exception)
}


fun <T> SslAxolotl.wrapExceptions(block: () -> T): T {
    try {
        return block()
    } catch (exception: Exception) {
        throw SslAxolotl.ExceptionWrapper(
            rememberingTrustManager.lastServerOfferedCertificateChain,
            rememberingTrustManager.lastAuthType,
            exception
        )
    }
}