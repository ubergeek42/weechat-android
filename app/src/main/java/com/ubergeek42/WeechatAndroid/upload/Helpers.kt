package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.os.PowerManager
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.WeechatAndroid.service.P
import com.ubergeek42.WeechatAndroid.utils.Toaster.Companion.ErrorToast
import java.lang.System.currentTimeMillis
import java.util.concurrent.CancellationException
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

inline val Long.f inline get() = this.toFloat()
inline val Int.l inline get() = this.toLong()
inline val Int.f inline get() = this.toFloat()
inline val Int.d inline get() = this.toDouble()
inline val Float.i inline get() = this.toInt()
inline val Double.i inline get() = this.toInt()
inline val Double.l inline get() = this.toLong()


// for floating division of integers
infix fun Long.fdiv(i: Long): Float = this / i.f

val Int.dp_to_px get() = (this * P._1dp).toInt()

// same as to for Pairs, but for triples
infix fun <A, B, C> Pair<A, B>.to(that: C): Triple<A, B, C> = Triple(this.first, this.second, that)

inline fun <reified T : Throwable> suppress(showToast: Boolean = false, f: () -> Unit) {
    try {
        f()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        if (t !is T) throw t
        if (showToast) ErrorToast.show(t)
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


// the purpose of this is to limit progress updates of a value by skipping some updates
// step() will return true if the listeners should be notified of an update. this will happen if:
//   * value changed, and:
//   * value reaches min or max value, or
//   * value change exceeds value threshold and time since last update exceeds time threshold (ms)
class SkippingLimiter(
    private val min: Float,
    private val max: Float,
    private val valueThreshold: Float,
    private val timeThreshold: Long     // milliseconds
) {
    private var value = -1F
    private var lastUpdate = -1L

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


//private val suffixes = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
//
//fun humanizeSize(size: Float, thousands: Int = 0): String {
//    return if (size < 500) {
//        size.format(if (size < 1) 1 else 0) + suffixes[thousands]
//    } else {
//        humanizeSize(size / 1000, thousands + 1)
//    }
//}


//// this also limits updates, but it does so by posting runnables at the specified intervals
//// on the main thread. only the last posted runnable is run, others are skipped
//class DelayingLimiter(
//    private val interval: Long
//) {
//    private var lastUpdate = -1L
//    private var runnable: (() -> Unit)? = null
//
//    @MainThread fun post(runnable: () -> Unit) {
//        val delta = currentTimeMillis() - lastUpdate
//
//        if (delta >= interval) {
//            this.runnable = runnable
//            tick()
//        } else {
//            if (this.runnable == null) main(delay = interval - delta) { tick() }
//            this.runnable = runnable
//        }
//    }
//
//    @MainThread private fun tick() {
//        runnable?.let {
//            it()
//            lastUpdate = currentTimeMillis()
//            runnable = null
//        }
//    }
//}