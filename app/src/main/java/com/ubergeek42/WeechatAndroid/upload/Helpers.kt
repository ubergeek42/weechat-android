package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.os.PowerManager
import androidx.annotation.MainThread
import com.ubergeek42.WeechatAndroid.Weechat
import java.lang.System.currentTimeMillis
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.absoluteValue


val applicationContext: Context = Weechat.applicationContext
val resolver = applicationContext.contentResolver!!


@OptIn(ExperimentalContracts::class)
inline fun <T : Any> T.lock(func: (T.() -> Unit)) {
    contract { callsInPlace(func, InvocationKind.EXACTLY_ONCE) }
    synchronized(this) {
        func(this)
    }
}

// this will run stuff on main thread
fun main(delay: Long = 0L, f: () -> Unit) = Weechat.runOnMainThread({ f() }, delay)

// for use in "${pi.format(2)" where pi is Float
fun Float.format(digits: Int) = "%.${digits}f".format(this)

// for floating division of integers
infix fun Long.fdiv(i: Long): Float = this / i.toFloat();

inline fun <reified T : Throwable> suppress(f: () -> Unit) {
    try {
        f()
    } catch (t: Throwable) {
        if (t !is T) throw t
        t.printStackTrace()
    }
}

private var wakeLockCounter = 0
fun <R> wakeLock(tag: String, f: () -> R): R {
    val service = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    val wakeLock = service.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "com.ubergeek42.WeechatAndroid::$tag-${wakeLockCounter++}")
    try {
        wakeLock.acquire(30 * 60 * 1000L) // up to 30 minutes
        return f()
    } finally {
        wakeLock.release()
    }
}

private val suffixes = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")

fun humanizeSize(size: Float, thousands: Int = 0): String {
    return if (size < 500) {
        size.format(if (size < 1) 1 else 0) + suffixes[thousands]
    } else {
        humanizeSize(size / 1000, thousands + 1)
    }
}


// the purpose of this is to limit progress updates of a value by skipping some updates
// step() will return true if the listeners should be notified of an update. this will happen if:
//   * value changed, and:
//   * value reaches min or max value, or
//   * value change exceeds value threshold and time since last update exceeds time threshold (ms)
class SkippingLimiter(
    val min: Float,
    val max: Float,
    val valueThreshold: Float,
    val timeThreshold: Long     // milliseconds
) {
    var value = -1F
    var lastUpdate = -1L

    fun reset() {
        value = -1F
        lastUpdate = -1L
    }

    @Suppress("RedundantIf")
    fun step(value: Float): Boolean {
        val currentTime = currentTimeMillis()

        val emit = if (value == this.value) {
                       false
                   } else if (value <= min || value >= max) {
                       true
                   } else if (currentTime - lastUpdate < timeThreshold) {
                       false
                   } else if ((value - this.value).absoluteValue > valueThreshold) {
                       true
                   } else {
                       false
                   }

        if (emit) {
            this.value = value
            this.lastUpdate = currentTime
        }

        return emit
    }
}


// this also limits updates, but it does so by posting runnables at the specified intervals
// on the main thread. only the last posted runnable is run, others are skipped
class DelayingLimiter(
    private val interval: Long
) {
    private var lastUpdate = -1L
    private var runnable: (() -> Unit)? = null

    @MainThread fun post(runnable: () -> Unit) {
        val delta = currentTimeMillis() - lastUpdate

        if (delta >= interval) {
            this.runnable = runnable
            tick()
        } else {
            if (this.runnable == null) main(delay = interval - delta) { tick() }
            this.runnable = runnable
        }
    }

    @MainThread private fun tick() {
        runnable?.let {
            it()
            lastUpdate = currentTimeMillis()
            runnable = null
        }
    }
}