package oshaj.runtime;

import java.lang.ref.WeakReference;

import acme.util.Util;
import oshaj.sourceinfo.MethodTable;
import oshaj.util.IntSet;

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
	
	protected final int id;
	
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
	
	private int[]    methodIdStack  = new int[INITIAL_STACK_CAPACITY];
	private int      stackSize      = 0;

	protected IntSet currentReaderSet;
	
	// TODO WeakRef
//	protected Object cachedArray;
//	protected State[] cachedArrayStates;

	public ThreadState(Thread thread) {
		threadRef = new WeakReference<Thread>(thread);
		name = thread.getName();
		id = newID();
	}

	public Thread getThread() {
		return threadRef.get();
	}
	
	public String getName() {
		final Thread thread = threadRef.get();
		return thread == null ? name : thread.getName();
	}
	
	public String toString() {
		return "Thread " + id + " (\"" + getName() + "\")";
	}
	
	
	// -- Stack implementation ----------------------------
	
	public void enter(int mid, IntSet rs) {
		if (stackSize > methodIdStack.length - 1) {
			resize(stackSize * 2);
		}
		methodIdStack[stackSize++] = mid;
		currentReaderSet = rs;
//		readerSetStack[stackSize++] = rs;
	}
	
	private void resize(int n) {
		final int[] newMethodIdStack = new int[n];
		for (int i = 0; i < methodIdStack.length; i++) {
			newMethodIdStack[i] = methodIdStack[i];
		}
		methodIdStack = newMethodIdStack;
//		final IntSet[] newReaderSetStack = new IntSet[n];
//		for (int i = 0; i < readerSetStack.length; i++) {
//			newReaderSetStack[i] = readerSetStack[i];
//		}
//		readerSetStack = newReaderSetStack;
	}
	
	public int exit() {
		final int caller = --stackSize;
		currentReaderSet = MethodTable.policyTable[caller];
		return caller;
//		readerSetStack[stackSize - 1] = null;
	}
	
	public int currentMethod() {
		return methodIdStack[stackSize-1];
	}
	
}
