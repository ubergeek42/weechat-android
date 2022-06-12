package com.ubergeek42.weechat.relay.connection;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

import static com.ubergeek42.weechat.relay.connection.RelayConnection.CONNECTION_TIMEOUT;

public class WebSocketConnection implements IConnection {
    final private static Logger logger = LoggerFactory.getLogger("WebSocketConnection");

    final private HostnameVerifier verifier;
    final private String hostname;
    final private WebSocket webSocket;
    final private PipedOutputStream pipedOutputStream;

    public WebSocketConnection(String hostname, int port, String path, SSLSocketFactory sslSocketFactory,
                               HostnameVerifier verifier) throws URISyntaxException, IOException {
        URI uri = new URI(sslSocketFactory == null ? "ws" : "wss", null, hostname, port, "/" + path, null, null);
        this.verifier = verifier;
        this.hostname = hostname;
        webSocket = new WebSocketFactory()
                .setSSLSocketFactory(sslSocketFactory)
                .setVerifyHostname(false)
                .setConnectionTimeout(CONNECTION_TIMEOUT)
                .createSocket(uri);
        webSocket.addListener(new Listener());
        pipedOutputStream = new PipedOutputStream();
    }


    @Override public Streams connect() throws IOException, WebSocketException {
        PipedInputStream inputStream = new PipedInputStream();
        pipedOutputStream.connect(inputStream);

        webSocket.connect();
        Utils.verifyHostname(verifier, webSocket.getSocket(), hostname);

        return new Streams(inputStream, null);
    }

    @Override public void disconnect() throws IOException {
            webSocket.disconnect();
            pipedOutputStream.close();
    }

    void sendMessage(String string) {
        webSocket.sendText(string);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class Listener extends WebSocketAdapter {
        @Override public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
            logger.trace("onConnected()");
        }

        @Override public void onConnectError(WebSocket websocket, WebSocketException exception) {
            logger.error("onConnectError({})", exception);
        }

        @Override public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws IOException {
            logger.trace("onDisconnected(closedByServer={})", closedByServer);
            pipedOutputStream.close();
        }

        @Override public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
            logger.trace("onBinaryMessage(size={})", binary.length);
            pipedOutputStream.write(binary);
            pipedOutputStream.flush();        // much faster with this
        }

        @Override public void onError(WebSocket websocket, WebSocketException cause){
            logger.error("onError()", cause);
        }
    }
}
