/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.protocol.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;

public abstract class AbstractConnection implements Connection {
    protected final static boolean DEBUG = false;
    protected static Logger logger = LoggerFactory.getLogger("AbstractConnection");

    private static int iteration = 0;

    private volatile STATE state = STATE.UNKNOWN;
    private Observer observer = null;

    protected OutputStream out = null;
    protected InputStream in = null;

    protected Thread connector = null;
    protected Thread reader = null;
    protected Thread writer = null;

    //////////////////////////////////////////////////////////////////////////////////////////////// interface

    @Override public STATE getState() {
        return state;
    }

    @Override public void connect() {
        assertEquals(state, STATE.UNKNOWN);
        assertNull(connector);

        final int i = AbstractConnection.iteration++;

        state = STATE.CONNECTING;
        observer.onStateChanged(STATE.CONNECTING);

        connector = new Thread(new Runnable() {@Override public void run() {hi(); connectOnce(i); bye();}});
        connector.setName("con" + i);
        connector.start();
    }

    @Override synchronized public void disconnect() {
        logger.warn("disconnect()");
        assertNotEquals(state, STATE.UNKNOWN);
        assertNotNull(connector);

        if (state == STATE.DISCONNECTED) return;
        state = STATE.DISCONNECTED;
        observer.onStateChanged(STATE.DISCONNECTED);
        doDisconnect();
    }

    @Override public void setObserver(Observer observer) {
        this.observer = observer;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// override me!

    protected abstract void doConnect() throws Exception;

    protected void doDisconnect() {
        connector.interrupt();
        if (reader != null) reader.interrupt();
        if (writer != null) writer.interrupt();
    }

    protected void startReader(final int i) {
        reader = new Thread(new Runnable() {@Override public void run() {hi(); readLoop(); bye();}});
        reader.setName("r" + i);
        reader.start();
    }

    protected void startWriter(final int i) {
        writer = new Thread(new Runnable() {@Override public void run() {hi(); writeLoop(); bye();}});
        writer.setName("wr" + i);
        writer.start();
    }

    @Override public void sendMessage(String string) {
        outbox.add(string);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// connect

    private void connectOnce(final int i) {
        try {
            doConnect();
            state = STATE.CONNECTED;
        } catch (Exception e) {
            if (state != STATE.DISCONNECTED) {
                e.printStackTrace();
                observer.onException(e);
            }
            disconnect();
            return;
        }
        observer.onStateChanged(STATE.CONNECTED);
        startReader(i);
        startWriter(i);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// send

    private LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private void writeLoop() {
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                assert state == STATE.CONNECTED;
                out.write(outbox.take().getBytes());
            }
        } catch (InterruptedException | IOException e) {
            if (state == STATE.DISCONNECTED) return;
            logger.warn("writeLoop(): exception while state == {}", state);
            e.printStackTrace();
        } finally {
            try {out.close();}
            catch (IOException ignored) {}
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// receive

    private final static int HEADER_LENGTH = 4;
    private void readLoop() {
        byte[] data;
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                data = new byte[HEADER_LENGTH];
                readAll(data, 0);
                data = enlarge(data, new Data(data).getUnsignedInt());
                readAll(data, HEADER_LENGTH);
                observer.onMessage(new RelayMessage(data));

            }
        } catch (IOException | StreamClosed e) {
            if (state == STATE.DISCONNECTED) return;
            logger.warn("readLoop(): exception while state == {}", state);
            e.printStackTrace();
            observer.onException(e);
        } finally {
            try {in.close();}
            catch (IOException ignored) {}
            disconnect();
        }
    }

    public static class StreamClosed extends Exception {}
    private void readAll(byte[] data, int startAt) throws IOException, StreamClosed {
        for (int pos = startAt; pos != data.length;) {
            int read = in.read(data, pos, data.length - pos);
            if (read == -1) throw new StreamClosed();
            pos += read;
        }
    }

    private static byte[] enlarge(byte[] in, int size) {
        byte[] out = new byte[size];
        System.arraycopy(in, 0, out, 0, in.length);
        return out;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// receive

    private void hi() {logger.warn("hi");}
    private void bye() {logger.warn("bye");}
}
