/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ubergeek42.weechat.relay.connection;

import com.jcraft.jsch.SocketFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

class SocketChannelFactory implements SocketFactory {

    private SocketChannel channel;

    // JSch doesn't expose the cause of exceptions raised by createSocket.
    // Throw a RuntimeException so we know if we were interrupted or if
    // there was some other connection failure.
    @Override public Socket createSocket(String host, int port) throws IOException {
        try {
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(host, port));
            return channel.socket();
        } catch (ClosedByInterruptException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public InputStream getInputStream(Socket socket) throws IOException {
        return socket.getInputStream();
    }

    // you might ask, why on earth don't you just do socket.getOutputStream()?
    // well that's because somewhere deep in the java it's been decided that in some cases
    // there shall be a deadlock when you try to concurrently read and write both streams
    // WARNING: this method assumes that createSocket() is called before it
    @Override public OutputStream getOutputStream(Socket socket) throws IOException {
        return Channels.newOutputStream(new WritableByteChannel() {
            @Override public int write(ByteBuffer byteBuffer) throws IOException {
                return channel.write(byteBuffer);
            }

            @Override public boolean isOpen() {
                return channel.isOpen();
            }

            @Override public void close() throws IOException {
                channel.close();
            }
        });
    }
}
