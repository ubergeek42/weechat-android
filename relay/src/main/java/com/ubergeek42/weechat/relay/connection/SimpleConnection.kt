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
    private val socket = if (sslAxolotl == null) {
                             Socket()
                         } else {
                             sslAxolotl.sslSocketFactory.createSocket()
                         }

    @Throws(IOException::class)
    override fun connect(): IConnection.Streams {
        socket.connect(InetSocketAddress(hostname, port), RelayConnection.CONNECTION_TIMEOUT)

        sslAxolotl?.wrapExceptions {
            (socket as SSLSocket).startHandshake()
            Utils.verifyHostname(sslAxolotl.hostnameVerifier, socket, hostname)
        }

        return IConnection.Streams(socket.getInputStream(), socket.getOutputStream())
    }

    @Throws(IOException::class)
    override fun disconnect() {
        socket.close()
    }
}