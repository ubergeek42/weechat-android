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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.WeechatPreferencesActivity;
import com.ubergeek42.WeechatAndroid.notifications.HotlistHandler;
import com.ubergeek42.WeechatAndroid.notifications.HotlistObserver;
import com.ubergeek42.weechat.relay.RelayConnection;
import com.ubergeek42.weechat.relay.RelayConnectionHandler;
import com.ubergeek42.weechat.relay.RelayMessageHandler;
import com.ubergeek42.weechat.relay.messagehandler.BufferManager;
import com.ubergeek42.weechat.relay.messagehandler.HotlistManager;
import com.ubergeek42.weechat.relay.messagehandler.LineHandler;
import com.ubergeek42.weechat.relay.messagehandler.NicklistHandler;
import com.ubergeek42.weechat.relay.messagehandler.UpgradeHandler;
import com.ubergeek42.weechat.relay.messagehandler.UpgradeObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class RelayService extends Service implements RelayConnectionHandler,
        OnSharedPreferenceChangeListener, HotlistObserver, UpgradeObserver {

    private static Logger logger = LoggerFactory.getLogger(RelayService.class);
    
    private static final String KEYSTORE_PASSWORD = "weechat-android";
    private static final int NOTIFICATION_ID = 42;
    private NotificationManager notificationManger;

    boolean optimize_traffic = false;
    
    String host;
    int port;
    String pass;

    private int sshLocalPort = 22231;
    
    KeyStore sslKeystore;
    X509Certificate untrustedCert;

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
        showNotification(null, "Tap to connect");

        disconnected = false;

        loadKeystore();
        
        
        if (prefs.getBoolean("autoconnect", false)) {
            connect();
        }
    }
    
    // Load our keystore for storing SSL certificates
    private void loadKeystore() {
        boolean createKeystore = false;
        
        // Get the ssl keystore
        File keystoreFile = new File(getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks");

        try {
            sslKeystore = KeyStore.getInstance("BKS");
            sslKeystore.load(new FileInputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            e.printStackTrace();
        } catch (CertificateException e) {
            // Ideally never happens
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            logger.debug("Keystore not found, creating...");
            createKeystore = true;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        if (createKeystore) {
            createKeystore();
        }
    }
    private void createKeystore() {
        try {
            sslKeystore.load(null,null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore)null);
            // Copy current certs into our keystore so we can use it...
            // TODO: don't actually do this...
            X509TrustManager xtm = (X509TrustManager) tmf.getTrustManagers()[0]; 
            for (X509Certificate cert : xtm.getAcceptedIssuers()) { 
                sslKeystore.setCertificateEntry(cert.getSubjectDN().getName(), cert); 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        saveKeystore();
    }
    
    private void saveKeystore() {
        File keystoreFile = new File(getDir("sslDir", Context.MODE_PRIVATE), "keystore.jks");
        try {
            sslKeystore.store(new FileOutputStream(keystoreFile), KEYSTORE_PASSWORD.toCharArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    void trustCertificate() {
        if (untrustedCert!=null) {
            try {
                KeyStore.TrustedCertificateEntry x = new KeyStore.TrustedCertificateEntry(untrustedCert);
                sslKeystore.setEntry(host, x, null);
            } catch (KeyStoreException e) {
                e.printStackTrace();
            }
            saveKeystore();
        }
    }

    @Override
    public void onDestroy() {
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
        return super.onStartCommand(intent, flags, startId);
    }

    public boolean connect() {
        // Only connect if we aren't already connected
        if ((relayConnection != null) && (relayConnection.isConnected())) {
            return false;
        }

        // Load the preferences
        host = prefs.getString("host", null);
        pass = prefs.getString("password", "password");
        port = Integer.parseInt(prefs.getString("port", "8001"));

        // If no host defined, signal them to edit their preferences
        if (host == null) {
            Intent i = new Intent(this, WeechatPreferencesActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_CANCEL_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setContentIntent(contentIntent).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.app_version))
                    .setContentText(getString(R.string.notification_update_settings))
                    .setTicker(getString(R.string.notification_update_settings_details))
                    .setWhen(System.currentTimeMillis());

            Notification notification = builder.getNotification();
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
            notificationManger.notify(NOTIFICATION_ID, notification);
            return false;
        }

        shutdown = false;

        bufferManager = new BufferManager();
        hotlistManager = new HotlistManager();

        hotlistManager.setBufferManager(bufferManager);
        msgHandler = new LineHandler(bufferManager);
        nickHandler = new NicklistHandler(bufferManager);
        hotlistHandler = new HotlistHandler(bufferManager, hotlistManager);

        optimize_traffic = prefs.getBoolean("optimize_traffic", false);



        hotlistHandler.registerHighlightHandler(this);


        String connType = prefs.getString("connection_type", "plain");
        if (connType.equals("ssh")) {
            CreatePortForward();
            relayConnection = new RelayConnection("127.0.0.1", sshLocalPort, pass);

        } else if (connType.equals("stunnel")) {
            CreateStunnel();
            relayConnection = new RelayConnection(host, port, pass);
        } else if (connType.equals("ssl")) {
            relayConnection = new RelayConnection(sslKeystore, host, port, pass);
        } else {
            relayConnection = new RelayConnection(host, port, pass);
        }
        relayConnection.setConnectionHandler(this);

        relayConnection.connect();
        return true;
    }

    void resetNotification() {
        showNotification(null, getString(R.string.notification_connected_to) + relayConnection.getServer());
    }

    private void CreatePortForward(){
        String sshKeyFile = prefs.getString("ssh_keyfile", "");
        String sshHost = prefs.getString("ssh_host", "");
        String sshUser = prefs.getString("ssh_user", "");
        String sshPass = prefs.getString("ssh_pass", "");
        int sshPort = Integer.parseInt(prefs.getString("ssh_port", "22"));
        try {
            JSch jsch = new JSch();

            if (sshKeyFile != null && sshKeyFile.length()>0) {
                jsch.addIdentity(sshKeyFile, sshPass);
            }
            Session sshSession = jsch.getSession(sshUser, sshHost, sshPort);

            if (sshKeyFile == null || sshKeyFile.length()==0)
                sshSession.setPassword(sshPass);
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect();
            sshSession.setPortForwardingL(sshLocalPort, sshHost, port);
        } catch (JSchException e) {
            e.printStackTrace();
        }
    }

    /**
     * Connects to the server(via stunnel) in a new thread, so we can interrupt it if we want to
     * cancel the connection
     */
    private void CreateStunnel() {
            SSLContext context = null;
            KeyStore keyStore = null;
            TrustManagerFactory tmf = null;
            KeyStore keyStoreCA = null;
            KeyManagerFactory kmf = null;
            try {

                FileInputStream pkcs12in = new FileInputStream(new File(prefs.getString("stunnel_cert", "")));

                context = SSLContext.getInstance("TLS");

                // Local client certificate and key and server certificate
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(pkcs12in, prefs.getString("stunnel_pass", "").toCharArray());

                // Build a TrustManager, that trusts only the server certificate
                keyStoreCA = KeyStore.getInstance("BKS");
                keyStoreCA.load(null, null);
                keyStoreCA.setCertificateEntry("Server", keyStore.getCertificate("Server"));
                tmf = TrustManagerFactory.getInstance("X509");
                tmf.init(keyStoreCA);

                // Build a KeyManager for Client auth
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, null);
                context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            } catch (Exception e) {
                e.printStackTrace();
                for (RelayConnectionHandler wrch : connectionHandlers) {
                    wrch.onError(e.getMessage(), e);
                }
            }
    }



    private void showNotification(String tickerText, String content) {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                WeechatActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_version)).setContentText(content)
                .setTicker(tickerText).setWhen(System.currentTimeMillis());

        Notification notification = builder.getNotification();
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        notificationManger.notify(NOTIFICATION_ID, notification);
    }

    // Spawn a thread that attempts to reconnect us.
    private void reconnect() {
        // stop if we are already trying to reconnect
        if (reconnector != null && reconnector.isAlive()) {
            return;
        }
        reconnector = new Thread(new Runnable() {
            long delays[] = new long[] { 0, 5, 15, 30, 60, 120, 300, 600, 900 };
            int numReconnects = 0;// how many times we've tried this...

            @Override
            public void run() {
                for (;;) {
                    long currentDelay = 0;
                    if (numReconnects >= delays.length) {
                        currentDelay = delays[delays.length - 1];
                    } else {
                        currentDelay = delays[numReconnects];
                    }
                    if (currentDelay > 0) {
                        showNotification(getString(R.string.notification_reconnecting), String.format(getString(R.string.notification_reconnecting_details),currentDelay));
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
    public void onConnect() {
        if (disconnected) {
            showNotification(getString(R.string.notification_reconnected_to) + relayConnection.getServer(), getString(R.string.notification_connected_to)
                    + relayConnection.getServer());
        } else {
            String tmp = getString(R.string.notification_connected_to) + relayConnection.getServer();
            showNotification(tmp,tmp);
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
            showNotification(tmp,tmp);
        } else {
            String tmp = getString(R.string.notification_disconnected);
            showNotification(tmp,tmp);
        }
    }

    @Override
    public void onError(String error, Object extraData) {
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_version)).setContentText(message)
                .setTicker(message).setWhen(System.currentTimeMillis());

        Notification notification = builder.getNotification();
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        // Default notification sound if enabled
        if (prefs.getBoolean("notification_sounds", false)) {
            notification.defaults |= Notification.DEFAULT_SOUND;
        }

        notificationManger.notify(NOTIFICATION_ID, notification);
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
