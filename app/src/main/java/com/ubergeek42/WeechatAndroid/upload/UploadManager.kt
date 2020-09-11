package com.ubergeek42.WeechatAndroid.upload

import androidx.annotation.MainThread
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

const val USE_SERVICE = true

interface UploadObserver {
    @MainThread fun onUploadsStarted()
    @MainThread fun onProgress(ratio: Float)
    @MainThread fun onUploadDone(suri: Suri)
    @MainThread fun onUploadFailure(suri: Suri, e: Exception)
    @MainThread fun onFinished()
}


class UploadManager {
    @Root private val kitty = Kitty.make()
    val useService = USE_SERVICE

    val uploaders = mutableListOf<Uploader>()

    var observer: UploadObserver? = null
        set(observer) {
            field = observer

            observer?.let {
                if (uploaders.isNotEmpty()) {
                    it.onUploadsStarted()
                    it.onProgress(getCumulativeRatio())
                }
            }
        }

    @MainThread fun startOrFilterUploads(suris: List<Suri>) {
        uploaders.removeAll {
            if (it.suri in suris) {
                false
            } else {
                if (it.state == Uploader.State.RUNNING) {
                    kitty.info("Cancelling upload: $it")
                    it.cancel()
                }
                true
            }
        }

        for (suri in suris) {
            if (suri !in uploaders.map { it.suri }) {
                startUpload(suri)
            }
        }
    }

    private fun startUpload(suri: Suri) {
        Uploader.upload(suri, object : ProgressListener {
            override fun onStarted(uploader: Uploader) {
                main {
                    kitty.info("Upload started: $uploader")
                    uploaders.add(uploader)
                    if (useService) UploadService.onUploadStarted(uploader)
                    if (uploaders.size == 1) {
                        observer?.onUploadsStarted()
                        limiter.reset()
                    }
                }
            }

            override fun onProgress(uploader: Uploader) {
                main {
                    val ratio = getCumulativeRatio()
                    if (limiter.step(ratio)) {
                        kitty.trace("Upload progress: ${ratio.format(2)}; $uploader")
                        if (useService) UploadService.onUploadProgress()
                        observer?.onProgress(ratio)
                    }
                }
            }

            override fun onDone(uploader: Uploader, httpUri: String) {
                suri.httpUri = httpUri
                main {
                    kitty.info("Upload done: $uploader, result: $httpUri")
                    uploaders.remove(uploader)
                    if (useService) UploadService.onUploadRemoved(uploader)
                    observer?.onUploadDone(suri)
                    if (uploaders.isEmpty()) observer?.onFinished()
                }
            }

            override fun onFailure(uploader: Uploader, e: Exception) {
                main {
                    kitty.info("Upload failure: $uploader")
                    uploaders.remove(uploader)
                    if (useService) UploadService.onUploadRemoved(uploader)
                    observer?.onUploadFailure(suri, e)
                    if (uploaders.isEmpty()) observer?.onFinished()
                }
            }
        })
    }

    var limiter: SkippingLimiter = SkippingLimiter(min = 0f, max = 1f,
                                              valueThreshold = 0.01f, timeThreshold = 16)

    private fun getCumulativeRatio(): Float {
        val cumulativeTransferredBytes = uploaders.map { it.transferredBytes }.sum()
        val cumulativeTotalBytes = uploaders.map { it.totalBytes }.sum()
        return cumulativeTransferredBytes fdiv cumulativeTotalBytes
    }

    companion object {
        private val managers = mutableMapOf<Long, UploadManager>().withDefault { UploadManager() }

        @JvmStatic fun forBuffer(buffer: Long): UploadManager = managers.getValue(buffer)
    }
}
