package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri
import androidx.annotation.MainThread
import okhttp3.*
import okio.BufferedSink
import okio.IOException
import okio.source
import kotlin.concurrent.thread


interface ProgressListener {
    fun onStarted()
    fun onProgress(read: Long, total: Long)
    fun onDone(body: String)
    fun onFailure(e: Exception)
}


private const val FORM_FIlE_NAME = "file"
private const val UPLOAD_URI = "https://x0.at"
private const val SEGMENT_SIZE = 4096L


private val client = OkHttpClient()


class Uploader(
    private val suri: Suri
) {
    private val listeners = mutableSetOf<ProgressListener>()
    private var call: Call? = null

    private fun upload() {
        try {
            call = prepare()
            jobs.lock {
                listeners.forEach { it.onStarted() }
            }
            val response = execute()
            jobs.lock {
                listeners.forEach { it.onDone(response) }
                remove(suri.uri)
            }
        } catch (e: Exception) {
            jobs.lock {
                listeners.forEach { it.onFailure(e) }
                remove(suri.uri)
            }
        }
    }

    private fun cancel() {
        call?.cancel()
    }

    private fun prepare() : Call {
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FORM_FIlE_NAME, suri.fileName, getRequestBody())
                .build()

        val request = Request.Builder()
                .url(UPLOAD_URI)
                .post(requestBody)
                .build()

        return client.newCall(request)
    }

    @Throws(IOException::class, SecurityException::class)
    private fun execute(): String {
        call!!.execute().use { response ->
            if (response.isSuccessful) {
                return response.body!!.string()
            } else {
                throw IOException("Unexpected code $response")
            }
        }
    }

    private fun getRequestBody(): RequestBody {
        return object : RequestBody() {
            override fun contentType() = suri.mediaType
            override fun contentLength() = suri.fileSize

            override fun writeTo(sink: BufferedSink) {
                var totalRead = 0L

                suri.getInputStream().source().use {
                    while (true) {
                        jobs.lock {
                            listeners.forEach { l -> l.onProgress(totalRead, suri.fileSize) }
                        }

                        val read = it.read(sink.buffer, SEGMENT_SIZE)
                        if (read == -1L) return

                        sink.flush()

                        totalRead += read
                    }
                }
            }
        }
    }

    companion object Jobs {
        var counter = 0
        val jobs = mutableMapOf<Uri, Uploader>()

        @MainThread fun upload(suri: Suri, listener: ProgressListener) {
            jobs.lock {
                var uploader = jobs[suri.uri]
                if (uploader != null) {
                    uploader.listeners.add(listener)
                    listener.onStarted()
                } else {
                    uploader = Uploader(suri)
                    uploader.listeners.add(listener)
                    jobs[suri.uri] = uploader
                    thread(name = "u-" + counter++) { uploader.upload() }
                }
            }
        }

        @MainThread fun cancel(suri: Suri) {
            jobs.lock {
                jobs[suri.uri]?.cancel()
            }
        }
    }
}

private inline fun <T : Any> T.lock(func: (T.() -> Unit)) {
    synchronized (this) {
        func(this)
    }
}

// private fun <T> Iterable<T>.forEachLocked(func: (T.() -> Unit)) {
//     this.lock {
//         forEach(func)
//     }
// }