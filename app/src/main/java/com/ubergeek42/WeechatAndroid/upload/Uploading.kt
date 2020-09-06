package com.ubergeek42.WeechatAndroid.upload

import android.content.Context
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.IOException
import okio.source
import java.lang.Exception


interface ProgressListener {
    fun onStarted()
    fun onProgress(read: Long, total: Long)
    fun onDone(body: String)
    fun onException(e: Exception)
}


const val FORM_FIlE_NAME = "file"
const val UPLOAD_URI = "https://x0.at"
const val SEGMENT_SIZE = 4096L


private val client = OkHttpClient()


fun uploadUri(context: Context, shareUri: ShareUri, progressListener: ProgressListener) {
    try {
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FORM_FIlE_NAME, shareUri.fileName,
                        shareUri.asRequestBody(context, progressListener))
                .build()

        val request = Request.Builder()
                .url(UPLOAD_URI)
                .post(requestBody)
                .build()

        progressListener.onStarted()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                progressListener.onDone(response.body!!.string())
            } else {
                throw IOException("Unexpected code $response")
            }
        }
    } catch (e: Exception) {
        progressListener.onException(e)
    }
}


fun ShareUri.asRequestBody(context: Context, progressListener: ProgressListener): RequestBody {
    val cr = context.contentResolver
    val fileSize = cr.openFileDescriptor(uri, "r")?.statSize ?: -1L
    val inputStream = cr.openInputStream(uri)!!

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
