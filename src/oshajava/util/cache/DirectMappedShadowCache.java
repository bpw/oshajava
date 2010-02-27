package oshajava.util.cache;

import java.util.concurrent.ConcurrentMap;

import oshajava.runtime.RuntimeMonitor;
import oshajava.util.count.Counter;

/**
 * A direct-mapped cache front-end for a Map. Keys are hashed by System.identityHashCode.
 * @author bpw
 *
 * @param <K> Type of keys
 * @param <V> Type of values
 */
public class DirectMappedShadowCache<K,V> extends ShadowCache<K,V> {
	
	public static final boolean COUNT = true && RuntimeMonitor.PROFILE;

	/**
	 * Mask for hashing.
	 */
	protected final int mask;
	
	/**
	 * Cache keys.
	 */
	protected final Object[] keys;
	
	/**
	 * Cache values.
	 */
	protected final V[] values;
	
	protected final Counter hits, misses;

	/**
	 * Create a new cache of the given size as a front end to the given map.
	 * @param store
	 * @param size
	 */
	public DirectMappedShadowCache(final ConcurrentMap<K,V> store, int size) {
		this(store, size, null, null);
	}
	@SuppressWarnings("unchecked")
	public DirectMappedShadowCache(final ConcurrentMap<K,V> store, int size, Counter hits, Counter misses) {
		super(store);
		this.keys = new Object[size];
		this.values = (V[])new Object[size];
		this.hits = hits;
		this.misses = misses;
		this.mask = size - 1;
		if ((mask & size) != 0) throw new IllegalArgumentException("The size parameter must be a power of 2. (" + size + " is not.)");
		
	}
	
	/**
	 * Get the value for key. May displace others from the cache.
	 * @param key
	 * @return
	 */
	public V get(final K key) {
		final int slot = System.identityHashCode(key) & mask;
		if (keys[slot] == key) {
			if (COUNT) hits.inc();
			return values[slot];
		}
		if (key == null) {
			return null;
		}
		if (COUNT) misses.inc();
		final V val = store.get(key);
		if (val != null) {
			keys[slot] = key;
			values[slot] = val;
		}
		return val;
	}
		
	/**
	 * Put the given key/value pair in the map if not there already. Assumed not in cache. May displace others from cache.
	 * @param key
	 * @param value
	 */
	public V putIfAbsent(final K key, final V value) {
		if (key == null || value == null) {
			throw new NullPointerException();
		}
		final int slot = System.identityHashCode(key) & mask;
		if (keys[slot] == key) {
			return values[slot];
		}
		final V oldval = store.putIfAbsent(key, value);
		keys[slot] = key;
		if (oldval == null) {
			values[slot] = value;
		} else {
			values[slot] = oldval;
		}
		return oldval;
	}
	
	/**
	 * Clear the cache (probably to allow gc...)
	 */
	public void flush() {
		for (int i = 0; i < keys.length; i++) {
			keys[i] = null;
			values[i] = null;
		}
	}

}
