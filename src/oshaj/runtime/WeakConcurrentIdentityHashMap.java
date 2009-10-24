package oshaj.runtime;

//TODO
import acme.util.identityhash.WeakIdentityHashMap;

public class WeakConcurrentIdentityHashMap<K,V> {
	
	private static final int DEFAULT_WIDTH = 16;
	
	private final WeakIdentityHashMap<K,V>[] segments;
	private final int width;
	
	@SuppressWarnings("unchecked")
	public WeakConcurrentIdentityHashMap(final int width) {
		this.width = width;
		this.segments = new WeakIdentityHashMap[width];
		for (int i = 0; i < width; i++) {
			segments[i] = new WeakIdentityHashMap<K,V>();
		}
	}
	
	public WeakConcurrentIdentityHashMap() {
		this(DEFAULT_WIDTH);
	}
	
	public V get(K k) {
		final WeakIdentityHashMap<K,V> segment = segments[System.identityHashCode(k) % width]; 
		synchronized(segment) {
			return segment.get(k);
		}
	}
	
	public void put(K k, V v) {
		if (v == null) throw new NullPointerException();
		final WeakIdentityHashMap<K,V> segment = segments[System.identityHashCode(k) % width]; 
		synchronized(segment) {
			segment.put(k,v);
		}		
	}

	public V putIfAbsent(K k, V v) {
		if (v == null) throw new NullPointerException();
		final WeakIdentityHashMap<K,V> segment = segments[System.identityHashCode(k) % width]; 
		synchronized(segment) {
			V oldValue = segment.get(v);
			if (oldValue != null) {
				return oldValue;
			} else {
				segment.put(k,v);
				return null;
			}
		}		
	}

}