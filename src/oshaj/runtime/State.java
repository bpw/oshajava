package oshaj.runtime;

import oshaj.util.BitVector;

/**
 * A representation of the state of a field as a channel for communication
 * between methods in different threads.
 * 
 * TODO try baking this in directly as 3 shadow fields instead of 1 with indirection.
 * Takes 64 bytes per object whether or not the shadow fields are used instead of the 
 * current 8 bytes, then 64 bytes + header more when actually used.
 * 
 * @author bpw
 *
 */
public class State {
	
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected long writerTid;
	
	/**
	 * Method id of the last method in which the field was written.
	 */
	protected int writerMethod;
	
	/**
	 * Method allowed to read the field from a different thread in the current state.
	 */
	protected BitVector readerList;
	
	protected State(long writerTid, int writerMethod, BitVector readerList) {
		this.writerTid = writerTid;
		this.writerMethod = writerMethod;
		this.readerList = readerList;
	}
	
	protected State(long writerTid, int writerMethod) {
		this.writerTid = writerTid;
		this.writerMethod = writerMethod;
		this.readerList = null;
	}
	
}
