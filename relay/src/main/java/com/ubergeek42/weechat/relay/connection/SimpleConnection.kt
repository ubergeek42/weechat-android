package com.ubergeek42.weechat.relay.connection

import kotlin.Throws
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

class SimpleConnection(
    private val hostname: String,
    private val port: Int,
    sslSocketFactory: SSLSocketFactory?,
    private val verifier: HostnameVerifier,
) : IConnection {
    private val socket = if (sslSocketFactory == null) Socket() else sslSocketFactory.createSocket()

    @Throws(IOException::class)
    override fun connect(): IConnection.Streams {
        socket.connect(InetSocketAddress(hostname, port), RelayConnection.CONNECTION_TIMEOUT)
        Utils.verifyHostname(verifier, socket, hostname)
        return IConnection.Streams(socket.getInputStream(), socket.getOutputStream())
    }

    @Throws(IOException::class)
    override fun disconnect() {
        socket.close()
    }
}