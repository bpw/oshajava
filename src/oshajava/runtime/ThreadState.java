package oshajava.runtime;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;

import oshajava.sourceinfo.BitVectorIntSet;
import oshajava.sourceinfo.MethodTable;
import oshajava.sourceinfo.Spec;
import oshajava.support.acme.util.Util;
import oshajava.support.acme.util.identityhash.ConcurrentIdentityHashMap;
import oshajava.support.acme.util.identityhash.IdentityHashSet;


/**
 * All non-final fields are for thread private access only.
 * 
 * @author bpw
 *
 */
public final class ThreadState {
	
	private static final int INITIAL_STACK_CAPACITY = 128;
	
	private static final int INVALID_ID = -1;
	
	private static int idCounter = -1;
	
	private static synchronized int newID() {
		Util.assertTrue(idCounter < Integer.MAX_VALUE, "Ran out of thread IDs.");
		return ++idCounter;
	}
	
	public static synchronized int lastID() {
		return idCounter;
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
	
	/**
	 * Table of the State for every (this, method) pair, indexed by method id.
	 */
	private State[] stateTable;
	
	/**
	 * Cached copy of the current method id.
	 */
	public int currentMethod = -1;
	
	/**
	 * Cached copy of this thread's current State. Initially invalid.
	 */
	public State currentState = State.INVALID_STATE;
	
	protected Stack stack;
	
	/**
	 * Size of the state stack.
	 */
	private int stackSize  = 0;

	/**
	 * Cached copy of the last accessed array.
	 * TODO weak reference or just some GC of my own. e.g. delete after n method calls.	
	 */
	protected Object cachedArray;
	
	/**
	 * Cached reference to the coarse array state of the last accessed array. Only used
	 * in coarse array state mode.
	 */
	protected RuntimeMonitor.Ref<State> cachedArrayStateRef;
	
	/**
	 * Cached copy of the the array index states for the last accessed array. Only used
	 * in array index state mode.
	 */
	protected State[] cachedArrayIndexStates;
	
	/**
	 * Initial capacity of the lock state stack.
	 */
	private static final int INITIAL_LOCK_STACK_CAPACITY = 16;
	
	/**
	 * Stack of lock states representing the locks currently held by this thread.
	 */
	private LockState[] lockStateStack = new LockState[INITIAL_LOCK_STACK_CAPACITY]; 
	
	/**
	 * Stack of locks mapping to the lock states in the stack.
	 */
	private Object[] lockStack = new Object[INITIAL_LOCK_STACK_CAPACITY];
	
	/**
	 * Current size of the lock state stack.
	 */
	private int lockStateStackSize = 0;
	
	/**
	 * Create a new ThreadState.
	 * @param thread
	 * @param stateTableSize
	 */
	public ThreadState(final Thread thread, final int stateTableSize) {
		threadRef = new WeakReference<Thread>(thread);
		name = thread.getName();
		id = newID();
		stateTable = new State[stateTableSize];
	}

	/**
	 * Get the actual java.lang.Thread this ThreadState represents.
	 * @return
	 */
	public Thread getThread() {
		return threadRef.get();
	}
	
	/**
	 * Get the name of the Thread this ThreadState represents.
	 * @return
	 */
	public String getName() {
		final Thread thread = threadRef.get();
		return thread == null ? name : thread.getName();
	}
	
	public String toString() {
		return "Thread " + id + " (\"" + getName() + "\")";
	}
	
	/**
	 * Expand the state table if there are new methods coming in and it's too small.
	 * @param newSize
	 */
	public synchronized void expandStateTable(final int newSize) {
		stateTable = (State[])expand(stateTable, newSize);
	}
	
	protected synchronized void enter(final int mid) {
		stack = Stack.push(mid, stack);
	}
	
	protected boolean exit() {
		stack = Stack.pop(stack);
		return stack != null;
	}
	
	/**
	 * Expand the given array to size n.
	 * @param array
	 * @param n
	 * @return
	 */
	private Object[] expand(final Object[] array, int n) {
		final Object[] newArray = new Object[n];
		System.arraycopy(array, 0, newArray, 0, array.length);
		return newArray;
	}
	private int[] expand(final int[] array, int n) {
		final int[] newArray = new int[n];
		System.arraycopy(array, 0, newArray, 0, array.length);
		return newArray;
	}
	
	/**
	 * Push the given lock state onto the lock state stack.
	 * @param ls
	 */
	protected void pushLock(final Object lock, final LockState ls) {
		if (lockStateStackSize == lockStateStack.length) {
			lockStateStack = (LockState[])expand(lockStateStack, lockStateStackSize*2);
			lockStack = expand(lockStack, lockStateStackSize*2);
		}
		lockStack[lockStateStackSize] = lock;
		lockStateStack[lockStateStackSize++] = ls;
	}
	
	/**
	 * Pop a lock, lock state from their respective stacks and return the lock state.
	 * @return
	 */
	protected void popLock() {
		lockStack[--lockStateStackSize] = null;
		lockStateStack[lockStateStackSize] = null;
	}
	
	/**
	 * Bound on the number of tries to find the lock state in the stack.
	 */
	private static final int LOCK_STATE_CACHE_BOUND = 4;

	/**
	 * Lookup a lock state if it's in the top bit of the stack.
	 * @param lock
	 * @return
	 */
	protected LockState getLockState(final Object lock) {
		final int bound = lockStateStackSize > LOCK_STATE_CACHE_BOUND ? lockStateStackSize - LOCK_STATE_CACHE_BOUND : 0;
		for (int i = lockStateStackSize - 1; i > bound; i--) {
			if (lockStack[i] == lock) return lockStateStack[i];
		}
		return null;
	}
	
}
