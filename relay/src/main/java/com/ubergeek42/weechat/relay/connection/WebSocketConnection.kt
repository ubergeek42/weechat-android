package com.ubergeek42.weechat.relay.connection

import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import com.ubergeek42.weechat.SslAxolotl
import com.ubergeek42.weechat.wrapExceptions
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import javax.net.ssl.SSLSocket


class WebSocketConnection(
    private val hostname: String,
    port: Int,
    path: String,
    private val sslAxolotl: SslAxolotl?,
) : IConnection {
    private val webSocket: WebSocket
    private val pipedOutputStream = PipedOutputStream()

    // This is why we call setVerifyHostname(false) here:
    // The library is verifying the hostname like this:
    //   session = socket.getSession()
    //   if (verifier.verify(hostname, session)) { /* ok */ }
    //
    // This looks good on the surface, but there's a problem;
    // `socket.getSession()` does *not* throw any useful exceptions! As per the doc,
    //   > If an error occurs during the initial handshake, this method returns an invalid
    //   > session object which reports an invalid cipher suite of "SSL_NULL_WITH_NULL_NULL".
    // The subsequent call to the boolean method `verifier.verify()` also doesn't throw.
    // While it will not be able to verify the host, the only indication of an error that we get
    // is the false result of the call.
    //
    // However, if we disable *library* hostname verification, the library will try
    // reading from a stream, which *does* throw errors. We then verify the connection
    // after it has been established.
    //
    // Note that in the case of connecting to a server with a bad hostname
    // *and* a non-websocket endpoint at the same time, `webSocket.connect()` will throw this:
    //   com.neovisionaries.ws.client.OpeningHandshakeException:
    //     The status code of the opening handshake response is not '101 Switching Protocols'.
    //     The status line is: HTTP/1.1 404 Not Found
    // We would prefer to throw `SSLPeerUnverifiedException`, but making this happen might require
    // forking the library. The above error is also correct, so this is not a big issue.
    //
    // See https://developer.android.com/reference/javax/net/ssl/SSLSocket#getSession()
    init {
        val uri = URI(
            if (sslAxolotl == null) "ws" else "wss",  // scheme
            null,       // userInfo
            hostname,   // host
            port,       // port
            "/$path",   // path
            null,       // query
            null        // fragment
        )

        webSocket = WebSocketFactory()
                .setSSLSocketFactory(sslAxolotl?.sslSocketFactory)
                .setVerifyHostname(false)
                .setConnectionTimeout(RelayConnection.CONNECTION_TIMEOUT)
                .setServerName(hostname)  // for SNI
                .createSocket(uri)

        webSocket.addListener(Listener())
    }


    @Throws(IOException::class, WebSocketException::class)
    override fun connect(): IConnection.Streams {
        val inputStream = PipedInputStream()
        pipedOutputStream.connect(inputStream)

        if (sslAxolotl == null) {
            webSocket.connect()
        } else {
            sslAxolotl.wrapExceptions {
                webSocket.connect()
                Utils.verifyHostname(sslAxolotl.hostnameVerifier,
                                     webSocket.socket as SSLSocket, hostname)
            }
        }

        return IConnection.Streams(inputStream, null)
    }

    @Throws(IOException::class)
    override fun disconnect() {
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

        @Throws(IOException::class)
        override fun onDisconnected(websocket: WebSocket, serverCloseFrame: WebSocketFrame,
                                    clientCloseFrame: WebSocketFrame, closedByServer: Boolean) {
            logger.trace("onDisconnected(closedByServer={})", closedByServer)
            pipedOutputStream.close()
        }

        @Throws(Exception::class) override fun onBinaryMessage(websocket: WebSocket, binary: ByteArray) {
            logger.trace("onBinaryMessage(size={})", binary.size)
            pipedOutputStream.write(binary)
            pipedOutputStream.flush()  // much faster with this
        }

        override fun onError(websocket: WebSocket, cause: WebSocketException) {
            logger.error("onError()", cause)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("WebSocketConnection")
    }
}