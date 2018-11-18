package com.ubergeek42.weechat.relay.connection;


import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.protocol.Info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertTrue;

public class RelayConnection {
    protected static Logger logger = LoggerFactory.getLogger("RelayConnection");

    final private static String EOF = "\0";
    final private static String ID_VERSION = "version";
    final private static String ID_LIST_BUFFERS = "listbuffers";

    public enum STATE {
        UNKNOWN,
        CONNECTING,
        CONNECTED,
        AUTHENTICATED,
        BUFFERS_LISTED,
        DISCONNECTED;

        // the state can only advance sequentially, except for DISCONNECTED—that can be set at any
        // time after the connection attempt has been made
        boolean nextStateValid(STATE next) {
            return next.ordinal() - this.ordinal() == 1 || (next == DISCONNECTED && this != UNKNOWN);
        }
    }

    private static int iterationCounter = 0;

    final private IConnection connection;
    final private String password;
    final private IObserver observer;
    final private int iteration;

    private IConnection.Streams streams;

    private volatile STATE state = STATE.UNKNOWN;

    public RelayConnection(IConnection connection, String password, IObserver observer) {
        this.connection = connection;
        this.password = password;
        this.observer = observer;
        iteration = iterationCounter++;
    }

    public void sendMessage(String string) {
        if (!string.endsWith("\n")) string += "\n";
        if (connection instanceof WebSocketConnection) {
            ((WebSocketConnection) connection).sendMessage(string);
        } else {
            outbox.add(string);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // print stacktrace manually as android logger omits UnknownHostException tracebacks
    // https://stackoverflow.com/questions/28897239/log-e-does-not-print-the-stack-trace-of-unknownhostexception
    public synchronized void connect() {
        logger.trace("connect()");
        setState(STATE.CONNECTING);

        new FriendlyThread("c", () -> {
            try {
                streams = connection.connect();
            } catch (Exception e) {
                if (state != STATE.DISCONNECTED) {
                    logger.error("connect(): exception while state == " + state);
                    e.printStackTrace();
                    observer.onException(e);
                }
                disconnect();
                return;
            }
            synchronized (RelayConnection.this) {
                if (state == STATE.DISCONNECTED) {disconnect(); return;}   // just in case
                setState(STATE.CONNECTED);
                startReader();
                if (streams.outputStream != null) startWriter();
                authenticate();
            }
        }).start();
    }

    public synchronized void disconnect() {
        logger.trace("disconnect()");

        if (state != STATE.DISCONNECTED) setState(STATE.DISCONNECTED);

        try {
            if (outbox != null) outbox.add(EOF);
            connection.disconnect();
        } catch (IOException e) {
            logger.warn("disconnect(): error while disconnecting", e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void startReader() {
        new FriendlyThread("r", () -> {
            try {
                //noinspection InfiniteLoopStatement
                while (true) onMessage(Utils.getRelayMessage(streams.inputStream));
            } catch (IOException e) {
                if (state == STATE.DISCONNECTED) return;
                logger.error("reader: exception while state == " + state, e);
                observer.onException(e);
            } finally {
                disconnect();
            }
        }).start();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private LinkedBlockingQueue<String> outbox;
    private void startWriter() {
        outbox = new LinkedBlockingQueue<>();
        new FriendlyThread("w", () -> {
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    String message = outbox.take();
                    if (EOF.equals(message)) throw new InterruptedException();
                    streams.outputStream.write(message.getBytes());
                }
            } catch (InterruptedException | IOException e) {
                if (state == STATE.DISCONNECTED) return;
                logger.error("writer: exception while state == " + state, e);
            } finally {
                disconnect();
            }
        }).start();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class FriendlyThread extends Thread {
        final private Runnable runnable;

        FriendlyThread(String tag, Runnable runnable) {
            this.runnable = runnable;
            setName(tag + "-" + iteration);
        }

        @Override public void run() {
            logger.trace("hi");
            runnable.run();
            logger.trace("bye");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // code that sets state better be synchronized on way or another
    private void setState(STATE state) {
        logger.trace("setState({})", state);
        assertTrue(this.state.nextStateValid(state));
        this.state = state;
        observer.onStateChanged(state);
    }

    private void authenticate() {
        String password = this.password.replace(",", "\\,");
        sendMessage(String.format("init password=%s,compression=zlib\n" +
                "(%s) info version_number", password, ID_VERSION));
    }

    synchronized private void onMessage(RelayMessage message) {
        logger.trace("onMessage(id={})", message.getID());
        if (state == STATE.DISCONNECTED) return;

        if (ID_VERSION.equals(message.getID())) {
            Long version = Long.parseLong(((Info) message.getObjects()[0]).getValue());
            logger.info("WeeChat version: {}", String.format("0x%x", version));
            setState(STATE.AUTHENTICATED);
        }

        observer.onMessage(message);

        // ID_LIST_BUFFERS must get requested after onAuthenticated() (BufferList does that)
        if (ID_LIST_BUFFERS.equals(message.getID())) setState(STATE.BUFFERS_LISTED);
    }
}
