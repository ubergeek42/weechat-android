package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri
import androidx.annotation.MainThread
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root
import okhttp3.*
import okio.BufferedSink
import okio.IOException
import okio.source
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Root private val kitty: Kitty = Kitty.make()


private const val FORM_FIlE_NAME = "file"
private const val UPLOAD_URI = "https://x0.at"
private const val SEGMENT_SIZE = 4096L
private const val TIMEOUT = 30L


private val client = OkHttpClient.Builder()
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()


class Upload(
    val suri: Suri,
    private val uploadUri: String,
    private val formFileName: String,
    private val httpUriGetter: HttpUriGetter
) {
    var transferredBytes = 0L
    val totalBytes = suri.fileSize

    private val listeners = mutableSetOf<Listener>()
    private var call: Call? = null

    enum class State { RUNNING, DONE, FAILED }
    var state: State = State.RUNNING

    private fun upload() {
        try {
            call = prepare()
            val responseBody = wakeLock("upload") { execute() }
            val httpUri = httpUriGetter.getUri(responseBody)
            state = State.DONE
            jobs.lock {
                listeners.forEach { it.onDone(this@Upload, httpUri) }
                remove(suri.uri)
            }
        } catch (e: Exception) {
            state = State.FAILED
            val cancelled = call?.isCanceled() == true
            if (!cancelled) {
                kitty.warn("error while uploading", e)
                jobs.lock {
                    listeners.forEach { it.onFailure(this@Upload, e) }
                    remove(suri.uri)
                }
            }
        }
    }

    // cancel() will raise an exception in the execute() method, but it can happen
    // with a slight delay. to avoid that, cancel here
    fun cancel() {
        jobs.lock {
            state = State.FAILED
            call?.cancel()
            listeners.forEach { it.onFailure(this@Upload, CancelledException()) }
            remove(suri.uri)
        }
    }

    private fun prepare() : Call {
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(formFileName, suri.fileName, getRequestBody())
                .build()

        val request = Request.Builder()
                .url(uploadUri)
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
                            listeners.forEach { l -> l.onProgress(this@Upload) }
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

    class CancelledException : IOException("Upload cancelled")

    interface Listener {
        fun onStarted(upload: Upload)
        fun onProgress(upload: Upload)
        fun onDone(upload: Upload, httpUri: String)
        fun onFailure(upload: Upload, e: Exception)
    }

    override fun toString(): String {
        val id = System.identityHashCode(this)
        val ratio = transferredBytes fdiv totalBytes
        return "Upload<${suri.uri}, ${ratio.format(2)} transferred ($transferredBytes of $totalBytes bytes)>@$id"
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    companion object Jobs {
        private var counter = 0
        private val jobs = mutableMapOf<Uri, Upload>()

        @MainThread fun upload(suri: Suri, vararg listeners: Listener) {
            jobs.lock {
                val alreadyUploading = jobs.containsKey(suri.uri)
                val upload = jobs.getOrPut(suri.uri) {
                    Upload(suri,
                           uploadUri = UPLOAD_URI,
                           formFileName = FORM_FIlE_NAME,
                           httpUriGetter = HttpUriGetter.fromRegex("https://.+"))
                }
                upload.listeners.addAll(listeners)
                listeners.forEach { it.onStarted(upload) }
                if (!alreadyUploading) thread(name = "u-${counter++}") { upload.upload() }
            }
        }
    }
}
