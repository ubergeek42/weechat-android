package com.ubergeek42.WeechatAndroid.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

// this exists because computeIfAbsent(K, Function) requires API 24
public class DefaultHashMap<K, V> extends HashMap<K, V> {
    public interface Factory<K, V> {
        @NonNull V make(K key);
    }

    final private Factory<K, V> factory;

    public DefaultHashMap(Factory<K, V> factory) {
        super();
        this.factory = factory;
    }

    @NonNull public V computeIfAbsent(@Nullable K key) {
        V value = super.get(key);
        if (value == null) {
            value = factory.make(key);
            put(key, value);
        }
        return value;
    }
}