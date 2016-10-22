/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;

public class WebSocketConnection extends AbstractConnection {
    protected static Logger logger = LoggerFactory.getLogger("WebSocketConnection");

    private WebSocketClient client;
    private PipedOutputStream outputToInStream;

    public WebSocketConnection(String server, int port, String path, SSLContext sslContext) throws URISyntaxException, IOException {
        // can throw URISyntaxException
        URI uri = new URI(sslContext == null ? "ws" : "wss", null, server, port, "/" + path, null, null);

        // can throw IOException
        in = new PipedInputStream();
        outputToInStream = new PipedOutputStream();
        outputToInStream.connect((PipedInputStream) in);

        client = new MyWebSocket(uri, new Draft_17());
        if (sslContext != null) client.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(sslContext));
    }

    @Override protected void doConnect() throws Exception {
        if (!client.connectBlocking()) throw (exception != null) ?
                exception : new Exception("Could not connect using WebSocket");
    }

    // now this is one shady method
    // client.close() does not close the socket. see TooTallNate/Java-WebSocket#346
    // getConnection().closeConnection() seems to work, but i don't know if using it is right
    @Override protected void doDisconnect() {
        super.doDisconnect();
        client.close();
        client.getConnection().closeConnection(1000, "force closing");
        try{outputToInStream.close();} catch (Exception e) {e.printStackTrace();}
    }

    // we don't need writer
    @Override protected void startWriter(int i) {}

    // because we have our own sendMessage()
    @Override public void sendMessage(String string) {
        try {client.send(string.getBytes());}
        catch (WebsocketNotConnectedException ignored) {}
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private Exception exception = null;

    private class MyWebSocket extends WebSocketClient {
        public MyWebSocket(URI serverUri, Draft draft) {
            super(serverUri, draft);
        }

        @Override public void onOpen(ServerHandshake ignored) {
            logger.debug("WebSocket.onOpen(), readyState = {}", this.getReadyState());
        }

        @Override public void onMessage(String message) {
            logger.debug("WebSocket.onMessage(string = {})", message);
            throw new RuntimeException("Unexpected string message from websocket");
        }

        @Override public void onMessage(ByteBuffer bytes) {
            logger.debug("WebSocket.onMessage({} bytes)", bytes.array().length);
            try {
                outputToInStream.write(bytes.array());
                outputToInStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override public void onClose(int code, String reason, boolean remote) {
            logger.debug("WebSocket.onClose(code = {}, reason = {})", code, reason);
            try {outputToInStream.close();} catch (IOException e) {e.printStackTrace();}
        }

        // when connecting via SSL and when the connection is abruptly closed,
        // onError() with `java.lang.NullPointerException: ssl == null` is thrown
        @Override public void onError(Exception e) {
            logger.error("WebSocket.onError({}: {})", e.getClass().getSimpleName(), e.getMessage());
            exception = e;
            try {outputToInStream.close();} catch (IOException ignored) {}
        }
    }
}
