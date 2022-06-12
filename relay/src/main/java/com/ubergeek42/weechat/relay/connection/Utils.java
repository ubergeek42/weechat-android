package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.protocol.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public class Utils {

    public static class StreamClosed extends IOException {
        private StreamClosed() {
            super("Stream unexpectedly closed");
        }
    }

    private static class ProtocolError extends IOException {
        private ProtocolError(String s, Throwable throwable) {
            super(s, throwable);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // WARNING: If the handshake was not performed, `getSession()` will perform it, but
    // it does *not* throw any exceptions! As per the doc,
    //   > If an error occurs during the initial handshake, this method returns an invalid
    //   > session object which reports an invalid cipher suite of "SSL_NULL_WITH_NULL_NULL".
    // While the subsequent call to `verifier.verify()` will not be able to verify the host,
    // the only indication of an error that we get is the false result of the call.
    //
    // So, before calling this method, make sure that the handshake has been performed already!
    static void verifyHostname(HostnameVerifier verifier, SSLSocket socket, String hostname)
            throws SSLPeerUnverifiedException {
        if (!verifier.verify(hostname, socket.getSession())) {
            throw new SSLPeerUnverifiedException("Cannot verify hostname: " + hostname);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static int HEADER_LENGTH = 4;
    private final static int MAX_MESSAGE_SIZE = 5 * 1024 * 1024;    // 5 MiB

    // todo better handling of exceptions
    static RelayMessage getRelayMessage(InputStream stream) throws IOException {
        byte[] data = new byte[HEADER_LENGTH];

        readAll(stream, data, 0);                                   // throws IOException, StreamClosed
        int messageSize = new Data(data).getUnsignedInt();          // can throw but won't

        if (messageSize > MAX_MESSAGE_SIZE)
            throw new ProtocolError("Protocol error",  new NumberFormatException(
                    "Server is attempting to send a message of size " + messageSize + " bytes"));

        data = enlarge(data, messageSize);
        readAll(stream, data, HEADER_LENGTH);                       // throws IOException, StreamClosed

        RelayMessage message;
        try {message = new RelayMessage(data);}
        catch (Exception e) {throw new ProtocolError("Error while parsing message", e);}

        return message;
    }

    private static void readAll(InputStream stream, byte[] data, int startAt) throws IOException {
        for (int pos = startAt; pos != data.length;) {
            int read = stream.read(data, pos, data.length - pos);
            if (read == -1) throw new StreamClosed();
            pos += read;
        }
    }

    private static byte[] enlarge(byte[] in, int size) {
        byte[] out = new byte[size];
        System.arraycopy(in, 0, out, 0, in.length);
        return out;
    }

    static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    static class FriendlyThread extends Thread {
        final private Logger logger;
        final private Runnable runnable;

        FriendlyThread(String loggerName, int iteration, Runnable runnable) {
            logger = LoggerFactory.getLogger(loggerName);
            setName(Character.toLowerCase(loggerName.charAt(0)) + "-" + iteration);
            this.runnable = runnable;
        }

        @Override public void run() {
            logger.trace("hi");
            runnable.run();
            logger.trace("bye");
        }
    }
}
