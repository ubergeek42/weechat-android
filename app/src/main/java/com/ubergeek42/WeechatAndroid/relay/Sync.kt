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
import com.ubergeek42.WeechatAndroid.service.RelayService
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.cats.Cat
import com.ubergeek42.cats.CatD
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


var syncManager = Sync(
        desyncDelayBuffer = 60 * 1000L,
        desyncDelayOpenBuffer = 30 * 1000L,
        desyncDelayGlobal = 10 * 1000L,
        relaySyncAlarmAdditionalDelay = 1 * 1000L)


class Sync(
    private val desyncDelayBuffer: Long,
    private val desyncDelayOpenBuffer: Long,
    private val desyncDelayGlobal: Long,
    private val relaySyncAlarmAdditionalDelay: Long,
) {
    companion object {
        @Root private val kitty: Kitty = Kitty.make("Sync")
    }

    private var started = false

    @Cat fun start() {
        PowerBroadcastReceiver.register()
        Events.SendMessageEvent.fire(
                BufferSpec.listBuffersRequest + "\n" +
                "sync * buffers")
        started = true
        recheckEverything()
        HotlistSyncAlarm.schedule()
    }

    @Cat fun stop() {
        HotlistSyncAlarm.unschedule()
        PowerBroadcastReceiver.unregister()
        RelaySyncAlarm.unscheduleIfScheduled()

        syncedGlobally = false
        syncedPointers = setOf()
        started = false
    }

    enum class Flag {
        ActivityOpen,
        Charging,
    }

    private data class Info(
        val pointer: Long,
        val open: Boolean,
        val watched: Boolean,
        val lastTouchedAt: Long,
    )

    private var globalFlags = setOf<Flag>()
    private var globalLastTouchedAt = 0L

    private var pointerToInfo = mapOf<Long, Info>()

    private var syncedPointers = setOf<Long>()
    private var syncedGlobally = false

    private fun getGlobalDesyncTime() = globalLastTouchedAt + desyncDelayGlobal

    private fun shouldScheduleGlobalDesync() =
            !globalFlags.contains(Flag.ActivityOpen) &&
            !globalFlags.contains(Flag.Charging) &&
            syncedGlobally

    private fun shouldKeepGloballySyncing() =
            globalFlags.contains(Flag.ActivityOpen) ||
            globalFlags.contains(Flag.Charging) ||
                    getGlobalDesyncTime() > System.currentTimeMillis()

    private fun Info.getDesyncTime() = lastTouchedAt +
            if (open) desyncDelayOpenBuffer else desyncDelayBuffer

    private fun Info.shouldScheduleDesync() = !watched &&
            syncedPointers.contains(pointer)

    private fun Info.shouldKeepSyncing() = getDesyncTime() > System.currentTimeMillis()

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Synchronized fun getDesiredHotlistSyncInterval(): Long {
        return when {
            globalFlags.contains(Flag.ActivityOpen) -> 30.s_to_ms
            syncedGlobally -> 5.m_to_ms
            syncedPointers.isEmpty() -> 10.m_to_ms
            else -> 30.m_to_ms
        }
    }

    fun getDesiredPingInterval() = getDesiredHotlistSyncInterval()

    ////////////////////////////////////////////////////////////////////////////////////////////////


    @Synchronized fun addGlobalFlag(flag: Flag) {
        globalFlags = globalFlags + flag
        recheckEverything()
    }

    @Synchronized fun removeGlobalFlag(flag: Flag) {
        if (flag == Flag.ActivityOpen) globalLastTouchedAt = System.currentTimeMillis()

        globalFlags = globalFlags - flag
        recheckEverything()
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

    @Cat @Synchronized fun recheckEverything() {
        if (!started) return
        if (!shouldKeepGloballySyncing()) syncDesyncIndividualBuffersIfNeeded()
        globalSyncDesyncIfNeeded()
        scheduleUnscheduleDesyncIfNeeded()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun updateInfo(pointer: Long, block: (Info) -> Info) {
        val info = pointerToInfo[pointer] ?: Info(pointer, open = false, watched = false, lastTouchedAt = 0)
        pointerToInfo = pointerToInfo.toMutableMap().also { it[pointer] = block(info) }
    }

    private fun globalSyncDesyncIfNeeded() {
        val shouldSync = shouldKeepGloballySyncing()
        if (syncedGlobally != shouldSync) {
            if (shouldSync) sync() else desync()
        }
    }

    private fun syncDesyncIndividualBuffersIfNeeded() {
        pointerToInfo.forEach { (pointer, info) ->
            val shouldSync = info.shouldKeepSyncing()
            val synced = syncedPointers.contains(pointer)
            if (synced != shouldSync) {
                if (shouldSync) sync(pointer) else desync(pointer)
            }
        }
    }

    private fun scheduleUnscheduleDesyncIfNeeded() {
        val now = System.currentTimeMillis()
        var finalDesyncTime = Long.MAX_VALUE

        if (shouldScheduleGlobalDesync()) {
            finalDesyncTime = getGlobalDesyncTime()
        } else if (!syncedGlobally) {
            pointerToInfo.forEach { (_, info) ->
                if (info.shouldScheduleDesync()) {
                    val desyncTime = info.getDesyncTime()
                    if (desyncTime < finalDesyncTime) finalDesyncTime = desyncTime
                }
            }
        }

        when {
            finalDesyncTime == Long.MAX_VALUE -> RelaySyncAlarm.unscheduleIfScheduled()
            finalDesyncTime <= now -> recheckEverything()
            else -> RelaySyncAlarm.schedule(finalDesyncTime - now + relaySyncAlarmAdditionalDelay)
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @CatD fun sync() {
        if (syncedGlobally) return
        syncedGlobally = true
        Events.SendMessageEvent.fire(listOf(
                LastLinesSpec.request,
                LastReadLineSpec.request,
                HotlistSpec.request,
                "sync"
        ).joinToString("\n"))
    }

    @CatD private fun desync() {
        if (!syncedGlobally) return
        syncedGlobally = false
        Events.SendMessageEvent.fire("desync\nsync * buffers")
        BufferList.buffers.forEach {
            if (!syncedPointers.contains(it.pointer)) it.onDesynchronized()
        }
    }

    @CatD private fun sync(pointer: Long) {
        syncedPointers = syncedPointers + pointer
        Events.SendMessageEvent.fire("sync ${pointer.as0x}")
        BufferList.syncHotlist()    // todo optimize
    }

    @CatD(linger = true) private fun desync(pointer: Long) {
        syncedPointers = syncedPointers - pointer
        kitty.debug("%s remain", syncedPointers.size)
        Events.SendMessageEvent.fire("desync ${pointer.as0x}")
        if (!syncedGlobally) BufferList.findByPointer(pointer)?.onDesynchronized()
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private val alarmManager = applicationContext.
        getSystemService(Context.ALARM_SERVICE) as AlarmManager

// note: `val kitty` on a Companion object actually becomes a static on the enclosing class
// as a result, Companion methods annotated with @Cat don't pick it up.

// todo use a variant of set with OnAlarmListener @ api 24+
class RelaySyncAlarm : BroadcastReceiver() {
    companion object {
        private val pendingIntent = PendingIntent.getBroadcast(
                applicationContext, 13,
                Intent(applicationContext, RelaySyncAlarm::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT)

        private var scheduled = false

        @Cat @JvmStatic fun schedule(timeDelta: Long) {
            require(timeDelta >= 0)
            alarmManager.set(ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeDelta,
                    pendingIntent)
            scheduled = true
        }

        @Cat @JvmStatic private fun unschedule() {
            alarmManager.cancel(pendingIntent)
        }

        fun unscheduleIfScheduled() {
            if (scheduled) {
                unschedule()
                scheduled = false
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        syncManager.recheckEverything()
        scheduled = false
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////

class HotlistSyncAlarm : BroadcastReceiver() {
    companion object {
        private val pendingIntent = PendingIntent.getBroadcast(
                applicationContext, 0,
                Intent(applicationContext, HotlistSyncAlarm::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT)

        @Cat private fun schedule(timeDelta: Long) {
            require(timeDelta >= 0)
            alarmManager.set(ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeDelta,
                    pendingIntent)
        }

        @Cat fun unschedule() {
            alarmManager.cancel(pendingIntent)
        }

        fun schedule() {
            schedule(syncManager.getDesiredHotlistSyncInterval())
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val authenticated = RelayService.staticState.contains(RelayService.STATE.AUTHENTICATED)
        if (authenticated) {
            BufferList.syncHotlist()
            schedule()
        } else {
            unschedule()
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


object PowerBroadcastReceiver : BroadcastReceiver() {
    @Root private val kitty: Kitty = Kitty.make("Sync.PowerBroadcastReceiver")

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
            dispatchPowerConnectionChanged(isCharging)
        }
    }

    fun unregister() {
        applicationContext.unregisterReceiver(this)
    }

    @Cat fun dispatchPowerConnectionChanged(isCharging: Boolean) {
        if (isCharging) {
            syncManager.addGlobalFlag(Sync.Flag.Charging)
        } else {
            syncManager.removeGlobalFlag(Sync.Flag.Charging)
        }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            dispatchPowerConnectionChanged(true)
        } else if (intent.action == Intent.ACTION_POWER_DISCONNECTED){
            dispatchPowerConnectionChanged(false)
        }
    }
}


private inline val Int.s_to_ms get() = this * 1000L
private inline val Int.m_to_ms get() = this * 60L * 1000L