package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.os.PowerManager
import com.ubergeek42.WeechatAndroid.Weechat
import java.lang.System.currentTimeMillis


val applicationContext: Context = Weechat.applicationContext
val resolver = applicationContext.contentResolver!!


// this will run stuff on main thread
fun main(f: () -> Unit) = Weechat.runOnMainThread { f() }

// for use in "${pi.format(2)" where pi is Float
fun Float.format(digits: Int) = "%.${digits}f".format(this)

// for floating division of integers
infix fun Long.fdiv(i: Long): Float = this / i.toFloat();


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


// the purpose of this is to limit progress updates of a value.
// step() will return true if the listeners should be notified of an update. this will happen if:
//   * value changed, and:
//   * value reaches min or max value, or
//   * value change exceeds value threshold and time since last update exceeds time threshold (ms)
class Throttle(
    val min: Float,
    val max: Float,
    val valueThreshold: Float,
    val timeThreshold: Long     // milliseconds
) {
    var value = -1F
    var lastUpdate = -1L

    @Suppress("RedundantIf")
    fun step(value: Float): Boolean {
        val currentTime = currentTimeMillis()

        val emit = if (value == this.value) {
                       false
                   } else if (value <= min || value >= max) {
                       true
                   } else if (currentTime - lastUpdate < timeThreshold) {
                       false
                   } else if (value - this.value > valueThreshold) {
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