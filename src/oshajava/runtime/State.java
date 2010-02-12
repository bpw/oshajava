package oshajava.runtime;

import java.util.HashMap;


/**
 * A representation of the state of a field as a channel for communication
 * between methods in different threads.
 * 
 * @author bpw
 *
 */
public final class State {
		
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected final ThreadState thread;
	
	/**
	 * Stack when the field was written.
	 */
	protected final Stack stack;
	
	private final State caller;
	
	protected final HashMap<Integer,State> calleeToState = new HashMap<Integer,State>();
	
	private State(final ThreadState thread, final State caller, final Stack stack) {
		this.caller = caller;
		this.stack = stack;
		this.thread = thread;
	}
	
	public State call(final int method) {
		State cs = calleeToState.get(method);
		if (cs == null) {
			cs = new State(thread, this, Stack.push(method, stack));
			calleeToState.put(method, cs);
		}
		return cs;
	}
	
	public State ret() {
		return caller;
	}
	
	public static State root(final ThreadState ts) {
		return new State(ts, null, null);
	}
}
