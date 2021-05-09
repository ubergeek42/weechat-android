package com.ubergeek42.WeechatAndroid.notifications

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.ubergeek42.WeechatAndroid.upload.applicationContext


interface Statistics {
    fun reportBufferWasSharedTo(key: String)
    fun reportBufferWasManuallyFocused(key: String)
}


val statistics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     StatisticsImpl(applicationContext)
                 } else {
                     object : Statistics {
                         override fun reportBufferWasSharedTo(key: String) {}
                         override fun reportBufferWasManuallyFocused(key: String) {}
                     }
                 }



@RequiresApi(Build.VERSION_CODES.M)
class StatisticsImpl(val context: Context) : Statistics {
    private val bufferToManuallyFocusedCount = mapSortedByValue<String, Int> { count -> -count }
    private val bufferToSharedToCount = mapSortedByValue<String, Int> { count -> -count }

    override fun reportBufferWasManuallyFocused(key: String) {
        val count = bufferToManuallyFocusedCount.get(key) ?: 0
        bufferToManuallyFocusedCount.put(key, count + 1)
        shortcuts.reportBufferWasManuallyFocused(key, this)
    }

    override fun reportBufferWasSharedTo(key: String) {
        val count = bufferToSharedToCount.get(key) ?: 0
        bufferToSharedToCount.put(key, count + 1)
        shortcuts.reportBufferWasSharedTo(key, this)

    }

    fun getMostFrequentlyManuallyFocusedBuffers(upTo: Int): List<String> {
        return bufferToManuallyFocusedCount.getSomeKeys(upTo)
    }

    fun getMostFrequentlySharedToBuffers(upTo: Int): List<String> {
        return bufferToSharedToCount.getSomeKeys(upTo)
    }

}
