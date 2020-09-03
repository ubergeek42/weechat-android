package com.ubergeek42.WeechatAndroid.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import java.lang.Thread.sleep
import kotlin.concurrent.thread

const val ACTION_UPLOAD = "com.ubergeek42.WeechatAndroid.UPLOAD"
const val NOTIFICATION_CHANNEL_UPLOAD = "upload"
const val NOTIFICATION_ID = 64

class UploadService : Service() {
    @Root private val kitty = Kitty.make()

    data class Upload(val uri: Uri, val type: String, val id: Int)

    @MainThread override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kitty.debug("received intent: action %s, type %s, data %s", intent?.action, intent?.type, intent?.data)
        if (intent?.action == ACTION_UPLOAD) {
            startUpload(Upload(intent.data!!, intent.type!!, startId))
        }
        return START_NOT_STICKY
    }

    @MainThread override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @MainThread fun startUpload(upload: Upload) {
        onUploadStarted(upload)
        thread {
            sleep(5000)
            Weechat.runOnMainThread { onUploadEnded(upload) }
        }
    }

    private val jobs = mutableSetOf<Int>()

    @MainThread fun onUploadStarted(upload: Upload) {
        kitty.info("starting upload %s", upload)
        jobs.add(upload.id)
        showNotification()
    }

    @MainThread fun onUploadEnded(upload: Upload) {
        kitty.info("upload %s done", upload)
        jobs.remove(upload.id)
        if (jobs.isNotEmpty()) {
            showNotification()
        } else {
            kitty.debug("stopping service")
            stopSelf()
        }
    }

    private fun showNotification() {
        startForeground(NOTIFICATION_ID, makeNotification())
    }

    private fun makeNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            makeNotificationOnApi26OrAbove()
        } else {
            TODO("VERSION.SDK_INT < O")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun makeNotificationOnApi26OrAbove(): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL_UPLOAD)
                .setContentTitle("Uploading")
                .setContentText("Uploading " + jobs.size + " files")
                .setSmallIcon(R.drawable.ic_notification_main)
                .build()
    }

    companion object {
        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                makeNotificationChannel()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun makeNotificationChannel() {
            val context = Weechat.applicationContext
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            NotificationChannel(NOTIFICATION_CHANNEL_UPLOAD,
                    "Uploads",
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                manager.createNotificationChannel(this)
            }
        }
    }
}