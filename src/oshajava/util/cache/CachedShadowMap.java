package oshajava.util.cache;

import oshajava.runtime.Config;
import oshajava.util.WeakConcurrentIdentityHashMap;

public class CachedShadowMap<V> {
	
	private final int cacheSize;
	
	private final WeakConcurrentIdentityHashMap<Object,V> store = new WeakConcurrentIdentityHashMap<Object,V>(Config.shadowStoreGCoption.get());
	
	private final ThreadLocal<DirectMappedShadowCache<Object,V>> caches = new ThreadLocal<DirectMappedShadowCache<Object,V>>() {
		protected DirectMappedShadowCache<Object,V> initialValue() {
			return new DirectMappedShadowCache<Object,V>(store, cacheSize);
		}
	};
	
	public CachedShadowMap(final int cacheSize) {
		this.cacheSize = cacheSize;
	}

	public V get(final Object key) {
		return caches.get().get(key);
	}

	public V putIfAbsent(final Object key, final V value) {
		return caches.get().putIfAbsent(key, value);
	}

}
