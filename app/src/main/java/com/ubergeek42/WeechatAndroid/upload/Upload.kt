package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri
import androidx.annotation.MainThread
import cats.warn
import com.ubergeek42.WeechatAndroid.BuildConfig
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.IOException
import okio.source
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


private const val SEGMENT_SIZE = 4096L
private const val TIMEOUT = 30L


private val userAgentInterceptor = Interceptor { chain ->
    chain.proceed(
        chain.request()
            .newBuilder()
            .header("User-Agent", "Weechat-Android/${BuildConfig.VERSION_NAME} okhttp/${OkHttp.VERSION}")
            .build()
    )
}

private val client = OkHttpClient.Builder()
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(userAgentInterceptor)
        .build()


class Upload(
    val suri: Suri,
    private val uploadUri: String,
    private val uploadFormFieldName: String,
    private val httpUriGetter: HttpUriGetter,
    private val requestModifiers: List<RequestModifier>,
    private val requestBodyModifiers: List<RequestBodyModifier>,
) {
    var transferredBytes = 0L
    val totalBytes = suri.fileSize

    private val listeners = mutableSetOf<Listener>()
    private var call: Call? = null

    enum class State { RUNNING, DONE, FAILED }
    @Volatile var state: State = State.RUNNING

    private fun upload() {
        try {
            call = prepare()
            val responseBody = wakeLock("upload") { execute() }
            val httpUri = httpUriGetter.getUri(responseBody)
            state = State.DONE
            jobs.lock {
                listeners.forEach { it.onFinished(this@Upload, Result.Done(httpUri)) }
                remove(suri.uri)
            }
        } catch (e: Exception) {
            state = State.FAILED
            val cancelled = call?.isCanceled() == true
            if (!cancelled) {
                warn(e) { "error while uploading" }
                jobs.lock {
                    listeners.forEach { it.onFinished(this@Upload, Result.Failed(e)) }
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
            listeners.forEach { it.onFinished(this@Upload, Result.Cancelled) }
            remove(suri.uri)
        }
    }

    private fun prepare() : Call {
        val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(uploadFormFieldName, suri.fileName, getRequestBody())

        for (requestBodyModifier in requestBodyModifiers)
            requestBodyModifier.modify(requestBodyBuilder)

        val requestBuilder = Request.Builder()
                .url(uploadUri)
                .post(requestBodyBuilder.build())

        for (requestModifier in requestModifiers)
            requestModifier.modify(requestBuilder)

        return client.newCall(requestBuilder.build())
    }

    @Throws(IOException::class, SecurityException::class)
    private fun execute(): String {
        call!!.execute().use { response ->
            if (response.isSuccessful) {
                return response.body.string()
            } else {
                val beginningOfBody = try {
                    response.peekBody(256).string()
                } catch (e: IOException) {
                    throw IOException("Unexpected error ${response.code}: ${response.message}\n\n<Error reading body>\n")
                            .apply { addSuppressed(e) }
                }

                throw IOException("Unexpected error ${response.code}: ${response.message}\n\n$beginningOfBody\n")
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

    sealed interface Result {
        object Cancelled : Result
        data class Done(val httpUri: String) : Result
        data class Failed(val e: Exception) : Result
    }

    interface Listener {
        fun onStarted(upload: Upload)
        fun onProgress(upload: Upload)
        fun onFinished(upload: Upload, result: Result)
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
                           uploadUri = Config.uploadUri,
                           uploadFormFieldName = Config.uploadFormFieldName,
                           httpUriGetter = Config.httpUriGetter,
                           requestModifiers = Config.requestModifiers,
                           requestBodyModifiers = Config.requestBodyModifiers)
                }
                upload.listeners.addAll(listeners)
                listeners.forEach { it.onStarted(upload) }
                if (!alreadyUploading) thread(name = "u-${counter++}") { upload.upload() }
            }
        }
    }
}
