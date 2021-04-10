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
import com.ubergeek42.WeechatAndroid.relay.BufferList.syncHotlist
import com.ubergeek42.WeechatAndroid.upload.applicationContext

class SyncAlarmReceiver : BroadcastReceiver() {
    @MainThread override fun onReceive(context: Context, intent: Intent) {
        val authenticated = RelayService.staticState.contains(RelayService.STATE.AUTHENTICATED)
        if (authenticated) {
            syncHotlist()
        } else {
            unregister()
        }
    }

    companion object {
        private const val SYNC_EVERY_MS = 5 * 60 * 1000 // 5 minutes

        private val alarmManager = applicationContext
                .getSystemService(Context.ALARM_SERVICE) as AlarmManager

        private val pendingIntent = PendingIntent.getBroadcast(
                applicationContext, 0,
                Intent(applicationContext, SyncAlarmReceiver::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT)

        @JvmStatic @WorkerThread fun register() {
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + SYNC_EVERY_MS, SYNC_EVERY_MS.toLong(),
                    pendingIntent)
        }

        @JvmStatic @AnyThread fun unregister() {
            alarmManager.cancel(pendingIntent)
        }
    }
}