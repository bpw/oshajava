package oshaj.runtime;

import acme.util.collections.IntStack;

public class RuntimeMonitor {
	
	protected static final ThreadLocal<IntStack> stacks = new ThreadLocal<IntStack>() {
		@Override protected IntStack initialValue() { return new IntStack(); }
	};
		
	// thread, state
	public static void read(State state) {
		final Thread thread = Thread.currentThread();
	}
	
	public static void write(State state) {}
	
	// thread, array, index, state
	public static void arrayRead() {}
	
	public static void arrayWrite() {}
	
	// thread, obj, state
	public static void acquire() {}
	
	public static void release() {}
	
	// thread, method
	public static void enter(int mid) {
		stacks.get().push(mid);
	}
	
	public static void exit(int mid) {
		assert mid == stacks.get().pop();
	}

}
