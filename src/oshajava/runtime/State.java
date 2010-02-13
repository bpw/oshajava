package oshajava.runtime;

import java.util.HashMap;

import oshajava.util.count.Counter;


/**
 * A representation of communication context: thread x call stack.
 * 
 * @author bpw
 *
 */
public final class State {
	
	public static final Counter statesCreated = new Counter();
	public static final boolean COUNT_STATES = true;
		
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected final ThreadState thread;
	
	/**
	 * Stack when the field was written.
	 */
	protected final Stack stack;
	
	/**
	 * State of caller.
	 */
	private final State caller;
	
	/**
	 * States for callees by method. Populated lazily.
	 */
	protected final HashMap<Integer,State> calleeToState = new HashMap<Integer,State>();
	
	private State(final ThreadState thread, final State caller, final Stack stack) {
		this.caller = caller;
		this.stack = stack;
		this.thread = thread;
		
		if (COUNT_STATES) statesCreated.inc();
	}
	
	/**
	 * Get the state resulting from calling method.
	 * @param method
	 * @return
	 */
	public State call(final int method) {
		State cs = calleeToState.get(method);
		if (cs == null) {
			cs = new State(thread, this, Stack.push(method, stack));
			calleeToState.put(method, cs);
		}
		return cs;
	}
	
	/**
	 * Get the state resulting from returning from the current method.
	 * @return
	 */
	public State ret() {
		return caller;
	}
	
	/**
	 * Make the root state for a thread.
	 * @param ts
	 * @return
	 */
	public static State root(final ThreadState ts) {
		return new State(ts, null, null);
	}
}
