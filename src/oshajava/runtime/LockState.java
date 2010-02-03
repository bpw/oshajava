package oshajava.runtime;

public class LockState {
	
//	protected ThreadLocal<Integer> depth = new ThreadLocal<Integer>() {
//	    @Override
//	    protected Integer initialValue() {
//	        return 0;
//	    }
//	};
	private int depth = 0;
	
	public int getDepth() {
//	    return depth.get();
		return depth;
	}
	public void incrementDepth() {
//	    depth.set(depth.get()+1);
		depth++;
	}
	public void decrementDepth() {
//	    depth.set(depth.get()-1);
		depth--;
	}
	public void setDepth(int val) {
//	    depth.set(val);
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
