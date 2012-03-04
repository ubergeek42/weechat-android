package com.ubergeek42;

import java.util.LinkedHashMap;
import java.util.Map;
/**
 * A HashMap that expires old/least recently used entries(i.e. an LRU cache)
 * Taken from: http://amix.dk/blog/post/19465
 *
 * @param <K> - the type of keys maintained by this map
 * @param <V> - the type of mapped values
 */
public class LRUMap<K,V> extends LinkedHashMap<K, V> {
	private int max_capacity;
	
	public LRUMap(int initial_capacity, int max_capacity) {
		super(initial_capacity, 0.75f, true);
		this.max_capacity = max_capacity;
	}
	
	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		return size() > this.max_capacity;
	}
}
