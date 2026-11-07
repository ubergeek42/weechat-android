package com.ubergeek42.WeechatAndroid.utils

import android.content.ComponentCallbacks2
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ubergeek42.WeechatAndroid.BuildConfig


private val handler = Handler(Looper.getMainLooper())

private val TRIM_DELAY = if (BuildConfig.DEBUG) 60_000L else 30 * 60_000L


/**
 * Normally, the system will call `Application.onTrimMemory` itself. However:
 *   * On recent Android versions, only `TRIM_MEMORY_BACKGROUND` and `TRIM_MEMORY_UI_HIDDEN` are
 *     not deprecated. The other levels are not actually used.
 *   * We have a foreground service, and apparently this prevents the system
 *     from ever calling `onTrimMemory` on with `TRIM_MEMORY_BACKGROUND`.
 *
 * This manually calls `onTrimMemory` after some inactivity of the main activity.
 * This should propagate to other components that rely on `onTrimMemory`,
 * particulary Glide, and EmojiCompat.
 *
 * See https://issuetracker.google.com/issues/563496229
 */
fun AppCompatActivity.attachDelayedMemoryTrimmer() {
    val trimMemoryRunnable = Runnable {
        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    }

    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            handler.removeCallbacks(trimMemoryRunnable)
        }

        override fun onStop(owner: LifecycleOwner) {
            handler.postDelayed(trimMemoryRunnable, TRIM_DELAY)
        }
    })
}