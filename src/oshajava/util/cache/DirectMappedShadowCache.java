/*

Copyright (c) 2010, Benjamin P. Wood and Adrian Sampson, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the University of Washington nor the names of its
   contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package oshajava.util.cache;

import java.util.concurrent.ConcurrentMap;

import oshajava.runtime.RuntimeMonitor;
import oshajava.util.count.Counter;

/**
 * A THREAD-LOCAL direct-mapped cache front-end for a shared ConcurrentMap. 
 * Keys are hashed by System.identityHashCode.
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
	
	/**
	 * Optional counters to count hits and misses.
	 */
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
			if (COUNT && hits != null) hits.inc();
			return values[slot];
		}
		if (key == null) {
			return null;
		}
		if (COUNT && misses != null) misses.inc();
		final V val = store.get(key);
		if (val != null) { // TODO maybe set it anyway?
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
