package oshajava.util.cache;

import java.util.concurrent.ConcurrentMap;

/**
 * Outline of cache to be used as a THREAD-LOCAL front end to some concurrent map.
 * @author bpw
 *
 * @param <K>
 * @param <V>
 */
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
