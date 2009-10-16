package oshaj.runtime;

import oshaj.util.BitVector;

/**
 * A representation of the state of a field as a channel for communication
 * between methods in different threads.
 * 
 * @author bpw
 *
 */
public class State {
	
	/**
	 * Thread id of the last thread to write to the field.
	 */
	protected long writerTid = -1;
	
	/**
	 * Method id of the last method in which the field was written.
	 */
	protected int writerMethod = -1;
	
	/**
	 * Method allowed to read the field from a different thread in the current state.
	 */
	protected BitVector readerList;
	
}
