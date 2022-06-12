package com.ubergeek42.weechat.relay.connection

import com.ubergeek42.weechat.relay.connection.IConnection
import kotlin.Throws
import com.ubergeek42.weechat.relay.connection.RelayConnection
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

class SimpleConnection(
    private val hostname: String, private val port: Int, sslSocketFactory: SSLSocketFactory?,
    private val verifier: HostnameVerifier
) : IConnection {
    private val socket: Socket
    @Throws(IOException::class) override fun connect(): IConnection.Streams {
        socket.connect(InetSocketAddress(hostname, port), RelayConnection.CONNECTION_TIMEOUT)
        Utils.verifyHostname(verifier, socket, hostname)
        return IConnection.Streams(socket.getInputStream(), socket.getOutputStream())
    }

    @Throws(IOException::class) override fun disconnect() {
        socket.close()
    }

    init {
        socket = if (sslSocketFactory == null) Socket() else sslSocketFactory.createSocket()
    }
}