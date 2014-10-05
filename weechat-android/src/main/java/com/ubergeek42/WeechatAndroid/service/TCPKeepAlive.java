/*******************************************************************************
 * Copyright 2014 Simon Arlott
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid.service;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.FileDescriptor;
import java.net.Socket;
import java.net.SocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TCPKeepAlive {
    private static final Logger logger = LoggerFactory.getLogger("KeepAlive");

    /* These definitions are only valid on Linux */
    private static final int IPPROTO_TCP = 6;
    private static final int TCP_KEEPIDLE = 4;
    private static final int TCP_KEEPINTVL = 5;
    private static final int TCP_KEEPCNT = 6;

    private static Object os;
    private static Method setsockoptInt;

    static {
        try {
            String osName =  System.getProperty("os.name");
            if (osName.equals("Linux")) {
               Class libcore = Class.forName("libcore.io.Libcore");
               Field osField = libcore.getDeclaredField("os");
               osField.setAccessible(true);

               os = osField.get(null);
               setsockoptInt = osField.getType().getDeclaredMethod("setsockoptInt", FileDescriptor.class, int.class, int.class, int.class);
               setsockoptInt.setAccessible(true);
               logger.info("TCP keepalive available");
            } else {
                logger.warn("OS={}", osName);
            }
        } catch (Throwable t) {
            logger.warn("Unable to get setsockoptInt()", t);
        }
    }

    static boolean configureKeepAlive(Socket s, int idle, int interval, int count) {
        try {
            s.setKeepAlive(true);
        } catch (SocketException e) {
            logger.error("Unable to enable TCP keepalive on socket {}", s, e);
            return false;
        }

        if (setsockoptInt == null)
            return false;

        FileDescriptor fd = getFileDescriptor(s);
        if (fd == null) {
            logger.error("No FileDescriptor for socket {}", s);
            return false;
        }

        try {
            setsockoptInt.invoke(os, fd, IPPROTO_TCP, TCP_KEEPIDLE, idle);
            setsockoptInt.invoke(os, fd, IPPROTO_TCP, TCP_KEEPINTVL, interval);
            setsockoptInt.invoke(os, fd, IPPROTO_TCP, TCP_KEEPCNT, count);
            logger.info("Configured TCP keepalive on socket {}", s);
            return true;
        } catch (Throwable t) {
            logger.error("Unable to configure TCP keepalive on socket {}", s, t);
            return false;
        }
    }

    private static FileDescriptor getFileDescriptor(Socket s) {
        try {
            Method getFileDescriptor = s.getClass().getDeclaredMethod("getFileDescriptor$");
            getFileDescriptor.setAccessible(true);
            return (FileDescriptor)getFileDescriptor.invoke(s);
        } catch (Throwable t) {
            logger.warn("Unable to get FileDescriptor using getFileDescriptor$()", t);
        }

        logger.warn("Unable to get FileDescriptor for {}", s.getClass());
        return null;
    }
}
