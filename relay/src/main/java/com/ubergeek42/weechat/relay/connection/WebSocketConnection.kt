package com.ubergeek42.weechat.relay.connection

import com.neovisionaries.ws.client.WebSocket
import com.ubergeek42.weechat.relay.connection.IConnection
import kotlin.Throws
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketAdapter
import com.ubergeek42.weechat.relay.connection.WebSocketConnection
import com.neovisionaries.ws.client.WebSocketFrame
import com.neovisionaries.ws.client.WebSocketFactory
import com.ubergeek42.weechat.relay.connection.RelayConnection
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.Exception
import java.net.URI
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory

class WebSocketConnection(
    hostname: String, port: Int, path: String, sslSocketFactory: SSLSocketFactory?,
    verifier: HostnameVerifier
) : IConnection {
    private val verifier: HostnameVerifier
    private val hostname: String
    private val webSocket: WebSocket
    private val pipedOutputStream: PipedOutputStream
    @Throws(IOException::class, WebSocketException::class)
    override fun connect(): IConnection.Streams {
        val inputStream = PipedInputStream()
        pipedOutputStream.connect(inputStream)
        webSocket.connect()
        Utils.verifyHostname(verifier, webSocket.socket, hostname)
        return IConnection.Streams(inputStream, null)
    }

    @Throws(IOException::class) override fun disconnect() {
        webSocket.disconnect()
        pipedOutputStream.close()
    }

    fun sendMessage(string: String?) {
        webSocket.sendText(string)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private inner class Listener : WebSocketAdapter() {
        override fun onConnected(websocket: WebSocket, headers: Map<String, List<String>>) {
            logger.trace("onConnected()")
        }

        override fun onConnectError(websocket: WebSocket, exception: WebSocketException) {
            logger.error("onConnectError({})", exception)
        }

        @Throws(IOException::class) override fun onDisconnected(websocket: WebSocket,
                                                                serverCloseFrame: WebSocketFrame,
                                                                clientCloseFrame: WebSocketFrame,
                                                                closedByServer: Boolean) {
            logger.trace("onDisconnected(closedByServer={})", closedByServer)
            pipedOutputStream.close()
        }

        @Throws(Exception::class) override fun onBinaryMessage(websocket: WebSocket,
                                                               binary: ByteArray) {
            logger.trace("onBinaryMessage(size={})", binary.size)
            pipedOutputStream.write(binary)
            pipedOutputStream.flush() // much faster with this
        }

        override fun onError(websocket: WebSocket, cause: WebSocketException) {
            logger.error("onError()", cause)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("WebSocketConnection")
    }

    init {
        val uri = URI(if (sslSocketFactory == null) "ws" else "wss",
                      null,
                      hostname,
                      port,
                      "/$path",
                      null,
                      null)
        this.verifier = verifier
        this.hostname = hostname
        webSocket = WebSocketFactory()
                .setSSLSocketFactory(sslSocketFactory)
                .setVerifyHostname(false)
                .setConnectionTimeout(RelayConnection.CONNECTION_TIMEOUT)
                .createSocket(uri)
        webSocket.addListener(Listener())
        pipedOutputStream = PipedOutputStream()
    }
}