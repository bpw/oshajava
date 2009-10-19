package oshaj.runtime;

import oshaj.util.IntSet;

/**
 * A representation of the state of a field as a channel for communication
 * between methods in different threads.
 * 
 * TODO try baking this in directly as 3 shadow fields instead of 1 with indirection.
 * Takes 12 bytes per object whether or not the shadow fields are used instead of the 
 * current 4 bytes, then 12 bytes + header more when actually used.
 * 
 * @author bpw
 *
 */
public class State {
	
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected volatile Thread writerThread;
	
	/**
	 * Method id of the last method in which the field was written.
	 */
	protected int writerMethod;
	
	/**
	 * Method allowed to read the field from a different thread in the current state.
	 */
	protected IntSet readerSet;
	
	protected State(Thread writerThread, int writerMethod, IntSet readerSet) {
		this.writerMethod = writerMethod;
		this.readerSet = readerSet;
		this.writerThread = writerThread;
	}
	
	protected State(Thread writerTid, int writerMethod) {
		this.writerMethod = writerMethod;
		this.readerSet = null;
		this.writerThread = writerTid;
	}
	
}
