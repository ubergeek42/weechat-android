package com.ubergeek42.WeechatAndroid.upload

import androidx.annotation.MainThread
import cats.info
import cats.trace
import com.ubergeek42.WeechatAndroid.utils.Assert.assertThat


interface UploadObserver {
    @MainThread fun onUploadsStarted()
    @MainThread fun onProgress(ratio: Float)
    @MainThread fun onUploadFinished(suri: Suri, result: Upload.Result)
    @MainThread fun onAllUploadsFinished()
}


class UploadManager {
    // list of running and completed uploads in the current batch upload.
    // failed uploads get removed from this list.
    val uploads = mutableListOf<Upload>()

    var observer: UploadObserver? = null
        set(observer) {
            field = observer

            observer?.let {
                if (uploads.areRunning) {
                    it.onUploadsStarted()
                    it.onProgress(uploads.stats.ratio)
                }
            }
        }

    private val unreportedUploadEventsTracker = UnreportedUploadEventsTracker()

    // this will call through to onFinished
    @MainThread fun filterUploads(suris: List<Suri>) {
        for (upload in uploads) {
            if (upload.suri !in suris) {
                if (upload.state == Upload.State.RUNNING) {
                    info { "Cancelling upload: $upload" }
                    upload.cancel()
                }
            }
        }
    }

    @MainThread fun startUploads(suris: List<Suri>) {
        assertThat(uploads).isEmpty
        for (suri in suris) {
            if (suri !in uploads.map { it.suri }) {
                val cachedHttpUri = Cache.retrieve(suri.uri)
                if (cachedHttpUri != null) {
                    suri.httpUri = cachedHttpUri
                    observer?.onUploadFinished(suri, Upload.Result.Done(cachedHttpUri))
                } else {
                    startUpload(suri)
                }
            }
        }
    }

    private fun startUpload(suri: Suri) {
        Upload.upload(suri, object : Upload.Listener {
            override fun onStarted(upload: Upload) {
                main {
                    info { "Upload started: $upload" }
                    uploads.add(upload)
                    if (uploads.size == 1) {
                        observer?.onUploadsStarted()
                        limiter.reset()
                    }
                }
            }

            override fun onProgress(upload: Upload) {
                main {
                    val ratio = uploads.stats.ratio
                    if (limiter.step(ratio)) {
                        trace { "Upload progress: ${ratio.format(2)}; $upload" }
                        observer?.onProgress(ratio)
                    }
                }
            }

            override fun onFinished(upload: Upload, result: Upload.Result) {
                main {
                    info { "Upload finished: $upload, result: $result" }
                    uploads.remove(upload)

                    if (result is Upload.Result.Done) {
                        suri.httpUri = result.httpUri
                        Cache.record(suri.uri, result.httpUri)
                    }

                    observer?.onUploadFinished(suri, result)
                    unreportedUploadEventsTracker.onUploadFinished(suri, result, observer != null)

                    if (uploads.isEmpty()) {
                        observer?.onAllUploadsFinished()
                        unreportedUploadEventsTracker.onAllUploadsFinished(observer != null)
                    }
                }
            }
        })
    }

    var limiter: SkippingLimiter = SkippingLimiter(min = 0f, max = 1f,
                                                   valueThreshold = 0.01f, timeThreshold = 16)

    companion object {
        private val managers = mutableMapOf<Long, UploadManager>()

        @JvmStatic fun forBuffer(buffer: Long) = managers.getOrPut(buffer, ::UploadManager)
    }
}

@Suppress("unused")
class UploadsStats(val transferredBytes: Long,
                   val totalBytes: Long,
                   val ratio: Float)

val Iterable<Upload>.stats: UploadsStats get() {
    val transferredBytes = sumOf { it.transferredBytes }
    val totalBytes = sumOf { it.totalBytes }
    val ratio = transferredBytes fdiv totalBytes
    return UploadsStats(transferredBytes, totalBytes, ratio)
}

val Iterable<Upload>.areRunning get() = this.any { it.state == Upload.State.RUNNING }