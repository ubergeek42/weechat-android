package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import com.ubergeek42.WeechatAndroid.Weechat


val applicationContext: Context = Weechat.applicationContext
val resolver = applicationContext.contentResolver!!


// this will run stuff on main thread
fun main(f: () -> Unit) = Weechat.runOnMainThread { f() }

// for use in "${pi.format(2)" where pi is Float
fun Float.format(digits: Int) = "%.${digits}f".format(this)

// for floating division of integers
infix fun Long.fdiv(i: Long): Float = this / i.toFloat();