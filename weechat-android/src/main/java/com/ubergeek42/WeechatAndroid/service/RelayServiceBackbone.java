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

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.ubergeek42.WeechatAndroid.BuildConfig;
import com.ubergeek42.WeechatAndroid.PreferencesActivity;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.connection.IConnection;
import com.ubergeek42.weechat.relay.connection.PlainConnection;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.SSLConnection;
import com.ubergeek42.weechat.relay.connection.StunnelConnection;
import com.ubergeek42.weechat.relay.connection.WebSocketConnection;
import com.ubergeek42.weechat.relay.messagehandler.UpgradeHandler;
import com.ubergeek42.weechat.relay.messagehandler.UpgradeObserver;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class RelayServiceBackbone extends Service implements RelayConnectionHandler,
        OnSharedPreferenceChangeListener, UpgradeObserver {

    private static Logger logger = LoggerFactory.getLogger("RelayServiceBackbone");
    final private static boolean DEBUG = false;
    final private static boolean DEBUG_CONNECTION = false;
    final private static boolean DEBUG_NOTIFICATIONS = false;

    private static final int NOTIFICATION_ID = 42;
    private static final int NOTIFICATION_HIGHLIGHT_ID = 43;
    private NotificationManager notificationManger;
    private Thread upgrading;

    final static private String PREF_AUTO_CONNECT = "autoconnect";

    final static private String PREF_HOST = "host";
    final static private String PREF_PASSWORD = "password";
    final static private String PREF_PORT = "port";

    final static private String PREF_PING_ENABLED = "ping_enabled";
    final static private String PREF_PING_IDLE = "ping_idle";
    final static private String PREF_PING_TIMEOUT = "ping_timeout";

    final static private String PREF_STUNNEL_CERT = "stunnel_cert";
    final static private String PREF_STUNNEL_PASS = "stunnel_pass";
    final static private String PREF_SSH_HOST = "ssh_host";
    final static private String PREF_SSH_PORT = "ssh_port";
    final static private String PREF_SSH_USER = "ssh_user";
    final static private String PREF_SSH_PASS = "ssh_pass";
    final static private String PREF_SSH_KEYFILE = "ssh_keyfile";

    final static private String PREF_CONNECTION_TYPE = "connection_type";
    final static private String PREF_TYPE_SSH = "ssh";
    final static private String PREF_TYPE_STUNNEL = "stunnel";
    final static private String PREF_TYPE_SSL = "ssl";
    final static private String PREF_TYPE_WEBSOCKET = "websocket";
    final static private String PREF_TYPE_WEBSOCKET_SSL = "websocket-ssl";
    final static private String PREF_TYPE_PLAIN = "plain";

    final static private String PREF_NOTIFICATION_ENABLE = "notification_enable";
    final static private String PREF_NOTIFICATION_SOUND = "notification_sound";
    final static private String PREF_NOTIFICATION_LIGHT = "notification_light";
    final static private String PREF_NOTIFICATION_VIBRATE = "notification_vibrate";
    final static private String PREF_NOTIFICATION_TICKER = "notification_ticker";

    final static private String PREF_MUST_STAY_DISCONNECTED = "wow!";

    private String host;
    private int port;
    private String pass;

    RelayConnection connection;
    HashSet<RelayConnectionHandler> connectionHandlers = new HashSet<>();

    SharedPreferences prefs;
    SSLHandler certmanager;
    X509Certificate untrustedCert;
    private AlarmManager alarmMgr;

    int hotCount = 0;
    volatile long lastMessageReceivedAt = 0;

    /** mainly used to tell the user if we are reconnected */
    private volatile boolean disconnected;
    private boolean alreadyHadIntent;
    private volatile boolean networkUnavailable;

    /** handler that resides on a separate thread. useful for connection/etc */
    Handler thandler;

    // for some reason, this java can't have binary literals...
    public final static int DISCONNECTED =   Integer.parseInt("00001", 2);
    public final static int CONNECTING =     Integer.parseInt("00010", 2);
    public final static int CONNECTED =      Integer.parseInt("00100", 2);
    public final static int AUTHENTICATED =  Integer.parseInt("01000", 2);
    public final static int BUFFERS_LISTED = Integer.parseInt("10000", 2);
    int connectionStatus = DISCONNECTED;

    private BroadcastReceiver connectivityActionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            NetworkInfo networkInfo = connectivityManager().getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                if (networkUnavailable && mustAutoConnect()) startThreadedConnectLoop(true);
                networkUnavailable = false;
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// status & life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** check status of connection
     ** @param status one of DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATED, BUFFERS_LISTED
     ** @return true if connection corresponds to one of these */
    public boolean isConnection(int status) {
        return (connectionStatus & status) != 0;
    }

    @Override
    public void onCreate() {
        if (DEBUG) logger.debug("onCreate()");
        super.onCreate();

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        notificationManger = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // prepare handler that will run on a separate thread
        HandlerThread handlerThread = new HandlerThread("doge");
        handlerThread.start();
        thandler = new Handler(handlerThread.getLooper());

        startForeground(NOTIFICATION_ID, buildNotification(null, "Tap to connect", null));

        disconnected = false;
        alreadyHadIntent = false;
        networkUnavailable = false;

        // Prepare for dealing with SSL certs
        certmanager = new SSLHandler(new File(getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks"));

        registerReceiver(
                connectivityActionReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        );

        alarmMgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) logger.debug("onDestroy()");
        prefs.edit().remove(PREF_MUST_STAY_DISCONNECTED).commit(); // forget current connection status
        notificationManger.cancelAll();
        unregisterReceiver(connectivityActionReceiver);
        super.onDestroy();
    }

    /** this method is called:
     **     * whenever app calls startService() (that means on each screen rotate)
     **     * when service is recreated by system after OOM kill. in this case,
     **       the intent is 'null' (and we can say we are 're'connecting.
     ** we are using this method because it's the only way to know if we are returning from OOM kill.
     ** but we want to only run this ONCE after onCreate*/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG_CONNECTION) logger.debug("onStartCommand({}, {}, {}); had intent? {}", intent, flags, startId, alreadyHadIntent);
        if (!alreadyHadIntent) {
            if (mustAutoConnect()) startThreadedConnectLoop(intent == null);
            alreadyHadIntent = true;
        }
        return START_STICKY;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// notifications!
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** build notification without displaying it
     *
     * @param tickerText text that flashes a bit, can be null
     * @param content text that appears under title
     * @param intent intent that's executed on notification click, can be null
     * @return built notification */

    @TargetApi(16)
    private Notification buildNotification(@Nullable String tickerText, @NonNull String content, @Nullable PendingIntent intent) {
        if (DEBUG_NOTIFICATIONS) logger.debug("buildNotification({}, {}, {})", tickerText, content, intent);
        PendingIntent contentIntent;
        contentIntent = (intent != null) ? intent :
            PendingIntent.getActivity(this, 0, new Intent(this, WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

        int icon;
        if (!isConnection(AUTHENTICATED)) {
            if (isConnection(CONNECTING)) icon = R.drawable.ic_connecting;
            else icon = R.drawable.ic_disconnected;
        }
        else if (hotCount == 0) icon = R.drawable.ic_connected;
        else icon = R.drawable.ic_hot;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent)
               .setSmallIcon(icon)
               .setContentTitle("WeechatAndroid v" + BuildConfig.VERSION_NAME)
               .setContentText(content)
               .setWhen(System.currentTimeMillis());

        if (prefs.getBoolean(PREF_NOTIFICATION_TICKER, true)) {
            builder.setTicker(tickerText);
        }

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification.priority = Notification.PRIORITY_MIN;
        }
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    /** display default notification */
    void displayDefaultNotification() {
        showNotification(null, getString(R.string.notification_connected_to) + host);
    }

    /** display custom notification with specific intent */
    private void showNotification(@Nullable String tickerText, @NonNull String content, @NonNull PendingIntent intent) {
        notificationManger.notify(NOTIFICATION_ID, buildNotification(tickerText, content, intent));
    }

    /** display custom notification with default intent */
    private void showNotification(@Nullable String tickerText, @NonNull String content) {
        notificationManger.notify(NOTIFICATION_ID, buildNotification(tickerText, content, null));
    }

    private static final int BUFFER = 0, LINE = 1;

    /** display notification with a hot message
     ** clicking on it will open the buffer & scroll up to the hot line, if needed
     ** mind that SOMETIMES hotCount will be larger than hotList, because
     ** it's filled from hotlist data and hotList only contains lines that
     ** arrived in real time. so we add (message not available) if there are NO lines to display
     ** and add "..." if there are some lines to display, but not all */
    public void changeHotNotification(boolean newHighlight) {
        if (DEBUG_NOTIFICATIONS) logger.warn("changeHotNotification({})", newHighlight);
        final int hotCount = BufferList.getHotCount();
        final List<String[]> hotList = BufferList.hotList;

        if (hotCount == 0) {
            notificationManger.cancel(NOTIFICATION_HIGHLIGHT_ID);
        } else {
            // find our target buffer. if ALL items point to the same buffer, use it,
            // otherwise, go to buffer list (→ "")
            if (!prefs.getBoolean(PREF_NOTIFICATION_ENABLE, true)) {
                return;
            }
            Set<String> set = new HashSet<>();
            for (String[] h: hotList) set.add(h[BUFFER]);
            String target_buffer = (hotCount == hotList.size() && set.size() == 1) ? hotList.get(0)[BUFFER] : "";
            if (DEBUG_NOTIFICATIONS) logger.warn("...target='{}', hotCount={}, set.size()={}", target_buffer, hotCount, set.size());

            // prepare intent
            Intent intent = new Intent(this, WeechatActivity.class).putExtra("full_name", target_buffer);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            // prepare notification
            // make the ticker the LAST message
            String message = hotList.size() == 0 ? getString(R.string.hot_message_not_available) : hotList.get(hotList.size() - 1)[LINE];
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setContentIntent(contentIntent)
                    .setSmallIcon(R.drawable.ic_hot)
                    .setContentTitle(getResources().getQuantityString(R.plurals.hot_messages, hotCount, hotCount))
                    .setContentText(message);

            // display several lines only if we have at least one visible line and
            // 2 or more lines total. that is, either display full list of lines or
            // one ore more visible lines and "..."
            if (hotList.size() > 0 && hotCount > 1) {
                NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle()
                        .setSummaryText(host);

                for (String[] bufferToLine : hotList) inbox.addLine(bufferToLine[LINE]);
                if (hotList.size() < hotCount) inbox.addLine("…");

                builder.setContentInfo(String.valueOf(hotCount));
                builder.setStyle(inbox);
            }

            if (newHighlight) {
                builder.setTicker(message);

                String ringtone = prefs.getString(PREF_NOTIFICATION_SOUND, "content://settings/system/notification_sound");
                if (!"".equals(ringtone))
                    builder.setSound(Uri.parse(ringtone));

                int flags = 0;
                if (prefs.getBoolean(PREF_NOTIFICATION_LIGHT, false))
                    flags |= Notification.DEFAULT_LIGHTS;
                if (prefs.getBoolean(PREF_NOTIFICATION_VIBRATE, false))
                    flags |= Notification.DEFAULT_VIBRATE;
                builder.setDefaults(flags);
            }

            notificationManger.notify(NOTIFICATION_HIGHLIGHT_ID, builder.build());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// connect/disconnect
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** only auto-connect if auto-connect is on ON in the prefs and
     ** the user did not disconnect by tapping disconnect in menu */
    private boolean mustAutoConnect() {
         return prefs.getBoolean(PREF_AUTO_CONNECT, false) && !prefs.getBoolean(PREF_MUST_STAY_DISCONNECTED, false);
    }

    private static final boolean CONNECTION_IMPOSSIBLE = false;
    private static final long WAIT_BEFORE_WAIT_MESSAGE_DELAY = 5;
    private static final long DELAYS[] = new long[] {5, 15, 30, 60, 120, 300, 600, 900};

    public void startThreadedConnectLoop(final boolean reconnecting) {
        if (DEBUG_CONNECTION) logger.debug("startThreadedConnectLoop()");
        if (connection != null && connection.isConnected()) {
            logger.error("startThreadedConnectLoop() run while connected!!");
            return;
        }
        prefs.edit().putBoolean(PREF_MUST_STAY_DISCONNECTED, false).commit();
        thandler.removeCallbacksAndMessages(null);
        thandler.post(new Runnable() {
            int reconnects = 0;
            int ticker = reconnecting ? R.string.notification_reconnecting : R.string.notification_connecting;
            int content = reconnecting ? R.string.notification_reconnecting_details : R.string.notification_connecting_details;
            int contentNow = reconnecting ? R.string.notification_reconnecting_details_now : R.string.notification_connecting_details_now;

            Runnable connectRunner = new Runnable() {
                @Override
                public void run() {
                    if (DEBUG_CONNECTION) logger.debug("...run()");
                    if (connection != null && connection.isConnected())
                        return;
                    if (DEBUG_CONNECTION) logger.debug("...not connected; connecting now");
                    connectionStatus = CONNECTING;
                    showNotification(String.format(getString(ticker), prefs.getString("host", null)),
                            getString(contentNow));
                    if (connect() != CONNECTION_IMPOSSIBLE)
                        thandler.postDelayed(notifyRunner, WAIT_BEFORE_WAIT_MESSAGE_DELAY * 1000);
                }
            };

            Runnable notifyRunner = new Runnable() {
                @Override
                public void run() {
                    if (DEBUG_CONNECTION) logger.debug("...run()");
                    if (connection != null && connection.isConnected())
                        return;
                    long delay = DELAYS[reconnects < DELAYS.length ? reconnects : DELAYS.length - 1];
                    if (DEBUG_CONNECTION) logger.debug("...waiting {} seconds", delay);
                    showNotification(String.format(getString(ticker), host),
                            String.format(getString(content), delay));
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
        prefs.edit().putBoolean(PREF_MUST_STAY_DISCONNECTED, mustStayDisconnected).commit();
        thandler.removeCallbacksAndMessages(null);
        thandler.post(new Runnable() {
            @Override
            public void run() {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void startThreadedDisconnect() {
        startThreadedDisconnect(true);
    }

    @TargetApi(19)
    void schedulePing(long triggerAt, Bundle extras) {
        Intent intent = new Intent(PingActionReceiver.PING_ACTION);
        intent.putExtras(extras);

        PendingIntent alarmIntent = PendingIntent.getBroadcast(
                this, 0,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmMgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent);
        } else {
            alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent);
        }
    }

    void schedulePing(long triggerAt) {
        schedulePing(triggerAt, new Bundle());
    }

    long pingIdleTime() {
        return Integer.parseInt(prefs.getString(PREF_PING_IDLE, "300")) * 1000;
    }

    long pingTimeout() {
        return Integer.parseInt(prefs.getString(PREF_PING_TIMEOUT, "30")) * 1000;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** try connect once
     ** @return true if connection attempt has been made and false if connection is not possible */
    private boolean connect() {
        if (DEBUG_CONNECTION) logger.debug("connect()");

        // only connect if we aren't already connected
        if ((connection != null) && (connection.isConnected()))
            return false;

        if (connection != null)
            connection.disconnect();

        // load the preferences
        host = prefs.getString(PREF_HOST, null);
        pass = prefs.getString(PREF_PASSWORD, null);
        port = Integer.parseInt(prefs.getString(PREF_PORT, "8001"));

        // if no host defined, signal user to edit their preferences
        if (host == null || pass == null) {
            Intent intent = new Intent(this, PreferencesActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            showNotification(getString(R.string.notification_update_settings_details),
                    getString(R.string.notification_update_settings),
                    contentIntent);
            return false;
        }

        NetworkInfo networkInfo = connectivityManager().getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            Intent intent = new Intent(this, WeechatActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            showNotification(getString(R.string.notification_network_unavailable_details),
                    getString(R.string.notification_network_unavailable),
                    contentIntent);
            networkUnavailable = true;
            return false;
        }

        IConnection conn;
        String connType = prefs.getString(PREF_CONNECTION_TYPE, PREF_TYPE_PLAIN);
        if (connType.equals(PREF_TYPE_SSH)) {
            SSHConnection tmp = new SSHConnection(host, port);
            tmp.setSSHHost(prefs.getString(PREF_SSH_HOST, ""));
            tmp.setSSHPort(prefs.getString(PREF_SSH_PORT, "22"));
            tmp.setSSHUsername(prefs.getString(PREF_SSH_USER, ""));
            tmp.setSSHKeyFile(prefs.getString(PREF_SSH_KEYFILE, ""));
            tmp.setSSHPassword(prefs.getString(PREF_SSH_PASS, ""));
            conn = tmp;
        } else if (connType.equals(PREF_TYPE_STUNNEL)) {
            StunnelConnection tmp = new StunnelConnection(host, port);
            tmp.setStunnelCert(prefs.getString(PREF_STUNNEL_CERT, ""));
            tmp.setStunnelKey(prefs.getString(PREF_STUNNEL_PASS, ""));
            conn = tmp;
        } else if (connType.equals(PREF_TYPE_SSL)) {
            SSLConnection tmp = new SSLConnection(host, port);
            tmp.setSSLKeystore(certmanager.sslKeystore);
            conn = tmp;
        } else if (connType.equals(PREF_TYPE_WEBSOCKET)) {
            WebSocketConnection tmp = new WebSocketConnection(host, port, false);
            conn = tmp;
        } else if (connType.equals(PREF_TYPE_WEBSOCKET_SSL)) {
            WebSocketConnection tmp = new WebSocketConnection(host, port, true);
            tmp.setSSLKeystore(certmanager.sslKeystore);
            conn = tmp;
        } else {
            conn = new PlainConnection(host, port);
        }
        conn.addConnectionHandler(this);
        connection = new RelayConnection(conn, pass);
        connection.connect();
        return true;
    }

    private ConnectivityManager connectivityManager() {
        return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// callbacks
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnecting() {
        if (DEBUG) logger.debug("onConnecting()");
        connectionStatus = CONNECTING;
        for (RelayConnectionHandler rch : connectionHandlers) rch.onConnecting();
    }

    @Override
    public void onConnect() {
        if (DEBUG) logger.debug("onConnect()");
        connectionStatus = CONNECTED;

        if (prefs.getBoolean(PREF_PING_ENABLED, true)) {
            long triggerAt = SystemClock.elapsedRealtime() + pingTimeout();
            schedulePing(triggerAt);
        }

        connection.addHandler("*", lastMessageHandler);

        for (RelayConnectionHandler rch : connectionHandlers) rch.onConnect();
    }

    @Override
    public void onAuthenticated() {
        if (DEBUG) logger.debug("onAuthenticated()");
        connectionStatus = CONNECTED | AUTHENTICATED;

        if (disconnected) {
            showNotification(getString(R.string.notification_reconnected_to) + host, getString(R.string.notification_connected_to) + host);
        } else {
            String tmp = getString(R.string.notification_connected_to) + host;
            showNotification(tmp, tmp);
        }
        disconnected = false;

        startHandlingBoneEvents();

        // handle weechat upgrading & buffer listed event
        UpgradeHandler uh = new UpgradeHandler(this);
        connection.addHandler("_upgrade", uh);
        connection.addHandler("_upgrade_ended", uh);

        for (RelayConnectionHandler rch : connectionHandlers) rch.onAuthenticated();
    }

    @Override
    public void onAuthenticationFailed() {
        if (DEBUG) logger.debug("onAuthenticateFailed()");

        for (RelayConnectionHandler rch : connectionHandlers) rch.onAuthenticationFailed();
    }

    abstract void startHandlingBoneEvents();

    @Override
    public void onBuffersListed() {
        if (DEBUG) logger.debug("onBuffersListed()");
        connectionStatus = CONNECTED | AUTHENTICATED | BUFFERS_LISTED;
        for (RelayConnectionHandler rch : connectionHandlers) rch.onBuffersListed();
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) logger.debug("onDisconnect()");
        connectionStatus = DISCONNECTED;

        // Only do the disconnect handler once
        if (disconnected) return;
        disconnected = true;

        Intent intent = new Intent(PingActionReceiver.PING_ACTION);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_NO_CREATE);
        alarmMgr.cancel(alarmIntent);

        // automatically attempt reconnection if enabled (and if we aren't shutting down)
        if (mustAutoConnect()) {
            startThreadedConnectLoop(true);
        } else {
            String tmp = getString(R.string.notification_disconnected);
            showNotification(tmp, tmp);
        }

        for (RelayConnectionHandler rch : connectionHandlers) rch.onDisconnect();
    }

    @Override
    public void onError(String error, Object extraData) {
        if (DEBUG) logger.error("onError(..., ...)", error, extraData);
        for (RelayConnectionHandler rch : connectionHandlers) rch.onError(error, extraData);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Load/refresh preferences
        if (key.equals(PREF_HOST)) {
            host = prefs.getString(key, null);
        } else if (key.equals(PREF_PASSWORD)) {
            pass = prefs.getString(key, null);
        } else if (key.equals(PREF_PORT)) {
            port = Integer.parseInt(prefs.getString(key, "8001"));
        }
    }

    @Override
    public void onUpgrade() {
        // Don't do this twice
        if (upgrading != null && upgrading.isAlive())
            return;

        // Basically just reconnect on upgrade
        upgrading = new Thread(new Runnable() {
            @Override
            public void run() {
                showNotification(getString(R.string.notification_upgrading),
                        getString(R.string.notification_upgrading_details));
                startThreadedDisconnect();
                SystemClock.sleep(5000);
                startThreadedConnectLoop(true);
            }
        });
        upgrading.start();
    }

    private RelayMessageHandler lastMessageHandler = new RelayMessageHandler() {
        @Override
        public void handleMessage(RelayObject obj, String id) {
            lastMessageReceivedAt = SystemClock.elapsedRealtime();
        }
    };
}
