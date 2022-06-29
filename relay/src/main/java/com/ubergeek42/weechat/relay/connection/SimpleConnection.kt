package com.ubergeek42.weechat.relay.connection

import com.ubergeek42.weechat.SslAxolotl
import com.ubergeek42.weechat.wrapExceptions
import kotlin.Throws
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket


class SimpleConnection(
    private val hostname: String,
    private val port: Int,
    private val sslAxolotl: SslAxolotl?,
) : IConnection {
    private val socket = Socket()

    // Do NOT call the argument-less `sslSocketFactory.sslSocketFactory()`,
    // for on Android 6 it creates a socket that does not play well with SNI
    // and won't connect to websites that require it, e.g. badssl.com.
    @Suppress("IfThenToElvis")
    @Throws(IOException::class)
    override fun connect(): IConnection.Streams {
        socket.connect(InetSocketAddress(hostname, port), RelayConnection.CONNECTION_TIMEOUT)

        val finalSocket = if (sslAxolotl == null) {
            socket
        } else {
            sslAxolotl.wrapExceptions {
                val sslSocket = sslAxolotl.sslSocketFactory
                        .createSocket(socket, hostname, port, true) as SSLSocket
                sslSocket.startHandshake()
                Utils.verifyHostname(sslAxolotl.hostnameVerifier, sslSocket, hostname)
                sslSocket
            }
        }

        return IConnection.Streams(finalSocket.getInputStream(), finalSocket.getOutputStream())
    }

    @Throws(IOException::class)
    override fun disconnect() {
        socket.close()
    }
}