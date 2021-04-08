package com.ubergeek42.WeechatAndroid.relay

import android.app.AlarmManager
import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.upload.applicationContext


private const val DESYNC_THRESHOLD_BUFFER = 10 * 1000L  // 10 seconds
private const val DESYNC_THRESHOLD_GLOBAL = 5 * 1000L   // 5 seconds
private const val DESYNC_THRESHOLD_ADDITION = 1000L     // 1 second


class Sync {
    companion object {
        var instance: Sync? = null
        @JvmStatic fun get() = instance ?: Sync().also { instance = it }
    }

    enum class Flag {
        ActivityOpen,
        Charging,
    }

    data class Info(
        val open: Boolean,
        val watched: Boolean,
        val lastTouchedAt: Long,
    ) {
        companion object {
            val default = Info(open = false, watched = false, 0)
        }
    }

    private var globalFlags = setOf<Flag>()
    private var globalLastTouchedAt = 0L

    private var pointerToInfo = mapOf<Long, Info>()

    private var syncedPointers = setOf<Long>()
    private var syncedGlobally = false

    private fun shouldScheduleDesync() =
            !globalFlags.contains(Flag.ActivityOpen) &&
            !globalFlags.contains(Flag.Charging)

    private fun shouldSync() =
            globalFlags.contains(Flag.ActivityOpen) ||
            globalFlags.contains(Flag.Charging) ||
            globalLastTouchedAt + DESYNC_THRESHOLD_GLOBAL > System.currentTimeMillis()

    private fun Info.shouldScheduleDesync() = !watched

    private fun Info.shouldSync() = watched ||
            lastTouchedAt + DESYNC_THRESHOLD_BUFFER > System.currentTimeMillis()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Synchronized fun addGlobalFlag(flag: Flag) {
        globalFlags = globalFlags + flag
        onGlobalFlagsChanged()
    }

    @Synchronized fun removeGlobalFlag(flag: Flag) {
        if (flag == Flag.ActivityOpen) {
            globalLastTouchedAt = System.currentTimeMillis()
        }

        globalFlags = globalFlags - flag
        onGlobalFlagsChanged()

        if (shouldScheduleDesync()) RelaySyncAlarm.tick()
    }

    @Synchronized fun setOpen(pointer: Long, open: Boolean) {
        updateInfo(pointer) { info -> info.copy(open = open) }
    }

    @Synchronized fun setWatched(pointer: Long, watched: Boolean) {
        updateInfo(pointer) { info -> info.copy(watched = watched) }
    }

    @Synchronized fun touch(pointer: Long) {
        updateInfo(pointer) { info -> info.copy(lastTouchedAt = System.currentTimeMillis()) }
    }

    private fun updateInfo(pointer: Long, block: (Info) -> Info) {
        val oldInfo = pointerToInfo[pointer] ?: Info.default
        val newInfo = block(oldInfo)
        onInfoChanged(pointer, newInfo)
        pointerToInfo = pointerToInfo.toMutableMap().also { it[pointer] = newInfo }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onGlobalFlagsChanged() {
        val shouldSyncGlobally = shouldSync()
        if (syncedGlobally != shouldSyncGlobally) {
            if (shouldSyncGlobally) sync() else desync()
        }
    }

    private fun onInfoChanged(pointer: Long, maybeNewInfo: Info? = null) {
        val newInfo = maybeNewInfo ?: pointerToInfo[pointer] ?: Info.default
        val syncedBefore = syncedPointers.contains(pointer)
        val shouldSync = newInfo.shouldSync()

        if (syncedBefore != shouldSync) {
            if (shouldSync) sync(pointer) else desync(pointer)
        }
    }

    // todo postpone individual buffer desyncs until after global desync
    @Synchronized fun recheckAll(): Long {
        val now = System.currentTimeMillis()
        var minimalCheckAt = Long.MAX_VALUE

        onGlobalFlagsChanged()

        if (shouldScheduleDesync()) {
            val checkAt = globalLastTouchedAt + DESYNC_THRESHOLD_GLOBAL
            if (checkAt > now) minimalCheckAt = checkAt
        }

        pointerToInfo.forEach { (pointer, info) ->
            onInfoChanged(pointer, info)

            if (info.shouldScheduleDesync()) {
                val checkAt = info.lastTouchedAt + DESYNC_THRESHOLD_BUFFER
                if (checkAt > now) minimalCheckAt = minOf(checkAt, minimalCheckAt)
            }
        }

        return if (minimalCheckAt == Long.MAX_VALUE) Long.MAX_VALUE else minimalCheckAt - now
    }

    private fun sync() {
        if (syncedGlobally) return
        syncedGlobally = true
        Events.SendMessageEvent.fire(listOf(
                LastLinesSpec.request,
                LastReadLineSpec.request,
                HotlistSpec.request,
                "sync"
        ).joinToString("\n"))
    }

    private fun desync() {
        if (!syncedGlobally) return
        syncedGlobally = false
        Events.SendMessageEvent.fire("desync\nsync * buffers")
        BufferList.buffers.forEach {
            if (!syncedPointers.contains(it.pointer)) it.onDesynchronized()
        }
    }

    private fun sync(pointer: Long) {
        syncedPointers = syncedPointers + pointer
        Events.SendMessageEvent.fire("sync ${pointer.as0x}")
        BufferList.syncHotlist()
    }

    private fun desync(pointer: Long) {
        syncedPointers = syncedPointers - pointer
        Events.SendMessageEvent.fire("desync ${pointer.as0x}")
        if (!syncedGlobally) BufferList.findByPointer(pointer)?.onDesynchronized()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

private val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

// todo use a variant of set with OnAlarmListener @ api 24+
class RelaySyncAlarm : BroadcastReceiver() {
    companion object {
        private val pendingIntent = PendingIntent.getBroadcast(
                applicationContext, 13,
                Intent(applicationContext, RelaySyncAlarm::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT)

        private fun schedule(timeDelta: Long) {
            require(timeDelta >= 0)
            alarmManager.set(ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeDelta + DESYNC_THRESHOLD_ADDITION,
                    pendingIntent)
        }

        private fun unschedule() {
            alarmManager.cancel(pendingIntent)
        }

        fun tick() {
            val timeDelta = Sync.get().recheckAll()
            if (timeDelta == Long.MAX_VALUE) {
                unschedule()
            } else {
                schedule(timeDelta)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        tick()
    }
}