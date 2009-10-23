package oshaj.runtime;

import oshaj.sourceinfo.IntSet;

/**
 * A representation of the state of a field as a channel for communication
 * between methods in different threads.
 * 
 * INVARIANT: to preserve consistency, always read the writerThread field
 * before reading any other fields and write the writerThread field after
 * writing any other fields. When writing, acquire the lock on this object
 * for the duration of all the writes. Reading is more selective... depends
 * on the situation.
 * 
 * @author bpw
 *
 */
public final class State {
	
	public static final State INVALID_STATE = new State(null, -1, null);
	
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected final ThreadState thread;
	
	/**
	 * Method id of the last method in which the field was written.
	 */
	protected final int method;
	
	/**
	 * Method allowed to read the field from a different thread in the current state.
	 */
	protected final IntSet readers;
	
	protected State(ThreadState thread, int method, IntSet successors) {
		this.method = method;
		this.readers = successors;
		this.thread = thread;
	}
	
	protected State(ThreadState thread, int method) {
		this.method = method;
		this.readers = null;
		this.thread = thread;
	}
	
}
