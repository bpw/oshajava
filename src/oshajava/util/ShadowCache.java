package oshajava.util;

import java.util.concurrent.ConcurrentMap;

import oshajava.util.count.Counter;

public abstract class ShadowCache<K,V> {

	/**
	 * The backing store.
	 */
	protected final ConcurrentMap<K,V> store;
	
	public ShadowCache(final ConcurrentMap<K,V> store) {
		this.store = store;
	}
	
	public abstract V get(final K key);
	public abstract V putIfAbsent(final K key, final V value);
	
}
