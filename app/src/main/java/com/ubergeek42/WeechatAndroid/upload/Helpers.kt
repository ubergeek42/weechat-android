package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import android.os.PowerManager
import com.ubergeek42.WeechatAndroid.Weechat


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