package com.ubergeek42.WeechatAndroid.upload

import androidx.annotation.MainThread
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
                if (it.active) {
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
            val progressThrottle = Throttle(min = 0f, max = 1f,
                                            valueThreshold = 0.01f, timeThreshold = 16)

            override fun onStarted(uploader: Uploader) {
                main {
                    kitty.info("Upload started: $uploader")
                    uploaders.add(uploader)
                    if (uploaders.size == 1) observer?.onUploadsStarted()
                }
            }

            override fun onProgress(uploader: Uploader) {
                main {
                    val ratio = getCumulativeRatio()
                    if (progressThrottle.step(ratio)) {
                        kitty.trace("Upload progress: $uploader")
                        observer?.onProgress(ratio)
                    }
                }
            }

            override fun onDone(uploader: Uploader, httpUri: String) {
                suri.httpUri = httpUri
                main {
                    kitty.info("Upload done: $uploader, result: $httpUri")
                    uploaders.remove(uploader)
                    observer?.onUploadDone(suri)
                    if (uploaders.isEmpty()) observer?.onFinished()
                }
            }

            override fun onFailure(uploader: Uploader, e: Exception) {
                main {
                    kitty.info("Upload failure: $uploader")
                    uploaders.remove(uploader)
                    observer?.onUploadFailure(suri, e)
                    if (uploaders.isEmpty()) observer?.onFinished()
                }
            }
        })
    }

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
