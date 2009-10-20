package oshaj.runtime;

import oshaj.sourceinfo.IntSet;

public class LockState {
	
	protected int depth = 0;

	/**
	 * Thread id of the last thread to hold the lock.
	 */
	protected ThreadState lastThread;
	
	/**
	 * Method id of the last method in which the lock was held.
	 */
	protected int lastMethod;
	
	/**
	 * Method allowed to read the field from a different thread in the current state.
	 */
	protected IntSet nextMethods;
	
	protected LockState(ThreadState holderThread, int holderMethod, IntSet nextMethods) {
		this.lastMethod  = holderMethod;
		this.nextMethods = nextMethods;
		this.lastThread  = holderThread;
	}
	
	protected LockState(ThreadState holderThread, int holderMethod) {
		this.lastMethod  = holderMethod;
		this.nextMethods = null;
		this.lastThread  = holderThread;
	}

}
