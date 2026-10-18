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
package com.ubergeek42.WeechatAndroid.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.notifications.addOrRemoveActionForCurrentNotifications
import com.ubergeek42.WeechatAndroid.notifications.showMainNotification
import com.ubergeek42.WeechatAndroid.relay.BufferList.handleMessage
import com.ubergeek42.WeechatAndroid.relay.BufferList.onServiceAuthenticated
import com.ubergeek42.WeechatAndroid.relay.BufferList.onServiceStopped
import com.ubergeek42.WeechatAndroid.service.Events.SendMessageEvent
import com.ubergeek42.WeechatAndroid.service.Events.StateChangedEvent
import com.ubergeek42.WeechatAndroid.service.SSLHandler.Companion.getInstance
import com.ubergeek42.WeechatAndroid.utils.Assert
import com.ubergeek42.WeechatAndroid.utils.Constants
import com.ubergeek42.WeechatAndroid.utils.Network
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.CatD
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import com.ubergeek42.weechat.relay.RelayMessage
import com.ubergeek42.weechat.relay.connection.IConnection
import com.ubergeek42.weechat.relay.connection.IObserver
import com.ubergeek42.weechat.relay.connection.RelayConnection
import com.ubergeek42.weechat.relay.connection.SSHConnection
import com.ubergeek42.weechat.relay.connection.SimpleConnection
import com.ubergeek42.weechat.relay.connection.Utils.StreamClosed
import com.ubergeek42.weechat.relay.connection.WebSocketConnection
import com.ubergeek42.weechat.relay.protocol.RelayObject
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.nio.channels.UnresolvedAddressException
import java.util.EnumSet
import kotlin.concurrent.Volatile

class RelayService : Service(), IObserver {
    @Root
    private val kitty: Kitty = Kitty.make()

    @Volatile
    var connection: RelayConnection? = null
    private var pingingPenguin: PingingPenguin? = null
    private var doge: Handler? = null // thread "doge" used for connecting/disconnecting

    @MainThread @Cat override fun onCreate() {
        // prepare handler that will run on a separate thread
        val handlerThread = HandlerThread("d-" + iteration)
        handlerThread.start()
        doge = Handler(handlerThread.getLooper())

        Network.get().register(this, Network.Callback {
            if (P.reconnect && state.contains(STATE.STARTED)) _start()
        })

        pingingPenguin = PingingPenguin(this)
        EventBus.getDefault().register(this)
    }

    @MainThread @Cat override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        P.saveStuff()
        Network.get().unregister(this)
        doge!!.post(Runnable { doge!!.getLooper().quit() })
    }

    @MainThread override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // todo synchronization?
    @Subscribe @Cat fun onEvent(event: SendMessageEvent) {
        connection!!.sendMessage(event.message)
    }

    // this method is called:
    //     * whenever app calls startService() (that means on each screen rotate)
    //     * when service is recreated by system after OOM kill. (intent = null)
    @MainThread @Cat override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || ACTION_START == intent.getAction()) {
            if (state.contains(STATE.STOPPED)) {
                P.loadConnectionPreferences()
                start()
            }
        } else if (ACTION_STOP == intent.getAction()) {
            if (!state.contains(STATE.STOPPED)) {
                stop()
            }
        }
        return START_STICKY
    }

    // called by user and when disconnected
    @MainThread @Cat @Synchronized private fun start() {
        Assert.assertThat(state).contains(STATE.STOPPED)
        changeState(EnumSet.of<STATE?>(STATE.STARTED))
        P.setServiceAlive(true)
        _start()
    }

    // called by ↑ and Connectivity
    @MainThread @Cat @Synchronized fun _start() {
        doge!!.removeCallbacksAndMessages(null)
        doge!!.post(object : Runnable {
            var reconnects: Int = 0
            var waiting: Boolean = false

            val willConnectSoon: String =
                getString(R.string.notifications__main__will_connect_in_n_seconds)
            val connectingNow: String = getString(R.string.notifications__main__connecting_now)

            @WorkerThread override fun run() {
                if (state.contains(STATE.AUTHENTICATED)) {
                    P.connectionSurelyPossibleWithCurrentPreferences = true
                    return
                }
                if (waiting) waitABit() else connectNow()
                waiting = !waiting
            }

            @WorkerThread fun connectNow() {
                showMainNotification(this@RelayService, connectingNow)
                when (connect()) {
                    TRY.LATER      -> {}
                    TRY.IMPOSSIBLE -> {
                        if (!P.connectionSurelyPossibleWithCurrentPreferences) {
                            stop()
                            break
                        }
                        doge!!.postDelayed(this, WAIT_BEFORE_WAIT_MESSAGE_DELAY * 1000)
                    }

                    TRY.POSSIBLE   -> doge!!.postDelayed(this,
                                                         WAIT_BEFORE_WAIT_MESSAGE_DELAY * 1000)
                }
            }

            @WorkerThread fun waitABit() {
                val delay: Long =
                    DELAYS[if (reconnects < DELAYS.size) reconnects else DELAYS.size - 1]
                val message = String.format(willConnectSoon, delay)
                showMainNotification(this@RelayService, message)
                reconnects++
                doge!!.postDelayed(this, delay * 1000)
            }
        })
    }

    // called by user and when there was a fatal exception while trying to connect
    @AnyThread @Cat @Synchronized fun stop() {
        Assert.assertThat(state).doesNotContain(STATE.STOPPED)
        if (state.contains(STATE.AUTHENTICATED)) goodbye()
        changeState(EnumSet.of<STATE?>(STATE.STOPPED))
        interrupt()
        stopSelf()
        P.setServiceAlive(false)
    }

    // called by ↑ and PingingPenguin
    // close whatever connection we have in a thread, may result in a call to onStateChanged
    @AnyThread @Cat @Synchronized fun interrupt() {
        doge!!.removeCallbacksAndMessages(null)
        doge!!.post(Runnable {
            doge!!.removeCallbacksAndMessages(null)
            if (connection != null) connection!!.disconnect()
        })
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private enum class TRY {
        POSSIBLE, LATER, IMPOSSIBLE
    }

    @WorkerThread @CatD(exit = true) private fun connect(): TRY {
        if (connection != null) connection!!.disconnect()

        if (!Network.get().hasProperty(Network.Property.CONNECTED)) {
            showMainNotification(this, getString(R.string.notifications__main__waiting_for_network))
            return TRY.LATER
        }

        val conn: IConnection?
        try {
            when (P.connectionType) {
                Constants.PREF_TYPE_SSH           -> conn = SSHConnection(
                    P.host, P.port, P.sshHost, P.sshPort, P.sshUser,
                    P.sshAuthenticationMethod, P.sshPassword, P.sshSerializedKey,
                    P.sshServerKeyVerifier
                )

                Constants.PREF_TYPE_SSL           -> conn = SimpleConnection(
                    P.host, P.port,
                    getInstance(this).makeSslAxolotl()
                )

                Constants.PREF_TYPE_WEBSOCKET     -> conn = WebSocketConnection(
                    P.host, P.port, P.wsPath, null
                )

                Constants.PREF_TYPE_WEBSOCKET_SSL -> conn = WebSocketConnection(
                    P.host, P.port, P.wsPath,
                    getInstance(this).makeSslAxolotl()
                )

                else                              -> conn = SimpleConnection(P.host, P.port, null)
            }
        } catch (e: Exception) {
            kitty.error("connect(): exception while creating connection", e)
            onException(e)
            return TRY.IMPOSSIBLE
        }

        connection = RelayConnection(conn, P.handshakeMethod, P.pass, this)
        connection!!.connect()
        return TRY.POSSIBLE
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// callbacks
    ////////////////////////////////////////////////////////////////////////////////////////////////
    enum class STATE {
        STOPPED,
        STARTED,
        AUTHENTICATED,
        LISTED,
    }

    var state: EnumSet<STATE?> = EnumSet.of<STATE?>(STATE.STOPPED)

    @WorkerThread @Synchronized override fun onStateChanged(s: RelayConnection.STATE) {
        if (state.contains(STATE.STOPPED)) return
        when (s) {
            RelayConnection.STATE.CONNECTING, RelayConnection.STATE.CONNECTED -> return
            RelayConnection.STATE.AUTHENTICATED                               -> {
                changeState(EnumSet.of<STATE?>(STATE.STARTED, STATE.AUTHENTICATED))
                showMainNotification(this,
                                     getString(R.string.notifications__main__connected_to,
                                               P.printableHost))
                hello()
            }

            RelayConnection.STATE.BUFFERS_LISTED                              -> changeState(EnumSet.of<STATE?>(
                STATE.STARTED,
                STATE.AUTHENTICATED,
                STATE.LISTED))

            RelayConnection.STATE.DISCONNECTED                                -> {
                if (!state.contains(STATE.AUTHENTICATED)) return  // continue connecting

                changeState(EnumSet.of<STATE?>(STATE.STOPPED))
                goodbye()
                if (P.reconnect) Weechat.runOnMainThread(Runnable { this.start() })
                else stopSelf()
            }
        }
    }

    private fun changeState(state: EnumSet<STATE?>) {
        this.state = state
        staticState = state
        EventBus.getDefault().postSticky(StateChangedEvent(state))
    }

    @WorkerThread @Cat private fun hello() {
        addOrRemoveActionForCurrentNotifications(true)
        if (P.pingEnabled) pingingPenguin!!.startPinging()
        onServiceAuthenticated()
        SyncAlarmReceiver.start(this)
    }

    //sync so that goodbye() never happens while state=auth but hello didn't run yet
    @AnyThread @Cat private fun goodbye() {
        SyncAlarmReceiver.stop(this)
        onServiceStopped()
        if (P.pingEnabled) pingingPenguin!!.stopPinging()
        P.saveStuff()
        addOrRemoveActionForCurrentNotifications(false)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    class ExceptionWrapper internal constructor(cause: Exception?, message: String?) :
            Exception(message) {
        init {
            initCause(cause)
        }
    }

    @WorkerThread override fun onException(e: Exception) {
        var e = e
        kitty.error("→ onException(%s)", e.javaClass.getSimpleName())
        if (state.contains(STATE.STOPPED)) return
        if (e is StreamClosed && (!state.contains(STATE.AUTHENTICATED))) e = ExceptionWrapper(e,
                                                                                              getString(
                                                                                                  R.string.error__connection__server_unexpectedly_closed_connection))
        else if (e is UnresolvedAddressException) e = ExceptionWrapper(e,
                                                                       getString(R.string.error__connection__could_not_resolve_address,
                                                                                 if (P.connectionType == Constants.PREF_TYPE_SSH) P.sshHost else P.host))
        EventBus.getDefault().post(Events.ExceptionEvent(e))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// status & life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////
    init {
        kitty.setPrefix((++iteration).toString())
    }

    @WorkerThread override fun onMessage(message: RelayMessage) {
        kitty.trace("→ onMessage(%s)", message.getID())
        if (state.contains(STATE.STOPPED)) return
        pingingPenguin!!.onMessage()
        val objects = if (message.getObjects() == null) NULL else message.getObjects()
        val id = message.getID()
        for (`object` in objects) handleMessage(`object`, id)
    }

    companion object {
        private var iteration = -1

        // action is one of ACTION_START or ACTION_STOP
        // see https://stackoverflow.com/a/47654126/1449683 regarding the version check
        @JvmStatic fun startWithAction(context: Context, action: String) {
            val intent = Intent(context, RelayService::class.java)
            intent.setAction(action)


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        const val ACTION_START: String = "com.ubergeek42.WeechatAndroid.START"
        const val ACTION_STOP: String = "com.ubergeek42.WeechatAndroid.STOP"

        ////////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////// connect/disconnect
        ////////////////////////////////////////////////////////////////////////////////////////////////
        private const val WAIT_BEFORE_WAIT_MESSAGE_DELAY: Long = 5
        private val DELAYS = longArrayOf(5, 15, 30, 60, 120, 300, 600, 900)

        @JvmField var staticState: EnumSet<STATE?>? =
            EnumSet.of<STATE?>(STATE.STOPPED) // probably don't use this

        ////////////////////////////////////////////////////////////////////////////////////////////////
        private val NULL = arrayOf<RelayObject?>(null)
    }
}
