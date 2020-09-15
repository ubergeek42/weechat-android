package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri


private class Record(val httpUri: String,
                     val timestamp: Long = System.currentTimeMillis())

private val cache = mutableMapOf<Uri, Record>()

private fun filterRecords() {
    val now = System.currentTimeMillis()
    cache.values.retainAll { now - it.timestamp < Config.cacheMaxAge }
}

object Cache {
    fun record(uri: Uri, httpUri: String) {
        cache.lock {
            cache[uri] = Record(httpUri)
        }
    }

    fun retrieve(uri: Uri): String? {
        cache.lock {
            filterRecords()
            return cache[uri]?.httpUri
        }
    }
}
