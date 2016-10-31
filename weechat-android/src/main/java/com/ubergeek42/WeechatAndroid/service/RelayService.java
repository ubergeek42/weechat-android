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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.connection.AbstractConnection.StreamClosed;
import com.ubergeek42.weechat.relay.connection.Connection;
import com.ubergeek42.weechat.relay.connection.PlainConnection;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.SSLConnection;
import com.ubergeek42.weechat.relay.connection.WebSocketConnection;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.UnresolvedAddressException;
import java.util.EnumSet;

import de.greenrobot.event.EventBus;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class RelayService extends Service implements Connection.Observer {

    private static Logger logger = LoggerFactory.getLogger("RelayService");
    final private static boolean DEBUG = true;
    final private static boolean DEBUG_CONNECTION = true;

    public RelayConnection connection;
    private Connectivity connectivity;
    private PingActionReceiver ping;
    private Handler thandler;               // thread "doge" used for connecting/disconnecting

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// status & life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate() {
        if (DEBUG) logger.debug("onCreate()");
        super.onCreate();

        // prepare handler that will run on a separate thread
        HandlerThread handlerThread = new HandlerThread("doge");
        handlerThread.start();
        thandler = new Handler(handlerThread.getLooper());

        connectivity = new Connectivity();
        connectivity.register(this);

        ping = new PingActionReceiver(this);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) logger.debug("onDestroy()");
        P.saveStuff();
        connectivity.unregister();
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressWarnings("unused")
    public void onEvent(SendMessageEvent event) {
        logger.debug("onEvent({})", event);
        connection.sendMessage(event.message);
    }

    final public static String ACTION_START = "com.ubergeek42.WeechatAndroid.START";
    final public static String ACTION_STOP = "com.ubergeek42.WeechatAndroid.STOP";

    // this method is called:
    //     * whenever app calls startService() (that means on each screen rotate)
    //     * when service is recreated by system after OOM kill. (intent = null)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG_CONNECTION) logger.debug("onStartCommand({}, {}, {})", intent, flags, startId);
        if (intent == null || ACTION_START.equals(intent.getAction())) {
            if (state.contains(STATE.STOPPED)) {
                P.loadConnectionPreferences();
                start();
            }
        } else if (ACTION_STOP.equals(intent.getAction())) {
            if (!state.contains(STATE.STOPPED)) {
                stop();
            }
        }
        return START_STICKY;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// connect/disconnect
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final long WAIT_BEFORE_WAIT_MESSAGE_DELAY = 5;
    private static final long DELAYS[] = new long[] {5, 15, 30, 60, 120, 300, 600, 900};

    // called by user and when disconnected
    private void start() {
        if (DEBUG_CONNECTION) logger.debug("start()");
        if (!state.contains(STATE.STOPPED)) {
            logger.error("start() run while state != STATE.STOPPED");
            return;
        }

        state = EnumSet.of(STATE.STARTED);
        EventBus.getDefault().postSticky(new StateChangedEvent(state));
        P.setServiceAlive(true);
        _start();
    }

    // called by ↑ and Connectivity
    protected void _start() {
        if (DEBUG_CONNECTION) logger.debug("_start()");
        thandler.removeCallbacksAndMessages(null);
        thandler.post(new Runnable() {
            int reconnects = 0;

            final String ticker = getString(R.string.notification_connecting);
            final String content = getString(R.string.notification_connecting_details);
            final String contentNow = getString(R.string.notification_connecting_details_now);

            Runnable connectRunner = new Runnable() {
                @Override public void run() {
                    if (state.contains(STATE.AUTHENTICATED)) return;
                    if (DEBUG_CONNECTION) logger.debug("start(): not connected; connecting now");
                    Notificator.showMain(RelayService.this, String.format(ticker, P.printableHost), contentNow, null);
                    switch (connect()) {
                        case LATER: return; // wait for Connectivity
                        case IMPOSSIBLE: stop(); break; // can't connect due to ?!?!
                        case POSSIBLE: thandler.postDelayed(notifyRunner, WAIT_BEFORE_WAIT_MESSAGE_DELAY * 1000);
                    }
                }
            };

            Runnable notifyRunner = new Runnable() {
                @Override public void run() {
                    if (state.contains(STATE.AUTHENTICATED)) return;
                    long delay = DELAYS[reconnects < DELAYS.length ? reconnects : DELAYS.length - 1];
                    if (DEBUG_CONNECTION) logger.debug("start(): waiting {} seconds", delay);
                    Notificator.showMain(RelayService.this, String.format(ticker, P.printableHost), String.format(content, delay), null);
                    reconnects++;
                    thandler.postDelayed(connectRunner, delay * 1000);
                }
            };

            @Override public void run() {
                connectRunner.run();
            }
        });
    }

    // called by user and when there was a fatal exception while trying to connect
    protected void stop() {
        if (DEBUG_CONNECTION) logger.debug("stop()");
        if (state.contains(STATE.STOPPED)) {
            logger.error("stop() run while state == STATE.STOPPED");
            return;
        }

        if (state.contains(STATE.AUTHENTICATED)) goodbye();

        state = EnumSet.of(STATE.STOPPED);
        EventBus.getDefault().postSticky(new StateChangedEvent(state));
        interrupt();
        stopSelf();
        P.setServiceAlive(false);
    }

    // called by ↑ and PingActionReceiver
    // close whatever connection we have in a thread, may result in a call to onStateChanged
    protected void interrupt() {
        thandler.removeCallbacksAndMessages(null);
        thandler.post(new Runnable() {
            @Override public void run() {
                if (connection != null) connection.disconnect();
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private enum TRY {POSSIBLE, LATER, IMPOSSIBLE}

    private TRY connect() {
        if (DEBUG_CONNECTION) logger.debug("connect()");

        if (connection != null)
            connection.disconnect();

        if (!connectivity.isNetworkAvailable()) {
            Notificator.showMain(this, getString(R.string.notification_waiting_network), null);
            return TRY.LATER;
        }

        Connection conn;
        try {
            switch (P.connectionType) {
                case PREF_TYPE_SSH: conn = new SSHConnection(P.host, P.port, P.sshHost, P.sshPort, P.sshUser, P.sshPass, P.sshKey, P.sshKnownHosts); break;
                case PREF_TYPE_SSL: conn = new SSLConnection(P.host, P.port, P.sslSocketFactory); break;
                case PREF_TYPE_WEBSOCKET: conn = new WebSocketConnection(P.host, P.port, P.wsPath, null); break;
                case PREF_TYPE_WEBSOCKET_SSL: conn = new WebSocketConnection(P.host, P.port, P.wsPath, P.sslContext); break;
                default: conn = new PlainConnection(P.host, P.port); break;
            }
        } catch (Exception e) {
            logger.error("connect(): exception while creating connection", e);
            onException(e);
            return TRY.IMPOSSIBLE;
        }

        connection = new RelayConnection(conn, P.pass);
        connection.setObserver(this);
        connection.connect();
        return TRY.POSSIBLE;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// callbacks
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public enum STATE {
        STOPPED,
        STARTED,
        AUTHENTICATED,
        LISTED,
    }

    public EnumSet<STATE> state = EnumSet.of(STATE.STOPPED);

    @Override public void onStateChanged(Connection.STATE s) {
        logger.debug("onStateChanged({})", s);
        switch (s) {
            case CONNECTING:
            case CONNECTED:
                return;
            case AUTHENTICATED:
                state = EnumSet.of(STATE.STARTED, STATE.AUTHENTICATED);
                Notificator.showMain(this, getString(R.string.notification_connected_to, P.printableHost), null);
                hello();
                break;
            case BUFFERS_LISTED:
                state = EnumSet.of(STATE.STARTED, STATE.AUTHENTICATED, STATE.LISTED);
                break;
            case DISCONNECTED:
                if (state.contains(STATE.STOPPED)) return;
                if (!state.contains(STATE.AUTHENTICATED)) return; // continue connecting
                state = EnumSet.of(STATE.STOPPED);
                goodbye();
                if (P.reconnect) start();
                else stopSelf();
        }
        EventBus.getDefault().postSticky(new StateChangedEvent(state));
    }

    private void hello() {
        ping.scheduleFirstPing();
        BufferList.launch(this);
        SyncAlarmReceiver.start(this);
    }

    private void goodbye() {
        SyncAlarmReceiver.stop(this);
        BufferList.stop();
        ping.unschedulePing();
        //P.saveStuff();
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ExceptionWrapper extends Exception {
        public ExceptionWrapper(Exception cause, String message) {
            super(message);
            initCause(cause);
        }
    }

    // ALWAYS followed by onStateChanged(STATE.DISCONNECTED); might be StreamClosed
    @Override public void onException(Exception e) {
        logger.error("onException({})", e.getClass().getSimpleName());
        if (e instanceof StreamClosed && (!state.contains(STATE.AUTHENTICATED)))
            e = new ExceptionWrapper(e, getString(R.string.relay_error_server_closed));
        else if (e instanceof UnresolvedAddressException)
            e = new ExceptionWrapper(e, getString(R.string.relay_error_resolve, P.connectionType.equals(PREF_TYPE_SSH) ? P.sshHost : P.host));
        EventBus.getDefault().post(new ExceptionEvent(e));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static RelayObject[] NULL = {null};

    @Override public void onMessage(RelayMessage message) {
        ping.onMessage();
        RelayObject[] objects = message.getObjects() == null ? NULL : message.getObjects();
        String id = message.getID();
        for (RelayObject object : objects)
            BufferList.handleMessage(object, id);
    }
}
