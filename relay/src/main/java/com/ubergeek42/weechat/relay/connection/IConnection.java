package com.ubergeek42.weechat.relay.connection;

import com.neovisionaries.ws.client.WebSocketException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IConnection {

    class Streams {
        final InputStream inputStream;
        final OutputStream outputStream;

        Streams(InputStream inputStream, OutputStream outputStream) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }
    }

    // blocking connect. return 2 streams, output stream is optional
    Streams connect() throws IOException, InterruptedException, WebSocketException;

    // non-blocking disconnect. can be called during the connect() call. the connection is supposed
    // to be closed immediately or in the next few seconds. can be called several times with no harm
    void disconnect() throws IOException;
}
