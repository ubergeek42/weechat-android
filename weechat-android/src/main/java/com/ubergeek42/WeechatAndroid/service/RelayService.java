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

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.connection.AbstractConnection.StreamClosed;
import com.ubergeek42.weechat.relay.connection.Connection;
import com.ubergeek42.weechat.relay.connection.Connection.STATE;
import com.ubergeek42.weechat.relay.connection.PlainConnection;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.SSLConnection;
import com.ubergeek42.weechat.relay.connection.WebSocketConnection;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import de.greenrobot.event.EventBus;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class RelayService extends Service implements Connection.Observer {

    private static Logger logger = LoggerFactory.getLogger("RelayService");
    final private static boolean DEBUG = true;
    final private static boolean DEBUG_CONNECTION = true;

    public Notificator notificator;
    public RelayConnection connection;
    protected SharedPreferences prefs;

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

        P.init(getApplicationContext());

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        notificator = new Notificator(this);

        // prepare handler that will run on a separate thread
        HandlerThread handlerThread = new HandlerThread("doge");
        handlerThread.start();
        thandler = new Handler(handlerThread.getLooper());

        notificator.showMain(null, "Tap to connect", null);

        connectivity = new Connectivity();
        connectivity.register(this);

        ping = new PingActionReceiver(this);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) logger.debug("onDestroy()");
        prefs.edit().remove(PREF_MUST_STAY_DISCONNECTED).apply(); // forget current connection status
        //notificationManger.cancelAll();
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

    private boolean started = false;
    final public static String ACTION_START = "com.ubergeek42.WeechatAndroid.START";
    final public static String ACTION_STOP = "com.ubergeek42.WeechatAndroid.STOP";

    // this method is called:
    //     * whenever app calls startService() (that means on each screen rotate)
    //     * when service is recreated by system after OOM kill. (intent = null)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG_CONNECTION) logger.debug("onStartCommand({}, {}, {})", intent, flags, startId);
        if (intent == null || ACTION_START.equals(intent.getAction())) {
            if (!started) {
                //started = true;
                P.loadConnectionPreferences();
                startThreadedConnectLoop();
            }
        } else if (ACTION_STOP.equals(intent.getAction())) {
            if (started) {
                //started = false;
                startThreadedDisconnect();
            }
        }
        return START_STICKY;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// connect/disconnect
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** only auto-connect if auto-connect is on ON in the prefs and
     ** the user did not disconnect by tapping disconnect in menu */
    public boolean mustAutoConnect() {
         return P.reconnect && !prefs.getBoolean(PREF_MUST_STAY_DISCONNECTED, false);
    }

    private static final long WAIT_BEFORE_WAIT_MESSAGE_DELAY = 5;
    private static final long DELAYS[] = new long[] {5, 15, 30, 60, 120, 300, 600, 900};

    public void startThreadedConnectLoop() {
        if (DEBUG_CONNECTION) logger.debug("startThreadedConnectLoop()");
        if (started) {
            logger.error("startThreadedConnectLoop() run while started = true");
            return;
        }
        started = true;
        prefs.edit().putBoolean(PREF_MUST_STAY_DISCONNECTED, false).apply();
        thandler.removeCallbacksAndMessages(null);
        thandler.post(new Runnable() {
            int reconnects = 0;

            final String ticker = getString(R.string.notification_connecting);
            final String content = getString(R.string.notification_connecting_details);
            final String contentNow = getString(R.string.notification_connecting_details_now);

            Runnable connectRunner = new Runnable() {
                @Override
                public void run() {
                    if (state.contains(STATE.AUTHENTICATED)) return;
                    if (DEBUG_CONNECTION) logger.debug("startThreadedConnectLoop(): not connected; connecting now");
                    //connectionStatus = CONNECTING; TODO???
                    notificator.showMain(String.format(ticker, P.host), contentNow, null);
                    if (connect() == TRY.POSSIBLE)
                        thandler.postDelayed(notifyRunner, WAIT_BEFORE_WAIT_MESSAGE_DELAY * 1000);
                }
            };

            Runnable notifyRunner = new Runnable() {
                @Override
                public void run() {
                    if (state.contains(STATE.AUTHENTICATED)) return;
                    long delay = DELAYS[reconnects < DELAYS.length ? reconnects : DELAYS.length - 1];
                    if (DEBUG_CONNECTION) logger.debug("startThreadedConnectLoop(): waiting {} seconds", delay);
                    notificator.showMain(String.format(ticker, P.host), String.format(content, delay), null);
                    reconnects++;
                    thandler.postDelayed(connectRunner, delay * 1000);
                }
            };

            @Override
            public void run() {
                connectRunner.run();
            }
        });
    }

    // do the actual shutdown on a separate thread (to avoid NetworkOnMainThreadException on Android 3.0+)
    // remember that we are down lest we are reconnected when application
    // kills the service and restores it back later
    public void startThreadedDisconnect(boolean mustStayDisconnected) {
        if (DEBUG_CONNECTION) logger.debug("startThreadedDisconnect()");
        if (!started) {
            logger.error("startThreadedDisconnect() run while started = false");
            return;
        }
        started = false;
        prefs.edit().putBoolean(PREF_MUST_STAY_DISCONNECTED, mustStayDisconnected).apply();
        thandler.removeCallbacksAndMessages(null);
        thandler.post(new Runnable() {
            @Override
            public void run() {
                connection.disconnect();
                notificator.showMain(getString(R.string.notification_disconnected), null);
            }
        });
    }

    public void startThreadedDisconnect() {
        startThreadedDisconnect(true);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////


    private enum TRY {POSSIBLE, IMPOSSIBLE}

    private TRY connect() {
        if (DEBUG_CONNECTION) logger.debug("connect()");

        if (connection != null)
            connection.disconnect();

        if (!connectivity.isNetworkAvailable()) {
            Intent intent = new Intent(this, WeechatActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            notificator.showMain(getString(R.string.notification_network_unavailable_details), getString(R.string.notification_network_unavailable), contentIntent);
            return TRY.IMPOSSIBLE;
        }

        Connection conn;
        try {
            switch (P.connectionType) {
                case PREF_TYPE_SSH: conn = new SSHConnection(P.host, P.port, P.sshHost, P.sshPort, P.sshUser, P.sshPass, P.sshKeyfile); break;
                case PREF_TYPE_SSL: conn = new SSLConnection(P.host, P.port, P.sslContext); break;
                case PREF_TYPE_WEBSOCKET: conn = new WebSocketConnection(P.host, P.port, null); break;
                case PREF_TYPE_WEBSOCKET_SSL: conn = new WebSocketConnection(P.host, P.port, P.sslContext); break;
                default: conn = new PlainConnection(P.host, P.port); break;
            }
        } catch (Exception e) {
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

    public EnumSet<STATE> state = EnumSet.of(STATE.UNKNOWN);

    @Override public void onStateChanged(STATE state) {
        logger.debug("onStateChanged({})", state);
        switch (state) {
            case CONNECTING:
            case CONNECTED:
                this.state = EnumSet.of(state);
                break;
            case AUTHENTICATED:
                this.state = EnumSet.of(STATE.CONNECTED, STATE.AUTHENTICATED);
                notificator.showMain(getString(R.string.notification_connected_to) + P.host, null);

                P.restoreStuff();
                ping.scheduleFirstPing();
                BufferList.launch(this);
                SyncAlarmReceiver.start(this); // schedule synchronizations once 5 minutes todo shut it down?
                connection.sendMessage(P.optimizeTraffic ? "sync * buffers,upgrade" : "sync");
                break;
            case BUFFERS_LISTED:
                this.state = EnumSet.of(STATE.CONNECTED, STATE.AUTHENTICATED, STATE.BUFFERS_LISTED);
                break;
            case DISCONNECTED:
                boolean weWereAuthenticated = this.state.contains(STATE.AUTHENTICATED);
                this.state = EnumSet.of(state);
                if (weWereAuthenticated && started) startThreadedConnectLoop();
                //else notificator.showMain(getString(R.string.notification_disconnected), null);

                BufferList.stop();
                ping.unschedulePing();
                P.saveStuff();
                break;
        }
        EventBus.getDefault().postSticky(new StateChangedEvent(this.state));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class AuthenticationException extends Exception {
        public AuthenticationException(Exception cause, String message) {
            super(message);
            initCause(cause);
        }
    }

    // ALWAYS followed by onStateChanged(STATE.DISCONNECTED); might be StreamClosed
    @Override public void onException(Exception e) {
        logger.error("onException({})", e.getClass().getSimpleName());
        if (e instanceof StreamClosed && (!state.contains(STATE.AUTHENTICATED)))
            e = new AuthenticationException(e, "Server unexpectedly closed connection while connecting. Wrong password or connection type?");
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
