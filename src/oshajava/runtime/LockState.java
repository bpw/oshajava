package oshajava.runtime;

public class LockState {
	
	private int depth = 0;
	
	public int getDepth() {
		return depth;
	}
	public void incrementDepth() {
		depth++;
	}
	public void decrementDepth() {
		depth--;
	}
	public void setDepth(int val) {
		depth = val;
	}

	/**
	 * State of the lock.
	 */
	protected State lastHolder;
	
	protected LockState(final State state) {
		this.lastHolder = state;
	}

}
