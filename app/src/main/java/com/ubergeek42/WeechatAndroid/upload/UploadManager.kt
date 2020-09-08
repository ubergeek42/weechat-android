package com.ubergeek42.WeechatAndroid.upload

import androidx.annotation.MainThread
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


private val main: (() -> Unit) -> Unit = { Weechat.runOnMainThread { it() } }


interface UploadObserver {
    @MainThread fun onUploadsStarted()
    @MainThread fun onProgress(ratio: Float)
    @MainThread fun onUploadDone(suri: Suri, body: String)
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
                kitty.info("Cancelling upload: $it")
                it.cancel()
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
                    if (uploaders.size == 1) observer?.onUploadsStarted()
                }
            }

            var lastRatio = -1f
            override fun onProgress(uploader: Uploader) {
                main {
                    val newRatio = getCumulativeRatio();
                    if (lastRatio != newRatio) {
                        lastRatio = newRatio
                        kitty.info("Upload progress: $uploader")
                        observer?.onProgress(newRatio)
                    }
                }
            }

            override fun onDone(uploader: Uploader, body: String) {
                main {
                    kitty.info("Upload done: $uploader, result: $body")
                    uploaders.remove(uploader)
                    observer?.onUploadDone(suri, body)
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

private infix fun Long.fdiv(i: Long): Float = this / i.toFloat();