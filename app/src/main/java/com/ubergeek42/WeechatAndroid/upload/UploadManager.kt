package com.ubergeek42.WeechatAndroid.upload

import androidx.annotation.MainThread
import com.ubergeek42.WeechatAndroid.utils.Assert.assertThat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


interface UploadObserver {
    @MainThread fun onUploadsStarted()
    @MainThread fun onProgress(ratio: Float)
    @MainThread fun onUploadDone(suri: Suri)
    @MainThread fun onUploadFailure(suri: Suri, e: Exception)
    @MainThread fun onFinished()
}


class UploadManager {
    @Root private val kitty = Kitty.make()

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

    // this will call through to onFailure
    @MainThread fun filterUploads(suris: List<Suri>) {
        for (upload in uploads) {
            if (upload.suri !in suris) {
                if (upload.state == Upload.State.RUNNING) {
                    kitty.info("Cancelling upload: $upload")
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
                    observer?.onUploadDone(suri)
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
                    kitty.info("Upload started: $upload")
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
                        kitty.trace("Upload progress: ${ratio.format(2)}; $upload")
                        observer?.onProgress(ratio)
                    }
                }
            }

            override fun onDone(upload: Upload, httpUri: String) {
                suri.httpUri = httpUri
                main {
                    kitty.info("Upload done: $upload, result: $httpUri")
                    Cache.record(upload.suri.uri, httpUri)
                    // uploads.remove(upload)
                    observer?.onUploadDone(suri)
                    if (!uploads.areRunning) {
                        observer?.onFinished()
                        uploads.clear()
                    }
                }
            }

            override fun onFailure(upload: Upload, e: Exception) {
                main {
                    kitty.info("Upload failure: $upload, ${e.javaClass.simpleName}: ${e.message}")
                    uploads.remove(upload)
                    observer?.onUploadFailure(suri, e)
                    if (!uploads.areRunning) {
                        observer?.onFinished()
                        uploads.clear()
                    }
                }
            }
        })
    }

    var limiter: SkippingLimiter = SkippingLimiter(min = 0f, max = 1f,
                                                   valueThreshold = 0.01f, timeThreshold = 16)

    companion object {
        private val managers = mutableMapOf<Long, UploadManager>().withDefault { UploadManager() }

        @JvmStatic fun forBuffer(buffer: Long): UploadManager = managers.getValue(buffer)
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