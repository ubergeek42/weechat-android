package com.ubergeek42.WeechatAndroid.utils;

import java.util.ArrayList;

public class TinyMap<K, V> {
    private ArrayList<K> keys = new ArrayList<>();
    private ArrayList<V> values = new ArrayList<>();

    public TinyMap() {}

    public TinyMap<K, V> put(K k, V v) {
        keys.add(k);
        values.add(v);
        return this;
    }

    public V get(K k) {
        for (int i = 0; i < keys.size(); i++)
            if (k.equals(keys.get(i)))
                return values.get(i);
        throw new RuntimeException("Key " + k + " not found");
    }

    public static <K, V> TinyMap<K, V> of(K k1, V v1) {
        return new TinyMap<K, V>().put(k1, v1);
    }

    public static <K, V> TinyMap<K, V> of(K k1, V v1, K k2, V v2) {
        return new TinyMap<K, V>().put(k1, v1).put(k2, v2);
    }

    public static <K, V> TinyMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        return new TinyMap<K, V>().put(k1, v1).put(k2, v2).put(k3, v3);
    }
}
