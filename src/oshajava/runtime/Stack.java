package oshajava.runtime;

import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;

public class Stack {

	public final Stack parent;
	public final int method;
	public final int id;
	
	private Stack(int method, Stack parent) {
		this.method = method;
		this.parent = parent;
		synchronized (Stack.class) {
			this.id = ++idCounter;
		}
	}
	
	public static Stack pop(final Stack stack) {
		return stack.parent;
	}
	
	public static Stack push(final int m, final Stack stack) {
		return get(m,stack);
	}
	
	@Override
	public boolean equals(Object otherObject) {
		try {
			final Stack other = (Stack)otherObject;
			return method == other.method && (parent == null || parent.equals(other.parent));
		} catch (ClassCastException e) {
			return false;
		}
	}
	
	/******************************************************************/
	
	private static int idCounter = -1;
	
	// TODO make the whole thing thread-local to avoid synchronization?
	private static final ConcurrentIdentityHashMap<Integer,ConcurrentIdentityHashMap<Stack,Stack>> hashConsTable = 
		new ConcurrentIdentityHashMap<Integer,ConcurrentIdentityHashMap<Stack,Stack>>();

	// TODO Cache recent ones in each thread.
	private static Stack get(final int method, final Stack parent) {
		// TODO? if (parent.method == method) return parent;
		ConcurrentIdentityHashMap<Stack,Stack>  t = hashConsTable.get(method);
		if (t == null) {
			t = new ConcurrentIdentityHashMap<Stack,Stack>();
			final ConcurrentIdentityHashMap<Stack,Stack> s = hashConsTable.putIfAbsent(method, t);
			if (s != null) t = s;
		}
		Stack stack = t.get(parent);
		if (stack == null) {
			stack = new Stack(method,parent);
			final Stack s = t.putIfAbsent(parent, stack);
			if (s == null) {
				return stack;
			} else {
				synchronized(Stack.class) {
					idCounter--;
				}
				return s;
			}
		} else {
			return stack;
		}
	}
	
	public static synchronized int lastID() {
		return idCounter;
	}
}
