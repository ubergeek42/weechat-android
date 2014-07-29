/*******************************************************************************
 * Copyright 2012 Keith Johnson
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

import java.security.cert.X509Certificate;

import android.os.Binder;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functions that are available to clients of the relay service
 * 
 * @author ubergeek42<kj@ubergeek42.com>
 * 
 */
public class RelayServiceBinder extends Binder {
    private static Logger logger = LoggerFactory.getLogger("RelayServiceBinder");
    final private static boolean DEBUG = BuildConfig.DEBUG && true;

    private RelayService service;

    public RelayServiceBinder(RelayService service) {
        this.service = service;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** returns true if connection status corresponds to given connection */
    public boolean isConnection(int status) {
        return service.isConnection(status);
    }

    public boolean connect() {
        return service.connect();
    }

    public void addRelayConnectionHandler(RelayConnectionHandler handler) {
        service.connectionHandlers.add(handler);
    }

    public void removeRelayConnectionHandler(RelayConnectionHandler handler) {
        service.connectionHandlers.remove(handler);
    }

    /** Disconnect from the server and stop the background service */
    public void shutdown() {
        service.shutdown();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public Buffer getBufferByPointer(int id) {
        if (DEBUG) logger.error("getBufferByPointer({})");
        return service.bone.buffer_list.findByPointer(id);
    }

    public BufferList getBuffers() {
        if (DEBUG) logger.error("getBuffers()");
        if (service.bone == null) return null;
        return service.bone.buffer_list;
    }

    /** Send a message to the server(expected to be formatted appropriately) */
    public void sendMessage(String string) {
        service.relayConnection.sendMsg(string);
    }

    public void resetNotifications() {
        service.resetNotification();
    }

    /** subscribes to a buffer. Gets the last MAXLINES of lines, and subscribes to nicklist changes */
    public void subscribeBuffer(int pointer) {
        Buffer buffer = service.bone.buffer_list.findByPointer(pointer);
        if (DEBUG) logger.error("subscribeBuffer({}) ({})", pointer, buffer.full_name);
        if (!buffer.holds_all_lines_it_is_supposed_to_hold) {
            if (DEBUG) logger.error("subscribeBuffer(): requesting all lines");
            service.bone.buffer_list.requestLinesForBufferByPointer(pointer);
        }
//        if (!buffer.holds_all_nicknames) {
//            if (DEBUG) logger.error("subscribeBuffer(): requesting full nicklist");
//            //service.requestNicklist(pointer);
//        }
//        if (service.optimize_traffic) {
//            if (DEBUG) logger.error("subscribeBuffer(): syncing");
//            service.relayConnection.sendMsg("sync " + pointer);
//        }
    }

    public void unsubscribeBuffer(int bufferPointer) {
        if (DEBUG) logger.error("unsubscribeBuffer({})", bufferPointer);
        //if (service.optimize_traffic) service.relayConnection.sendMsg("desync " + bufferPointer);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void setCertificateError(X509Certificate cert) {
        service.untrustedCert = cert;
    }

    public X509Certificate getCertificateError() {
        return service.untrustedCert;
    }

    public void acceptCertificate() {
        service.certmanager.trustCertificate(service.untrustedCert);
    }

    public void rejectCertificate() {
        service.untrustedCert = null;
    }
}
