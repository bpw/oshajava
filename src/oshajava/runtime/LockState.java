package oshajava.runtime;

public class LockState {
	
	protected int depth = 0;

	/**
	 * State of the lock.
	 */
	protected State lastHolder;
	
	protected LockState(final State state) {
		this.lastHolder = state;
	}

}
