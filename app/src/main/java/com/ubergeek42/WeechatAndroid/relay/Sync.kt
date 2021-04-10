package com.ubergeek42.WeechatAndroid.relay

import android.app.AlarmManager
import android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.ubergeek42.WeechatAndroid.service.Events
import com.ubergeek42.WeechatAndroid.upload.applicationContext


class Sync(
    private val desyncDelayBuffer: Long,
    private val desyncDelayOpenBuffer: Long,
    private val desyncDelayGlobal: Long,
    private val relaySyncAlarmAdditionalDelay: Long,
) {
    fun start() { PowerBroadcastReceiver.register() }
    fun stop() { PowerBroadcastReceiver.unregister() }

    companion object {
        var instance: Sync? = null

        @JvmStatic fun get() = instance ?: Sync(
                desyncDelayBuffer = 10 * 30 * 1000L,
                desyncDelayOpenBuffer = 10 * 60 * 1000L,
                desyncDelayGlobal = 10 * 5 * 1000L,
                relaySyncAlarmAdditionalDelay = 1 * 1000L,
        ).also { instance = it }
    }

    enum class Flag {
        ActivityOpen,
        Charging,
    }

    private data class Info(
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

    private fun getGlobalCheckTime() = globalLastTouchedAt + desyncDelayGlobal

    private fun shouldScheduleGlobalDesync() =
            !globalFlags.contains(Flag.ActivityOpen) &&
            !globalFlags.contains(Flag.Charging)

    private fun shouldKeepGloballySyncing() =
            globalFlags.contains(Flag.ActivityOpen) ||
            globalFlags.contains(Flag.Charging) ||
                    getGlobalCheckTime() > System.currentTimeMillis()

    private fun Info.getCheckTime() = lastTouchedAt +
            if (open) desyncDelayOpenBuffer else desyncDelayBuffer

    private fun Info.shouldScheduleDesync() = !watched

    private fun Info.shouldSync() = watched

    private fun Info.shouldKeepSyncing() = shouldKeepGloballySyncing() ||
            getCheckTime() > System.currentTimeMillis()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Synchronized fun addGlobalFlag(flag: Flag) {
        globalFlags = globalFlags + flag
        onGlobalFlagsChanged()

        if (!shouldScheduleGlobalDesync()) RelaySyncAlarm.unschedule()
    }

    @Synchronized fun removeGlobalFlag(flag: Flag) {
        val now = System.currentTimeMillis()

        if (flag == Flag.ActivityOpen) globalLastTouchedAt = now

        globalFlags = globalFlags - flag
        onGlobalFlagsChanged()

        if (shouldScheduleGlobalDesync()) {
            val checkAt = getGlobalCheckTime()
            if (checkAt > now) RelaySyncAlarm.schedule(checkAt - now) else onSyncAlarm()
        }
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

    @Synchronized fun onPowerConnectionChanged(hasPower: Boolean) {
        if (hasPower) addGlobalFlag(Flag.Charging) else removeGlobalFlag(Flag.Charging)
    }

    fun onSyncAlarm() {
        val timeDelta = recheckAll()
        if (timeDelta == Long.MAX_VALUE) {
            RelaySyncAlarm.unschedule()
        } else {
            RelaySyncAlarm.schedule(timeDelta + relaySyncAlarmAdditionalDelay)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun onGlobalFlagsChanged() {
        val shouldSyncGlobally = shouldKeepGloballySyncing()
        if (syncedGlobally != shouldSyncGlobally) {
            if (shouldSyncGlobally) sync() else desync()
        }
    }

    private fun updateInfo(pointer: Long, block: (Info) -> Info) {
        val oldInfo = pointerToInfo[pointer] ?: Info.default
        val newInfo = block(oldInfo)
        onInfoChanged(pointer, newInfo)
        pointerToInfo = pointerToInfo.toMutableMap().also { it[pointer] = newInfo }
    }

    private fun onInfoChanged(pointer: Long, maybeNewInfo: Info? = null) {
        val newInfo = maybeNewInfo ?: pointerToInfo[pointer] ?: Info.default
        val syncedBefore = syncedPointers.contains(pointer)

        if (syncedBefore) {
            if (!newInfo.shouldKeepSyncing()) desync(pointer)
        } else {
            if (newInfo.shouldSync()) sync(pointer)
        }
    }

    @Synchronized private fun recheckAll(): Long {
        val now = System.currentTimeMillis()
        var minimalCheckAt = Long.MAX_VALUE

        onGlobalFlagsChanged()

        if (shouldScheduleGlobalDesync()) {
            val checkAt = getGlobalCheckTime()
            if (checkAt > now) minimalCheckAt = checkAt
        }

        pointerToInfo.forEach { (pointer, info) ->
            onInfoChanged(pointer, info)

            if (info.shouldScheduleDesync()) {
                val checkAt = info.getCheckTime()
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


// todo use a variant of set with OnAlarmListener @ api 24+
class RelaySyncAlarm : BroadcastReceiver() {
    companion object {
        private val alarmManager = applicationContext.
                getSystemService(Context.ALARM_SERVICE) as AlarmManager

        private val pendingIntent = PendingIntent.getBroadcast(
                applicationContext, 13,
                Intent(applicationContext, RelaySyncAlarm::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT)

        fun schedule(timeDelta: Long) {
            require(timeDelta >= 0)
            alarmManager.set(ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeDelta,
                    pendingIntent)
        }

        fun unschedule() {
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Sync.get().onSyncAlarm()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


object PowerBroadcastReceiver : BroadcastReceiver() {
    fun register() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        applicationContext.registerReceiver(this, intentFilter)

        val batteryStatus: Intent? = applicationContext.
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            Sync.get().onPowerConnectionChanged(isCharging)
        }
    }

    fun unregister() {
        applicationContext.unregisterReceiver(this)
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            Sync.get().onPowerConnectionChanged(true)
        } else if (intent.action == Intent.ACTION_POWER_DISCONNECTED){
            Sync.get().onPowerConnectionChanged(false)
        }
    }
}