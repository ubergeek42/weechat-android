package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri
import androidx.annotation.MainThread
import okhttp3.*
import okio.BufferedSink
import okio.IOException
import okio.source
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


interface ProgressListener {
    fun onStarted(uploader: Uploader)
    fun onProgress(uploader: Uploader)
    fun onDone(uploader: Uploader, httpUri: String)
    fun onFailure(uploader: Uploader, e: Exception)
}


private const val FORM_FIlE_NAME = "file"
private const val UPLOAD_URI = "https://x0.at"
private const val SEGMENT_SIZE = 4096L

private const val TIMEOUT = 60L

private val client = OkHttpClient.Builder()
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()


class Uploader(
    val suri: Suri
) {
    var transferredBytes = 0L
    val totalBytes = suri.fileSize

    private val listeners = mutableSetOf<ProgressListener>()
    private var call: Call? = null

    enum class State { RUNNING, DONE, FAILED }
    var state: State = State.RUNNING

    private fun upload() {
        try {
            call = prepare()
            val response = wakeLock("upload") { execute() }
            val httpUri = responseToHttpUri(response)
            state = State.DONE
            jobs.lock {
                listeners.forEach { it.onDone(this@Uploader, httpUri) }
                remove(suri.uri)
            }
        } catch (e: Exception) {
            state = State.FAILED
            e.printStackTrace()
            if (call?.isCanceled() == true) return
            jobs.lock {
                listeners.forEach { it.onFailure(this@Uploader, e) }
                remove(suri.uri)
            }
        }
    }

    // cancel() will raise an exception in the execute() method, but it can happen with a slight
    // delay. to avoid that, cancel here
    fun cancel() {
        jobs.lock {
            state = State.FAILED
            call?.cancel()
            listeners.forEach { it.onFailure(this@Uploader, UploadCancelledException()) }
            remove(suri.uri)
        }
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
            override fun contentLength() = totalBytes

            override fun writeTo(sink: BufferedSink) {
                suri.getInputStream().source().use {
                    while (true) {
                        jobs.lock {
                            if (call?.isCanceled() == true) return
                            listeners.forEach { l -> l.onProgress(this@Uploader) }
                        }

                        val read = it.read(sink.buffer, SEGMENT_SIZE)
                        if (read == -1L) return

                        sink.flush()

                        transferredBytes += read
                    }
                }
            }
        }
    }

    private fun responseToHttpUri(body: String): String {
        return body     // todo
    }

    override fun toString(): String {
        val id = System.identityHashCode(this)
        val ratio = transferredBytes fdiv totalBytes
        return "Uploader<${suri.uri}, ${ratio.format(2)} transferred ($transferredBytes of $totalBytes bytes)>@$id"
    }

    companion object Jobs {
        private var counter = 0
        private val jobs = mutableMapOf<Uri, Uploader>()

        @MainThread fun upload(suri: Suri, vararg listeners: ProgressListener) {
            jobs.lock {
                var uploader = jobs[suri.uri]
                if (uploader == null) {
                    uploader = Uploader(suri)
                    uploader.listeners.addAll(listeners)
                    jobs[suri.uri] = uploader
                    thread(name = "u-" + counter++) { uploader.upload() }
                } else {
                    uploader.listeners.addAll(listeners)
                }
                listeners.forEach { it.onStarted(uploader) }
            }
        }
    }
}

class UploadCancelledException : IOException("Upload cancelled")

inline fun <T : Any> T.lock(func: (T.() -> Unit)) {
    synchronized (this) {
        func(this)
    }
}
