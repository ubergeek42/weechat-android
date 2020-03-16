//  Copyright 2012 Keith Johnson
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.ubergeek42.WeechatAndroid.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.utils.Network;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.relay.Hotlist;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.relay.connection.IObserver;
import com.ubergeek42.weechat.relay.connection.RelayConnection;
import com.ubergeek42.weechat.relay.RelayMessage;
import com.ubergeek42.weechat.relay.connection.IConnection;
import com.ubergeek42.weechat.relay.connection.SimpleConnection;
import com.ubergeek42.weechat.relay.connection.SSHConnection;
import com.ubergeek42.weechat.relay.connection.Utils;
import com.ubergeek42.weechat.relay.connection.WebSocketConnection;
import com.ubergeek42.weechat.relay.protocol.RelayObject;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.nio.channels.UnresolvedAddressException;
import java.util.EnumSet;

import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.utils.Constants.*;
import static com.ubergeek42.WeechatAndroid.utils.Assert.assertThat;


public class RelayService extends Service implements IObserver {

    final private @Root Kitty kitty = Kitty.make();
    private static int iteration = -1;

    volatile public RelayConnection connection;
    private PingActionReceiver ping;
    private Handler doge;               // thread "doge" used for connecting/disconnecting

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// status & life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public RelayService() {
        super();
        kitty.setPrefix(String.valueOf(++iteration));
    }

    @MainThread @Override @Cat public void onCreate() {
        // prepare handler that will run on a separate thread
        HandlerThread handlerThread = new HandlerThread("d-" + iteration);
        handlerThread.start();
        doge = new Handler(handlerThread.getLooper());

        Network.get().register(this, () -> {
            if (P.reconnect && state.contains(STATE.STARTED)) _start();
        });

        ping = new PingActionReceiver(this);
        EventBus.getDefault().register(this);
    }

    @MainThread @Override @Cat public void onDestroy() {
        EventBus.getDefault().unregister(this);
        P.saveStuff();
        Network.get().unregister(this);
        doge.post(() -> doge.getLooper().quit());
    }

    @MainThread @Nullable @Override public IBinder onBind(Intent intent) {
        return null;
    }

    // todo synchronization?
    @Subscribe @Cat public void onEvent(SendMessageEvent event) {
        connection.sendMessage(event.message);
    }

    final public static String ACTION_START = "com.ubergeek42.WeechatAndroid.START";
    final public static String ACTION_STOP = "com.ubergeek42.WeechatAndroid.STOP";

    // this method is called:
    //     * whenever app calls startService() (that means on each screen rotate)
    //     * when service is recreated by system after OOM kill. (intent = null)
    @MainThread @Override @Cat public int onStartCommand(Intent intent, int flags, int startId) {
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
    private static final long[] DELAYS = new long[] {5, 15, 30, 60, 120, 300, 600, 900};

    // called by user and when disconnected
    @MainThread @Cat private synchronized void start() {
        assertThat(state).contains(STATE.STOPPED);
        changeState(EnumSet.of(STATE.STARTED));
        P.setServiceAlive(true);
        _start();
    }

    // called by ↑ and Connectivity
    @MainThread @Cat synchronized void _start() {
        doge.removeCallbacksAndMessages(null);
        doge.post(new Runnable() {
            int reconnects = 0;
            boolean waiting = false;

            final String willConnectSoon = getString(R.string.notification_connecting_details);
            final String connectingNow = getString(R.string.notification_connecting_details_now);

            @WorkerThread @Override public void run() {
                if (state.contains(STATE.AUTHENTICATED)) {
                    P.connectionSurelyPossibleWithCurrentPreferences = true;
                    return;
                }
                if (waiting) waitABit(); else connectNow();
                waiting = !waiting;
            }

            @WorkerThread void connectNow() {
                Notificator.showMain(RelayService.this, connectingNow);
                switch (connect()) {
                    case LATER: break;
                    case IMPOSSIBLE: if (!P.connectionSurelyPossibleWithCurrentPreferences) {stop(); break;}
                    case POSSIBLE: doge.postDelayed(this, WAIT_BEFORE_WAIT_MESSAGE_DELAY * 1000);
                }
            }

            @WorkerThread void waitABit() {
                long delay = DELAYS[reconnects < DELAYS.length ? reconnects : DELAYS.length - 1];
                String message = String.format(willConnectSoon, delay);
                Notificator.showMain(RelayService.this, message);
                reconnects++;
                doge.postDelayed(this, delay * 1000);
            }
        });
    }

    // called by user and when there was a fatal exception while trying to connect
    @AnyThread @Cat synchronized void stop() {
        assertThat(state).doesNotContain(STATE.STOPPED);
        if (state.contains(STATE.AUTHENTICATED)) goodbye();
        changeState(EnumSet.of(STATE.STOPPED));
        interrupt();
        stopSelf();
        P.setServiceAlive(false);
    }

    // called by ↑ and PingActionReceiver
    // close whatever connection we have in a thread, may result in a call to onStateChanged
    @AnyThread @Cat synchronized void interrupt() {
        doge.removeCallbacksAndMessages(null);
        doge.post(() -> {
            doge.removeCallbacksAndMessages(null);
            if (connection != null) connection.disconnect();
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private enum TRY {POSSIBLE, LATER, IMPOSSIBLE}

    @WorkerThread @CatD(exit=true) private TRY connect() {
        if (connection != null)
            connection.disconnect();

        if (!Network.get().hasProperty(Network.Property.CONNECTED)) {
            Notificator.showMain(this, getString(R.string.notification_waiting_network));
            return TRY.LATER;
        }

        IConnection conn;
        try {
            switch (P.connectionType) {
                case PREF_TYPE_SSH: conn = new SSHConnection(P.host, P.port, P.sshHost, P.sshPort, P.sshUser, P.sshPass, P.sshKey, P.sshKnownHosts); break;
                case PREF_TYPE_SSL: conn = new SimpleConnection(P.host, P.port, P.sslSocketFactory, SSLHandler.getHostnameVerifier()); break;
                case PREF_TYPE_WEBSOCKET: conn = new WebSocketConnection(P.host, P.port, P.wsPath, null, null); break;
                case PREF_TYPE_WEBSOCKET_SSL: conn = new WebSocketConnection(P.host, P.port, P.wsPath, P.sslSocketFactory, SSLHandler.getHostnameVerifier()); break;
                default: conn = new SimpleConnection(P.host, P.port, null, null); break;
            }
        } catch (Exception e) {
            kitty.error("connect(): exception while creating connection", e);
            onException(e);
            return TRY.IMPOSSIBLE;
        }

        connection = new RelayConnection(conn, P.pass, this);
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

    @WorkerThread @Override synchronized public void onStateChanged(RelayConnection.STATE s) {
        if (state.contains(STATE.STOPPED)) return;
        switch (s) {
            case CONNECTING:
            case CONNECTED:
                return;
            case AUTHENTICATED:
                changeState(EnumSet.of(STATE.STARTED, STATE.AUTHENTICATED));
                Notificator.showMain(this, getString(R.string.notification_connected_to, P.printableHost));
                hello();
                break;
            case BUFFERS_LISTED:
                changeState(EnumSet.of(STATE.STARTED, STATE.AUTHENTICATED, STATE.LISTED));
                break;
            case DISCONNECTED:
                if (!state.contains(STATE.AUTHENTICATED)) return; // continue connecting
                changeState(EnumSet.of(STATE.STOPPED));
                goodbye();
                if (P.reconnect) Weechat.runOnMainThread(this::start);
                else stopSelf();
        }
    }

    private void changeState(EnumSet<STATE> state) {
        this.state = state;
        EventBus.getDefault().postSticky(new StateChangedEvent(state));
    }

    @WorkerThread @Cat private void hello() {
        Hotlist.redraw(true);
        ping.scheduleFirstPing();
        BufferList.launch(this);
        SyncAlarmReceiver.start(this);
    }

    //sync so that goodbye() never happens while state=auth but hello didn't run yet
    @AnyThread @Cat private void goodbye() {
        SyncAlarmReceiver.stop(this);
        BufferList.stop();
        ping.unschedulePing();
        P.saveStuff();
        Hotlist.redraw(false);
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class ExceptionWrapper extends Exception {
        ExceptionWrapper(Exception cause, String message) {
            super(message);
            initCause(cause);
        }
    }

    @WorkerThread @Override public void onException(Exception e) {
        kitty.error("→ onException(%s)", e.getClass().getSimpleName());
        if (state.contains(STATE.STOPPED)) return;
        if (e instanceof Utils.StreamClosed && (!state.contains(STATE.AUTHENTICATED)))
            e = new ExceptionWrapper(e, getString(R.string.relay_error_server_closed));
        else if (e instanceof UnresolvedAddressException)
            e = new ExceptionWrapper(e, getString(R.string.relay_error_resolve, P.connectionType.equals(PREF_TYPE_SSH) ? P.sshHost : P.host));
        EventBus.getDefault().post(new ExceptionEvent(e));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static RelayObject[] NULL = {null};

    @WorkerThread @Override public void onMessage(RelayMessage message) {
        kitty.trace("→ onMessage(%s)", message.getID());
        if (state.contains(STATE.STOPPED)) return;
        ping.onMessage();
        RelayObject[] objects = message.getObjects() == null ? NULL : message.getObjects();
        String id = message.getID();
        for (RelayObject object : objects)
            BufferList.handleMessage(object, id);
    }
}
