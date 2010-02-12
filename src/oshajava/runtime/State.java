package oshajava.runtime;


/**
 * A representation of the state of a field as a channel for communication
 * between methods in different threads.
 * 
 * @author bpw
 *
 */
public final class State {
	
	public static final State INVALID_STATE = new State(null, null);
	
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected final ThreadState thread;
	
	/**
	 * Stack when the field was written.
	 */
	protected final Stack stack;
	
	protected State(ThreadState thread, Stack stack) {
		this.stack = stack;
		this.thread = thread;
	}
	
}
