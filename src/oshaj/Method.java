package oshaj;

import acme.util.identityhash.IdentityHashSet;

public class Method {
	private final IdentityHashSet<Method> expectedReaders = new IdentityHashSet<Method>();
	
	public void addReader(Method m) {
		expectedReaders.add(m);
	}
	
	public IdentityHashSet<Method> getExpectedReaders() {
		return expectedReaders;
	}
}
