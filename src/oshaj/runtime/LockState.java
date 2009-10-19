package oshaj.runtime;

import oshaj.util.IntSet;

public class LockState {
	
	protected int depth = 0;

	/**
	 * Thread id of the last thread to hold the lock.
	 */
	protected Thread lastThread;
	
	/**
	 * Method id of the last method in which the lock was held.
	 */
	protected int lastMethod;
	
	/**
	 * Method allowed to read the field from a different thread in the current state.
	 */
	protected IntSet nextMethods;
	
	protected LockState(Thread holderThread, int holderMethod, IntSet nextMethods) {
		this.lastMethod  = holderMethod;
		this.nextMethods = nextMethods;
		this.lastThread  = holderThread;
	}
	
	protected LockState(Thread holderThread, int holderMethod) {
		this.lastMethod  = holderMethod;
		this.nextMethods = null;
		this.lastThread  = holderThread;
	}

}
