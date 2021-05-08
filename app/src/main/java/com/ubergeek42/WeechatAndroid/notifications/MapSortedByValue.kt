package com.ubergeek42.WeechatAndroid.notifications

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


interface PrimitiveMap<K, V : Comparable<V>> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun getSomeKeys(upTo: Int): List<K>

    data class Entry<K, V : Comparable<V>>(
        val key: K,
        val value: V
    )
}


inline fun <K, V : Comparable<V>> mapSortedByValue(
    crossinline selector: (V) -> Comparable<*>
): PrimitiveMap<K, V> {
    val keyToEntry = HashMap<K, PrimitiveMap.Entry<K, V>>()
    val sortedSet = sortedSetOf<PrimitiveMap.Entry<K, V>>(compareBy { selector(it.value) })

    return object: PrimitiveMap<K, V> {
        override fun get(key: K): V? {
            return keyToEntry[key]?.value
        }

        override fun put(key: K, value: V) {
            keyToEntry[key]?.let { sortedSet.remove(it) }
            val entry = PrimitiveMap.Entry(key, value)
            keyToEntry[key] = entry
            sortedSet.add(entry)
        }

        override fun getSomeKeys(upTo: Int): List<K> {
            val result = ArrayList<K>(upTo)
            sortedSet.forEachIndexed { index, value ->
                if (index < upTo) {
                    result.add(value.key)
                } else {
                    return@forEachIndexed
                }
            }
            return result
        }
    }
}
