package com.ubergeek42.WeechatAndroid.notifications

import java.util.*
import kotlin.collections.ArrayList


interface PrimitiveMap<K, V : Comparable<V>> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun getSomeKeys(upTo: Int): List<K>

    data class Entry<K, V : Comparable<V>>(
        val key: K,
        val value: V
    ) {
        override fun equals(other: Any?) = (other is Entry<*, *> && other.key == key) ||
                                           other == key
        override fun hashCode() = key.hashCode()
    }
}


inline fun <K, V : Comparable<V>> mapSortedByValue(
    crossinline selector: (V) -> Comparable<*>
): PrimitiveMap<K, V> {
    val map = TreeMap<PrimitiveMap.Entry<K, V>, PrimitiveMap.Entry<K, V>>(
        compareBy { selector(it.value) }
    )

    return object: PrimitiveMap<K, V> {
        override fun get(key: K): V? {
            return map[key as PrimitiveMap.Entry<*, *>]?.value
        }

        override fun put(key: K, value: V) {
            val record = PrimitiveMap.Entry(key, value)
            map.remove(record)
            map[record] = record
        }

        override fun getSomeKeys(upTo: Int): List<K> {
            val result = ArrayList<K>(upTo)
            map.values.forEachIndexed { index, value ->
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
