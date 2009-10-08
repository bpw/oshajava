package oshaj.runtime;

public class RuntimeMonitor {
	
	// thread, obj, field, state
	public static void read(Object obj, State state) {
		final Thread thread = Thread.currentThread();
	}
	
	public static void write(Object obj, State state) {}
	
	// thread, array, index, state
	public static void arrayRead() {}
	
	public static void arrayWrite() {}
	
	// thread, obj, state
	public static void acquire() {}
	
	public static void release() {}
	
	// thread
	public static void startThread() {}
	
	// thread, method
	public static void enter() {}
	
	public static void exit() {}

}
