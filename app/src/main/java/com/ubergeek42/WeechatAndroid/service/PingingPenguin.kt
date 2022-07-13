// Copyright 2014 Matthew Horan
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ubergeek42.WeechatAndroid.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


@Root private val kitty: Kitty = Kitty.make()


private var staticPingingPenguin: PingingPenguin? = null  // Oh well


class PingingPenguin(val relayService: RelayService) {
    @WorkerThread fun startPinging() {
        staticPingingPenguin = this
        schedulePingAt(now() + P.pingIdleTime)
    }

    @AnyThread fun stopPinging() {
        staticPingingPenguin = null
        unschedulePings()
    }

    @Volatile private var lastMessageReceivedAt = 0L
    private var sentPing = false

    @WorkerThread fun onMessage() {
        lastMessageReceivedAt = now()
    }

    @MainThread fun onTimesUp() {
        if (!relayService.state.contains(RelayService.STATE.AUTHENTICATED)) return

        if (now() - lastMessageReceivedAt > P.pingIdleTime) {
            if (sentPing) {
                kitty.info("no message received, disconnecting")
                relayService.interrupt()
            } else {
                kitty.debug("last message too old, sending ping")
                Events.SendMessageEvent.fire("ping")
                sentPing = true
                schedulePingAt(now() + P.pingTimeout)
            }
        } else {
            sentPing = false
            schedulePingAt(lastMessageReceivedAt + P.pingIdleTime)
        }
    }
}


class PingBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        staticPingingPenguin?.onTimesUp()
    }
}


private fun schedulePingAt(at: Long) {
    alarmManager?.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pendingPingIntent)
}


private fun unschedulePings() {
    PendingIntent.getBroadcast(applicationContext, 0, pingIntent,
                               PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarmManager?.cancel(it) }
}


private fun now() = SystemClock.elapsedRealtime()

private val pingIntent = Intent(applicationContext, PingBroadcastReceiver::class.java)

private val pendingPingIntent = PendingIntent.getBroadcast(applicationContext, 0, pingIntent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

private val alarmManager = applicationContext
        .getSystemService(Context.ALARM_SERVICE) as AlarmManager?
