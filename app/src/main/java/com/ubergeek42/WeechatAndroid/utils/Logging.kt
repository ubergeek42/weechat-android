package com.ubergeek42.WeechatAndroid.utils

import android.util.Log
import cats.ModuleLogger
import cats.androidLogger

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@ModuleLogger val logger = androidLogger { packageName -> "App" }


fun jtrace(message: String) { Log.v("App", message) }
fun jtrace(e: Throwable, message: String) { Log.v("App", message, e) }
fun jtrace(message: () -> String) { Log.v("App", message()) }
fun jtrace(e: Throwable, message: () -> String) { Log.v("App", message(), e) }

fun jdebug(message: String) { Log.d("App", message) }
fun jdebug(e: Throwable, message: String) { Log.d("App", message, e) }
fun jdebug(message: () -> String) { Log.d("App", message()) }
fun jdebug(e: Throwable, message: () -> String) { Log.d("App", message(), e) }

fun jinfo(message: String) { Log.i("App", message) }
fun jinfo(e: Throwable, message: String) { Log.i("App", message, e) }
fun jinfo(message: () -> String) { Log.i("App", message()) }
fun jinfo(e: Throwable, message: () -> String) { Log.i("App", message(), e) }

fun jwarn(message: String) { Log.w("App", message) }
fun jwarn(e: Throwable, message: String) { Log.w("App", message, e) }
fun jwarn(message: () -> String) { Log.w("App", message()) }
fun jwarn(e: Throwable, message: () -> String) { Log.w("App", message(), e) }

fun jerr(message: String) { Log.e("App", message) }
fun jerr(e: Throwable, message: String) { Log.e("App", message, e) }
fun jerr(message: () -> String) { Log.e("App", message()) }
fun jerr(e: Throwable, message: () -> String) { Log.e("App", message(), e) }