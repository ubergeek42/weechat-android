package com.ubergeek42.WeechatAndroid.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

const val ACTION_UPLOAD = "com.ubergeek42.WeechatAndroid.UPLOAD"
const val NOTIFICATION_CHANNEL_UPLOAD = "upload"
const val NOTIFICATION_ID = 64

class UploadService : Service() {
    @Root private val kitty = Kitty.make()

    @MainThread override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    @MainThread override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        val service = this@UploadService
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class State { UPLOADING_DETERMINATE, UPLOADING_INDETERMINATE, FINISHED, NOT_FINISHED }

    @MainThread fun update() {
        val numberOfUploads = uploaders.size
        val transferredBytes = uploaders.map { it.transferredBytes }.sum()
        val totalBytes = uploaders.map { it.totalBytes }.sum()
        val ratio = transferredBytes fdiv totalBytes

        val state = if (numberOfUploads == 0) {
                        main(delay = 3000) { if (uploaders.size == 0) stopSelf() }
                        if (lastRemovedUploader?.state == Uploader.State.FAILED) State.NOT_FINISHED else State.FINISHED
                    } else {
                        if (ratio == 1f) State.UPLOADING_INDETERMINATE else State.UPLOADING_DETERMINATE
                    }
        showNotification(state, numberOfUploads, ratio, totalBytes)
    }

    private fun showNotification(state: State, numberOfUploads: Int, ratio: Float, totalBytes: Long) {
        val (title, icon) = when(state) {
            State.UPLOADING_DETERMINATE -> Pair("Uploading $numberOfUploads files", R.drawable.ic_notification_uploading)
            State.UPLOADING_INDETERMINATE -> Pair("Uploading $numberOfUploads files", R.drawable.ic_notification_uploading)
            State.FINISHED -> Pair("Upload finished", R.drawable.ic_notification_upload_done)
            State.NOT_FINISHED -> Pair("Upload not finished", R.drawable.ic_notification_upload_cancelled)
        }

        val percentage = (ratio * 100).toInt()
        val size = humanizeSize(totalBytes.toFloat())
        val text = if (numberOfUploads > 0) "$percentage of $size" else null

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_UPLOAD)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setOngoing(true)

        if (numberOfUploads > 0) {
            builder.setContentText("$percentage% of $size")
            builder.setProgress(100, percentage, state == State.UPLOADING_INDETERMINATE)
        }

        val notification = builder.build()

        limiter.post {
            kitty.debug("showNotification($state, $numberOfUploads, $ratio, $totalBytes)")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // at the time you can post notifications updates at the rate of roughly 5 per second
    // see DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE here and the relevant rate algorithm here:
    // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/notification/NotificationManagerService.java
    // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/notification/RateEstimator.java
    private val limiter = DelayingLimiter(200)

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

        ////////////////////////////////////////////////////////////////////////////////////////////

        private val uploaders = mutableSetOf<Uploader>()
        private var lastRemovedUploader: Uploader? = null

        @MainThread fun onUploadStarted(uploader: Uploader) {
            Intent(applicationContext, UploadService::class.java).apply {
                action = Intent.ACTION_SEND
                data = uploader.suri.uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                suppress<SecurityException> { applicationContext.startService(this) }
            }

            uploaders.add(uploader)
            updateService()
        }

        fun onUploadRemoved(uploader: Uploader) {
            lastRemovedUploader = uploader
            uploaders.remove(uploader)
            updateService()
        }

        fun onUploadProgress() {
            updateService()
        }

        private val bindIntent = Intent(applicationContext, UploadService::class.java)

        private fun updateService() {
            applicationContext.bindService(bindIntent,
                    object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            (service as LocalBinder).service.update()
                            applicationContext.unbindService(this)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) { /* ignored */ }
                    }, Context.BIND_AUTO_CREATE)
        }
    }
}