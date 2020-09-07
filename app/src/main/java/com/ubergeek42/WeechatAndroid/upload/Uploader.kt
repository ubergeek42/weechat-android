package com.ubergeek42.WeechatAndroid.upload

import com.ubergeek42.WeechatAndroid.Weechat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import okio.IOException
import okio.source


interface ProgressListener {
    fun onStarted()
    fun onProgress(read: Long, total: Long)
    fun onDone(body: String)
    fun onFailure(e: Exception)
}


const val FORM_FIlE_NAME = "file"
const val UPLOAD_URI = "https://x0.at"
const val SEGMENT_SIZE = 4096L


private val client = OkHttpClient()


class Uploader(
    private val shareUri: ShareUri,
    private val progressListener: ProgressListener
) {
    var call: Call? = null

    fun upload() {
        try {
            call = prepare()
            progressListener.onStarted()
            val response = execute()
            progressListener.onDone(response)
        } catch (e: Exception) {
            progressListener.onFailure(e)
        }
    }

    fun cancel() {
        call?.cancel()
    }

    @Throws(IOException::class)
    private fun prepare() : Call {
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FORM_FIlE_NAME, shareUri.fileName,
                        shareUri.asRequestBody(progressListener))
                .build()

        val request = Request.Builder()
                .url(UPLOAD_URI)
                .post(requestBody)
                .build()

        return client.newCall(request)
    }

    @Throws(IOException::class)
    private fun execute(): String {
        call!!.execute().use { response ->
            if (response.isSuccessful) {
                return response.body!!.string()
            } else {
                throw IOException("Unexpected code $response")
            }
        }
    }
}


fun ShareUri.asRequestBody(progressListener: ProgressListener): RequestBody {
    val resolver = Weechat.applicationContext.contentResolver
    val fileSize = resolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
    val inputStream = resolver.openInputStream(uri)!!

    return object : RequestBody() {
        override fun contentType() = type?.toMediaTypeOrNull()

        override fun contentLength() = fileSize

        override fun writeTo(sink: BufferedSink) {
            var totalRead = 0L

            inputStream.source().use {
                while (true) {
                    progressListener.onProgress(totalRead, fileSize)

                    val read = it.read(sink.buffer, SEGMENT_SIZE)
                    if (read == -1L) return

                    sink.flush()

                    totalRead += read
                }
            }
        }
    }
}
