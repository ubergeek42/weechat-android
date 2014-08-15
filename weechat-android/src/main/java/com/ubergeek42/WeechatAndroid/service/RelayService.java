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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.WeechatPreferencesActivity;
import com.ubergeek42.WeechatAndroid.notifications.HotlistHandler;
import com.ubergeek42.WeechatAndroid.notifications.HotlistObserver;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.connection.IConnection;
import com.ubergeek42.weechat.relay.connection.PlainConnection;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.SSLConnection;
import com.ubergeek42.weechat.relay.connection.StunnelConnection;
import com.ubergeek42.weechat.relay.connection.WebSocketConnection;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManager;
import com.ubergeek42.weechat.relay.messagehandler.LineHandler;
import com.ubergeek42.weechat.relay.messagehandler.NicklistHandler;
import com.ubergeek42.weechat.relay.messagehandler.UpgradeHandler;
import com.ubergeek42.weechat.relay.messagehandler.UpgradeObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.HashSet;

public class RelayService extends Service implements RelayConnectionHandler,
        OnSharedPreferenceChangeListener, HotlistObserver, UpgradeObserver {

    private static Logger logger = LoggerFactory.getLogger(RelayService.class);
    private static final int NOTIFICATION_ID = 42;
    public static final int NOTIFICATION_HIGHLIGHT_ID = 43;

    private NotificationManager notificationManger;

    boolean optimize_traffic = false;

    String host;
    int port;
    String pass;

    String stunnelCert;
    String stunnelPass;

    String sshHost;
    String sshPass;
    String sshPort;
    String sshUser;
    String sshKeyfile;

    RelayConnection relayConnection;
    BufferManager bufferManager;
    RelayMessageHandler msgHandler;
    NicklistHandler nickHandler;
    HotlistHandler hotlistHandler;
    HotlistManager hotlistManager;
    HashSet<RelayConnectionHandler> connectionHandlers = new HashSet<RelayConnectionHandler>();
    private SharedPreferences prefs;
    private boolean shutdown;
    private boolean disconnected;
    private Thread reconnector = null;
    private Thread upgrading;

    SSLHandler certmanager;
    X509Certificate untrustedCert;

    @Override
    public IBinder onBind(Intent arg0) {
        return new RelayServiceBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.registerOnSharedPreferenceChangeListener(this);

        notificationManger = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //showNotification(null, "Tap to connect");


        startForeground(NOTIFICATION_ID, buildNotification(null, "Tap to connect", null));

        disconnected = false;

        // Prepare for dealing with SSL certs
        certmanager = new SSLHandler(new File(getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks"));

        if (prefs.getBoolean("autoconnect", false)) {
            connect();
        }
    }


    @Override
    public void onDestroy() {
        logger.debug("relayservice destroyed");
        notificationManger.cancel(NOTIFICATION_ID);
        super.onDestroy();

        // TODO: decide whether killing the process is necessary...
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Autoconnect if possible
            // if (prefs.getBoolean("autoconnect", false)) {
            // connect();
            // }
        }
        return START_STICKY;
    }

    public boolean connect() {
        // Load the preferences
        host = prefs.getString("host", null);
        pass = prefs.getString("password", "password");
        port = Integer.parseInt(prefs.getString("port", "8001"));

        stunnelCert = prefs.getString("stunnel_cert", "");
        stunnelPass = prefs.getString("stunnel_pass", "");

        sshHost = prefs.getString("ssh_host", "");
        sshUser = prefs.getString("ssh_user", "");
        sshPass = prefs.getString("ssh_pass", "");
        sshPort = prefs.getString("ssh_port", "22");
        sshKeyfile = prefs.getString("ssh_keyfile", "");

        optimize_traffic = prefs.getBoolean("optimize_traffic", false);

        // If no host defined, signal them to edit their preferences
        if (host == null) {
            Intent i = new Intent(this, WeechatPreferencesActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            showNotification(getString(R.string.notification_update_settings_details),
                    getString(R.string.notification_update_settings),
                    contentIntent);
            return false;
        }

        // Only connect if we aren't already connected
        if ((relayConnection != null) && (relayConnection.isConnected())) {
            return false;
        }

        shutdown = false;

        bufferManager = new BufferManager();
        hotlistManager = new HotlistManager();

        hotlistManager.setBufferManager(bufferManager);
        msgHandler = new LineHandler(bufferManager);
        nickHandler = new NicklistHandler(bufferManager);
        hotlistHandler = new HotlistHandler(bufferManager, hotlistManager);

        hotlistHandler.registerHighlightHandler(this);

        IConnection conn;
        String connType = prefs.getString("connection_type", "plain");
        if (connType.equals("ssh")) {
            SSHConnection tmp = new SSHConnection(host, port);
            tmp.setSSHHost(sshHost);
            tmp.setSSHPort(sshPort);
            tmp.setSSHUsername(sshUser);
            tmp.setSSHKeyFile(sshKeyfile);
            tmp.setSSHPassword(sshPass);
            conn = tmp;
        } else if (connType.equals("stunnel")) {
            StunnelConnection tmp = new StunnelConnection(host, port);
            tmp.setStunnelCert(stunnelCert);
            tmp.setStunnelKey(stunnelPass);
            conn = tmp;
        } else if (connType.equals("ssl")) {
            SSLConnection tmp = new SSLConnection(host, port);
            tmp.setSSLKeystore(certmanager.sslKeystore);
            conn = tmp;
        } else if (connType.equals("websocket")) {
            WebSocketConnection tmp = new WebSocketConnection(host, port, false);
            conn = tmp;
        } else if (connType.equals("websocket-ssl")) {
            WebSocketConnection tmp = new WebSocketConnection(host, port, true);
            tmp.setSSLKeystore(certmanager.sslKeystore);
            conn = tmp;
        } else {
            PlainConnection pc = new PlainConnection(host, port);
            conn = pc;
        }

        relayConnection = new RelayConnection(conn, pass);
        conn.addConnectionHandler(this);

        relayConnection.connect();
        return true;
    }

    void resetNotification() {
        showNotification(null, getString(R.string.notification_connected_to) + host);
    }

    @TargetApi(16)
    private Notification buildNotification(String tickerText, String content, PendingIntent intent) {
        PendingIntent contentIntent;
        if (intent == null) {
            contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);
        } else {
            contentIntent = intent;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_version)).setContentText(content)
                .setTicker(tickerText).setWhen(System.currentTimeMillis());

        final Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification.priority = Notification.PRIORITY_MIN;
        }
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    private void showNotification(String tickerText, String content, PendingIntent intent) {
        notificationManger.notify(NOTIFICATION_ID, buildNotification(tickerText, content, intent));
    }

    private void showNotification(String tickerText, String content) {
        notificationManger.notify(NOTIFICATION_ID, buildNotification(tickerText, content, null));
    }

    // Spawn a thread that attempts to reconnect us.
    private void reconnect() {
        // stop if we are already trying to reconnect
        if (reconnector != null && reconnector.isAlive()) {
            return;
        }
        reconnector = new Thread(new Runnable() {
            long delays[] = new long[]{0, 5, 15, 30, 60, 120, 300, 600, 900};
            int numReconnects = 0;// how many times we've tried this...

            @Override
            public void run() {
                for (; ; ) {
                    long currentDelay = 0;
                    if (numReconnects >= delays.length) {
                        currentDelay = delays[delays.length - 1];
                    } else {
                        currentDelay = delays[numReconnects];
                    }
                    if (currentDelay > 0) {
                        showNotification(getString(R.string.notification_reconnecting), String.format(getString(R.string.notification_reconnecting_details), currentDelay));
                    }
                    // Sleep for a bit
                    SystemClock.sleep(currentDelay * 1000);

                    // See if we are connected, if so we can stop trying to reconnect
                    if (relayConnection != null && relayConnection.isConnected()) {
                        return;
                    }

                    // Try connecting again
                    connect();
                    numReconnects++;
                }
            }
        });
        reconnector.start();
    }

    @Override
    public void onConnecting() {

    }

    @Override
    public void onConnect() {

    }

    @Override
    public void onAuthenticated() {
        if (disconnected == true) {
            showNotification(getString(R.string.notification_reconnected_to) + host, getString(R.string.notification_connected_to) + host);
        } else {
            String tmp = getString(R.string.notification_connected_to) + host;
            showNotification(tmp, tmp);
        }
        disconnected = false;

        // Handle weechat upgrading
        UpgradeHandler uh = new UpgradeHandler(this);
        relayConnection.addHandler("_upgrade", uh);
        relayConnection.addHandler("_upgrade_ended", uh);

        // Handle us getting a listing of the buffers
        relayConnection.addHandler("listbuffers", bufferManager);

        // Handle weechat event messages regarding buffers
        relayConnection.addHandler("_buffer_opened", bufferManager);
        relayConnection.addHandler("_buffer_type_changed", bufferManager);
        relayConnection.addHandler("_buffer_moved", bufferManager);
        relayConnection.addHandler("_buffer_merged", bufferManager);
        relayConnection.addHandler("_buffer_unmerged", bufferManager);
        relayConnection.addHandler("_buffer_renamed", bufferManager);
        relayConnection.addHandler("_buffer_title_changed", bufferManager);
        relayConnection.addHandler("_buffer_localvar_added", bufferManager);
        relayConnection.addHandler("_buffer_localvar_changed", bufferManager);
        relayConnection.addHandler("_buffer_localvar_removed", bufferManager);
        relayConnection.addHandler("_buffer_closing", bufferManager);

        // Handle lines being added to buffers
        relayConnection.addHandler("_buffer_line_added", hotlistHandler);
        relayConnection.addHandler("_buffer_line_added", msgHandler);
        relayConnection.addHandler("listlines_reverse", msgHandler);

        // Handle changes to the nicklist for buffers
        relayConnection.addHandler("nicklist", nickHandler);
        relayConnection.addHandler("_nicklist", nickHandler);
        relayConnection.addHandler("_nicklist_diff", nickHandler);

        // Handle getting infolist hotlist for initial hotlist sync
        relayConnection.addHandler("initialinfolist", hotlistManager);

        // Get a list of buffers current open, along with some information about them
        relayConnection
                .sendMsg("(listbuffers) hdata buffer:gui_buffers(*) number,full_name,short_name,type,title,nicklist,local_variables,notify");
        // Get the current hotlist
        relayConnection.sendMsg("initialinfolist", "infolist", "hotlist");

        // Subscribe to any future changes
        if (!optimize_traffic)
            relayConnection.sendMsg("sync");

        for (RelayConnectionHandler rch : connectionHandlers) {
            rch.onConnect();
        }
    }

    @Override
    public void onDisconnect() {
        if (disconnected) {
            return; // Only do the disconnect handler once
        }
        // :( aww disconnected
        for (RelayConnectionHandler rch : connectionHandlers) {
            rch.onDisconnect();
        }

        disconnected = true;

        // Automatically attempt reconnection if enabled(and if we aren't shutting down)
        if (!shutdown && prefs.getBoolean("reconnect", true)) {
            reconnect();
            String tmp = getString(R.string.notification_reconnecting);
            showNotification(tmp, tmp);
        } else {
            String tmp = getString(R.string.notification_disconnected);
            showNotification(tmp, tmp);
        }
    }

    @Override
    public void onError(String error, Object extraData) {
        logger.error("relayservice - error!");
        for (RelayConnectionHandler rch : connectionHandlers) {
            rch.onError(error, extraData);
        }
    }

    public void shutdown() {
        if (relayConnection != null) {
            shutdown = true;
            // Do the actual shutdown on its own thread(to avoid an error on Android 3.0+)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    relayConnection.disconnect();
                }
            }).start();
        }
        // TODO: possibly call stopself?
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Load/refresh preferences
        if (key.equals("host")) {
            host = prefs.getString("host", null);
        } else if (key.equals("password")) {
            pass = prefs.getString("password", "password");
        } else if (key.equals("port")) {
            port = Integer.parseInt(prefs.getString("port", "8001"));
        } else if (key.equals("optimize_traffic")) {
            optimize_traffic = prefs.getBoolean("optimize_traffic", false);
        }
    }

    @Override
    public void onHighlight(String bufferName, String message) {
        Intent i = new Intent(this, WeechatActivity.class);
        i.putExtra("buffer", bufferName);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent dismissIntent = new Intent("com.ubergeek42.WeechatAndroid.REMOVE_ALL_SAVED_HIGHLIGHTS");
        dismissIntent.setPackage(getPackageName());

        PendingIntent dismissPI = PendingIntent.getBroadcast(this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.highlight)).setContentText(message)
                .setTicker(message).setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setDeleteIntent(dismissPI);


        //Generating a JSONObject for current highlight

        final JSONObject currentHighlight = new JSONObject();
        try {
            currentHighlight.put("text", message);
            currentHighlight.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Using an Inbox notification if there is more than 1 highlight
        final String previousHighlights = prefs.getString("previous_highlights", "");
        if (!TextUtils.isEmpty(previousHighlights)) {
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            builder.setContentTitle(getString(R.string.highlights));

            try {
                JSONArray highlights = new JSONArray(previousHighlights);
                SimpleDateFormat format = new SimpleDateFormat(prefs.getString("timestamp_format", "HH:mm:ss"));

                String timestamp = format.format(currentHighlight.getLong("timestamp"));
                Spannable line = new SpannableString(timestamp + " " + currentHighlight.getString("text"));
                line.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, timestamp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                inboxStyle.addLine(line);

                for (int j = highlights.length()-1; j >= 0; j--) {
                    JSONObject highlight = highlights.getJSONObject(j);

                    timestamp = format.format(highlight.getLong("timestamp"));
                    line = new SpannableString(timestamp + " " + highlight.getString("text"));
                    line.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, timestamp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    inboxStyle.addLine(line);

                }

                inboxStyle.setSummaryText("");
                builder.setContentInfo(String.valueOf(highlights.length()+1));
                builder.setStyle(inboxStyle);

                highlights.put(currentHighlight);
                prefs.edit().putString("previous_highlights", highlights.toString()).commit();

            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            JSONArray highlights = new JSONArray();
            highlights.put(currentHighlight);
            prefs.edit().putString("previous_highlights", highlights.toString()).commit();
        }

        Notification notification = builder.build();

        // Default notification sound if enabled
        if (prefs.getBoolean("notification_sounds", false)) {
            notification.defaults |= Notification.DEFAULT_SOUND;
        }

        notificationManger.notify(NOTIFICATION_HIGHLIGHT_ID, notification);
    }

    @Override
    public void onUpgrade() {
        // Don't do this twice
        if (upgrading != null && upgrading.isAlive()) {
            return;
        }

        // Basically just reconnect on upgrade
        upgrading = new Thread(new Runnable() {
            @Override
            public void run() {
                showNotification(getString(R.string.notification_upgrading),
                        getString(R.string.notification_upgrading_details));
                shutdown();
                SystemClock.sleep(5000);
                connect();
            }
        });
        upgrading.start();
    }
}
