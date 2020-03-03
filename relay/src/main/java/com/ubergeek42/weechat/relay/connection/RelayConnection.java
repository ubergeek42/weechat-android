package com.ubergeek42.weechat.relay.connection;


import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.protocol.Info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RelayConnection {
    final private static Logger logger = LoggerFactory.getLogger("RelayConnection");

    final static int CONNECTION_TIMEOUT = 5 * 1000;

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

    // control stream performs the actual connection, subsequent thread launching and disconnection
    // event stream pushes all connection events to the app, such as state changes, exceptions and messages
    // writer stream simply writes data to outputStream
    final private Events.EventStream controlStream;
    final private Events.EventStream eventStream;
    final private Events.EventStream writerStream;

    private IConnection.Streams streams;

    private volatile STATE state = STATE.UNKNOWN;

    public RelayConnection(IConnection connection, String password, IObserver observer) {
        this.connection = connection;
        this.password = password;
        this.observer = observer;
        iteration = iterationCounter++;

        controlStream = new Events.EventStream("ControlStream", iteration);
        eventStream = new Events.EventStream("EventStream", iteration);
        writerStream = new Events.EventStream("WriteStream", iteration);
    }

    public void sendMessage(String message) {
        final String string = message.endsWith("\n") ? message : message + "\n";
        if (connection instanceof WebSocketConnection) {
            ((WebSocketConnection) connection).sendMessage(string);
        } else {
            writerStream.post(new Protected("writerStream", () ->
                streams.outputStream.write(string.getBytes())
            ));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // start connecting in another thread. the first posted event will run to completion, but the
    // second part may be canceled by disconnect(). note that android won't print some exceptions
    // https://stackoverflow.com/questions/28897239/log-e-does-not-print-the-stack-trace-of-unknownhostexception
    public synchronized void connect() {
        logger.trace("connect()");
        setState(STATE.CONNECTING);

        controlStream.post(new Protected("controlStream", () -> {
            logger.trace("controlStream → connect");
            streams = connection.connect();
        }), () -> {
            synchronized (RelayConnection.this) {
                if (state == STATE.DISCONNECTED) return;    // can happen very rarely
                logger.trace("controlStream → connected; starting threads");
                setState(STATE.CONNECTED);
                startThreadsAndAuthenticate();
            }
        });
        eventStream.start();
        controlStream.start();
    }

    public synchronized void disconnect() {
        logger.trace("disconnect()");
        if (state == STATE.DISCONNECTED) return;
        setState(STATE.DISCONNECTED);

        controlStream.clearQueueAndClose(() -> {
            logger.trace("controlStream → disconnect");
            try {
                writerStream.close();
                eventStream.close();
                connection.disconnect();
            } catch (IOException e) {
                logger.warn("controlStream: error while disconnecting", e);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void startThreadsAndAuthenticate() {
        new Utils.FriendlyThread("ReadStream", iteration, new Protected("readStream", () -> {
            while (!Thread.interrupted()) onMessage(Utils.getRelayMessage(streams.inputStream));
        })).start();

        if (streams.outputStream != null) writerStream.start();

        String password = this.password.replace(",", "\\,");
        sendMessage(String.format("init password=%s,compression=zlib\n" +
                "(%s) info version_number", password, ID_VERSION));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // code that sets state better be synchronized on way or another
    private void setState(STATE state) {
        logger.trace("setState({})", state);
        if (!this.state.nextStateValid(state)) logger.error("next connection state is not valid: " + state);
        this.state = state;

        if (state == STATE.DISCONNECTED) eventStream.close(() -> observer.onStateChanged(state));
        else eventStream.post(() -> observer.onStateChanged(state));
    }

    synchronized private void onMessage(RelayMessage message) {
        // logger.trace("onMessage(id={})", message.getID());
        if (state == STATE.DISCONNECTED) return;

        if (ID_VERSION.equals(message.getID())) {
            Long version = Long.parseLong(((Info) message.getObjects()[0]).getValue());
            logger.info("WeeChat version: {}", String.format("0x%x", version));
            setState(STATE.AUTHENTICATED);
        }

        eventStream.post(() -> observer.onMessage(message));

        // ID_LIST_BUFFERS must get requested after onAuthenticated() (BufferList does that)
        if (ID_LIST_BUFFERS.equals(message.getID())) setState(STATE.BUFFERS_LISTED);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    class Protected implements Events.Event {
        final private String name;
        final private Events.ThrowingEvent event;

        Protected(String name, Events.ThrowingEvent event) {
            this.name = name;
            this.event = event;
        }

        @Override public void run() {
            try {
                event.run();
            } catch (Exception e) {
                if (state == STATE.DISCONNECTED) return;
                logger.error(name + ": exception while state == " + state, e);
                eventStream.post(() -> observer.onException(e));
                disconnect();
            }
        }
    }
}
