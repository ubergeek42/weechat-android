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
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.dialogs.createScrollableDialog
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


@Root private val kitty: Kitty = Kitty.make()


private var staticPingingPenguin: PingingPenguin? = null  // Oh well


class PingingPenguin(val relayService: RelayService) {
    @WorkerThread fun startPinging() {
        staticPingingPenguin = this
        scheduleTick(Tick.Regular(), lastMessageReceivedAt + P.pingIdleTime)
    }

    @AnyThread fun stopPinging() {
        staticPingingPenguin = null
        unschedulePings()
    }

    @Volatile private var lastMessageReceivedAt: Long = 0
    @Volatile private var lastTick: Tick = Tick.Regular()

    @WorkerThread fun onMessage() {
        lastMessageReceivedAt = now()
    }

    @MainThread fun onTick() {
        if (!relayService.state.contains(RelayService.STATE.AUTHENTICATED)) return

        if (lastMessageReceivedAt > lastTick.scheduledAt) {
            scheduleTick(Tick.Regular(), lastMessageReceivedAt + P.pingIdleTime)
        } else if (lastTick is Tick.Regular) {
            kitty.info("Last message was received too long ago, sending a ping")
            Events.SendMessageEvent.fire("ping")
            scheduleTick(Tick.LastMessageTooOld(), now() + P.pingTimeout)
        } else {
            kitty.info("No messages received since ping was sent, disconnecting")
            relayService.interrupt()
        }
    }

    private fun scheduleTick(tick: Tick, at: Long) {
        lastTick = tick

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            kitty.error("Can't schedule exact alarms, so pinging mechanism won't work")
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pendingPingIntent)
        }
    }
}


private sealed class Tick {
    val scheduledAt = now()

    class Regular : Tick()
    class LastMessageTooOld : Tick()
}


class PingBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        staticPingingPenguin?.onTick()
    }
}


private fun unschedulePings() {
    PendingIntent.getBroadcast(applicationContext, 0, pingIntent,
                               PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarmManager.cancel(it) }
}


private fun now() = SystemClock.elapsedRealtime()

private val pingIntent = Intent(applicationContext, PingBroadcastReceiver::class.java)

private val pendingPingIntent = PendingIntent.getBroadcast(applicationContext, 0, pingIntent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

private val alarmManager = applicationContext.getSystemService<AlarmManager>()!!

@RequiresApi(Build.VERSION_CODES.S)
class ExactAlarmPermissionRationaleDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) = requireActivity().run {
        createScrollableDialog {
            setTitle(R.string.dialog__alarm_permission_for_ping__title)
            setText(R.string.dialog__alarm_permission_for_ping__text)
            setPositiveButton(R.string.dialog__notification_permission__positive_button) {
                startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
fun shouldRequestExactAlarmPermission() = P.pingEnabled && !alarmManager.canScheduleExactAlarms()

@RequiresApi(Build.VERSION_CODES.S)
@Cat fun FragmentActivity.showAlarmPermissionRationaleDialog() {
    ExactAlarmPermissionRationaleDialogFragment()
        .show(supportFragmentManager, "alarm-permission-for-ping-dialog")
}
