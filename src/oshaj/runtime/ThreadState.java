package oshaj.runtime;

import java.lang.ref.WeakReference;

import oshaj.sourceinfo.MethodTable;

import acme.util.Util;

/**
 * All non-final fields are for thread private access only.
 * 
 * @author bpw
 *
 */
public final class ThreadState {
	
	private static final int INITIAL_STACK_CAPACITY = 128;
	
	private static int idCounter = -1;
	
	private static synchronized int newID() {
		Util.assertTrue(idCounter < Integer.MAX_VALUE, "Ran out of thread IDs.");
		return ++idCounter;
	}
	
	public final int id;
	
	/**
	 * Let GC go as planned... We have refs to ThreadStates in ThreadLocals in the
	 * RuntimeMonitor, but also in States, which are as long-lived as their data.
	 */
	private final WeakReference<Thread> threadRef;
	
	/**
	 * The initial name of the Thread. (Used on getName, toString only when the
	 * Thread has already been GCed. Otherwise, we use the up-to-date name from
	 * t.getName().)
	 */
	private final String name;
	
	private State[] stateTable;
	
	public int currentMethod;
	public State currentState = State.INVALID_STATE;
	private State[]  stateStack = new State[INITIAL_STACK_CAPACITY];
	private int      stackSize  = 0;

	// TODO weak reference or just some GC of my own. e.g. delete after n method calls.	
	protected Object cachedArray;
	protected RuntimeMonitor.Ref<State> cachedArrayStateRef; // coarse
	protected State[] cachedArrayIndexStates; // fine
	
	private static final int INITIAL_LOCK_STACK_CAPACITY = 16;
	private LockState[] lockStateStack = new LockState[INITIAL_LOCK_STACK_CAPACITY]; 
	
	public ThreadState(final Thread thread, final int stateTableSize) {
		threadRef = new WeakReference<Thread>(thread);
		name = thread.getName();
		id = newID();
		stateTable = new State[stateTableSize];
	}

	public Thread getThread() {
		return threadRef.get();
	}
	
//	protected State currentState() {
//		if (currentState == State.INVALID_STATE) {
//			throw new InlinedEntryPointException();
//		}
//		return currentState;
//	}
//	
//	protected int currentMethod() {
//		if (currentState == State.INVALID_STATE) {
//			throw new InlinedEntryPointException();
//		}
//		return currentMethod;
//	}
	
	public String getName() {
		final Thread thread = threadRef.get();
		return thread == null ? name : thread.getName();
	}
	
	public String toString() {
		return "Thread " + id + " (\"" + getName() + "\")";
	}
	
	public synchronized void expandStateTable(final int newSize) {
		stateTable = expand(stateTable, newSize);
	}
	
	public synchronized void loadNewMethods(int next, final int endExcl) {
		for (; next < endExcl; next++) {
			stateTable[next] = new State(this, next, MethodTable.policyTable[next]);
		}
	}
	
	protected synchronized void enter(final int mid) {
		if (stackSize > stateStack.length - 1) {
			stateStack = expand(stateStack, stackSize * 2);
		}
		// TODO is this optimization for recursion (or indirect-turned-direct 
		// recursion induced by inlining) worth it?
		if (mid == currentMethod) {
			stateStack[stackSize++] = stateTable[mid];
		} else {
			currentMethod = mid;
			final State newState = stateTable[mid];
			currentState = newState;
			stateStack[stackSize++] = newState;
		}
	}
	
	private State[] expand(final State[] array, int n) {
		final State[] newArray = new State[n];
		for (int i = 0; i < array.length; i++) {
			newArray[i] = array[i];
		}
		return newArray;
	}
	
	protected boolean exit() {
		--stackSize;
		if (stackSize > 0) {
			final State callerState = stateStack[stackSize];
			currentState = callerState;
			currentMethod = callerState.method;
			return true;
		} else {
			currentState = State.INVALID_STATE;
			return false;
		}
	}
	
}
