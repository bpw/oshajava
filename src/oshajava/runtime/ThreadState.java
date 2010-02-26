package oshajava.runtime;

import java.lang.ref.WeakReference;

import oshajava.support.acme.util.Util;
import oshajava.util.count.Counter;


/**
 * State information associated with a thread. ThreadStates represent 
 * threads and their metadata and store the current call stacks and 
 * communication state, in addition to caching lock and array states
 * recently accessed by the thread to avoid expensive HashMap lookups
 * where possible.
 * 
 * NOTE: All non-final fields are for thread private access only.
 * 
 * @author bpw
 *
 */
public final class ThreadState {
	
	/**
	 * Create a new ThreadState.
	 * @param thread
	 * @param stateTableSize
	 */
	public ThreadState(final Thread thread) {
		threadRef = new WeakReference<Thread>(thread);
		name = thread.getName();
		id = newID();
	}

	// -- Thread IDs -------------------------------------------------
	
	/**
	 * Counter for thread ids.
	 */
	private static int idCounter = -1;
	
	/**
	 * Get a new thread id.
	 * @return
	 */
	private static synchronized int newID() {
		Util.assertTrue(idCounter < Integer.MAX_VALUE, "Ran out of thread IDs.");
		return ++idCounter;
	}
	
	/**
	 * Get the last thread id allocated.
	 * @return
	 */
	public static synchronized int lastID() {
		return idCounter;
	}
	
	/**
	 * Thread id.
	 */
	public final int id;
	
	// -- Thread metadata --------------------------------------------------------
	
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
	 * Get the name of the Thread this ThreadState represents.
	 * @return
	 */
	public String getName() {
		final Thread thread = threadRef.get();
		return thread == null ? "[No longer live. Originally named " + name + "]" : thread.getName();
	}
	
	public String toString() {
		return "Thread " + id + " (\"" + getName() + "\")";
	}
	
	// -- Thread call stack/state -----------------------------------------------------
	
	/**
	 * Cached copy of this thread's current State.
	 */
	public State state = State.root(this);
	
	/**
	 * Update call stack/state to reflect entering the method with id mid.
	 * @param methodUID
	 */
	protected void enter(final int methodUID) {
		state = state.call(methodUID);
	}
	
	/**
	 * Update call stack/state to reflect exiting the method with id mid.
	 */
	protected void exit() {
		state = state.ret();
	}
	
	// -- Array state caching --------------------------------------------------------
	
	/**
	 * Direct-mapped cache -----------------------------------------------------------
	 * Most in upper 90%s with 16. SOR at 88, Series at 50, Sparse at 72
	 * 
	 * LEGACY
	 * Fully associative cache -------------------------------------------------------
	 * For reference. Hit rates and avg successful walk lengths for given cache sizes.
	 * 						History size
	 * Benchmark			1		2		3		4		5		6		7		8
	 * 
	 * Crypt A				86	1.0	96	1.9	100	1.9	100	2.8
	 * LUFact A				33	1.0	97	1.5	98	2.5	98	3.3
	 * MolDyn A				89	1.0	90	2.0	91	3.0	91	4.0
	 * RayTracer A			97	1.0	100	2.0	100	1.6	100 2.9
	 * SOR A				57	1.0	66	1.9	66	2.9	97	2.8
	 * Series A				0	1.0	50	1.5	50	2.5	50	3.5			50	5.5
	 * SparseMatmult A		0	1.0	0.6	1.5	15	1.1	15	1.6	16	2.6	99	3.6
	 * 
	 * With cursor repositioning on read hits:
	 * Crypt A						96	1.1	96	1.2	96	1.3
	 * LUFact A						98	1.7	98	2.0	98	2.3
	 * MolDyn A						91	1.0 92	1.1	93	1.1
	 * RayTracer A					100	1.0	100	1.0	100	1.0
	 * SOR A						66	1.2	86	1.7	91	1.8
	 * Series A						50	2.0	50	2.5	50	3.0
	 * SparseMatmult A				0.6	1.7	15	1.1	15	1.2			89	2.7
	 * 
	 * Also for reference, the alternate (non-cached) path consists of roughly 10 field
	 * reads on its fast path in WeakConcurrentIdentityHashMap.  
	 * So we should be able to optimize the tradeoff. E.g. we want to choose cache size N
	 * to minimize
	 *     ((1 - HitRate(N)) * (N + 10)) + (HitRate(N) * AvgHitTime(N)) 
	 */
//	protected static final int CACHED_ARRAYS = 3;
	
	protected static final boolean REPOSITION_CURSOR_ON_HIT = true;
	
	// TODO Do some GC of the cache or use array of WeakRefs to the actual array objects.
	protected static final int ARRAY_CACHE_SIZE = 16;
	// TODO victim buffer or 2-way associative?
//	protected static final int VICTIM_BUFFER_SIZE = 2;
	
//	/**
//	 * Profiling: how many cache slots checked before typical hit.
//	 */
//	protected static final Counter[] hitLengths = new Counter[CACHED_ARRAYS];
//	static {
//		if (RuntimeMonitor.COUNT_ARRAY_CACHE) {
//			for (int i = 0; i < CACHED_ARRAYS; i++) {
//				hitLengths[i] = new Counter();
//			}
//		}
//	}

	/**
	 * Cached copy of the last accessed array.
	 * TODO weak reference or just some GC of my own. e.g. delete after n method calls.	
	 */
	private final Object[] arrayCache = new Object[ARRAY_CACHE_SIZE];
	
	/**
	 * Cached reference to the coarse array state of the last accessed array. Only used
	 * in coarse array state mode.
	 */
	@SuppressWarnings("unchecked")
	private final RuntimeMonitor.Ref<State>[] arrayStateRefCache = new RuntimeMonitor.Ref[ARRAY_CACHE_SIZE];
	
//	/**
//	 * Always points to last cached array.
//	 */
//	private int cachedArrayCursor = 0;
//	
	/**
	 * Cached copy of the the array index states for the last accessed array. Only used
	 * in array index state mode.
	 */
	private State[][] arrayIndexStateCache = new State[ARRAY_CACHE_SIZE][];
	
	/**
	 * Get a cached array state array. (For array index states.)
	 * @param array
	 * @return
	 */
	protected State[] getCachedArrayStateArray(final Object array) {
		final int slot = System.identityHashCode(array) % ARRAY_CACHE_SIZE;
		if (arrayCache[slot] == array) {
			return arrayIndexStateCache[slot];
		} else {
			return null;
		}	
	}
//	protected State[] getVictimBufferIndexStates(final Object array) {
//		int hitLength = 0;
//		for (int i = cachedArrayCursor; i - cachedArrayCursor < CACHED_ARRAYS; i++) {
//			if (arrayCache[i % CACHED_ARRAYS] == array) {
//				if (RuntimeMonitor.COUNT_ARRAY_CACHE) hitLengths[hitLength].inc();
//				if (REPOSITION_CURSOR_ON_HIT) cachedArrayCursor = i % CACHED_ARRAYS;
//				return arrayIndexStateCache[i % CACHED_ARRAYS];
//			}
//			if (RuntimeMonitor.COUNT_ARRAY_CACHE) hitLength++;
//		}
//		return null;
//	}
	
	/**
	 * Only call if the array wasn't in the cache already.
	 * @param array
	 * @param stateRef
	 */
	protected void cacheArrayStateArray(final Object array, final State[] states) {
		final int slot = System.identityHashCode(array) % ARRAY_CACHE_SIZE;
		arrayCache[slot] = array;
		arrayIndexStateCache[slot] = states;
	}
	
	/**
	 * Get a cached array state ref. (For writes.)
	 * @param array
	 * @return
	 */
	protected RuntimeMonitor.Ref<State> getCachedArrayStateRef(final Object array) {
		final int slot = System.identityHashCode(array) % ARRAY_CACHE_SIZE;
		if (arrayCache[slot] == array) {
			return arrayStateRefCache[slot];
		} else {
			return null;
		}
	}
	
//	protected RuntimeMonitor.Ref<State> getVictimBufferStateRef(final Object array) {
//		int hitLength = 0;
//		for (int i = cachedArrayCursor; i - cachedArrayCursor < CACHED_ARRAYS; i++) {
//			if (arrayCache[i % CACHED_ARRAYS] == array) {
//				if (RuntimeMonitor.COUNT_ARRAY_CACHE) hitLengths[hitLength].inc();
//				if (REPOSITION_CURSOR_ON_HIT) cachedArrayCursor = i % CACHED_ARRAYS;
//				return arrayStateRefCache[i % CACHED_ARRAYS];
//			}
//			if (RuntimeMonitor.COUNT_ARRAY_CACHE) hitLength++;
//		}
//		return null;
//
//	}

	/**
	 * Get a cached array state. (For reads.)
	 * @param array
	 * @return
	 */
	protected State getCachedArrayState(final Object array) {
		final RuntimeMonitor.Ref<State> ref = getCachedArrayStateRef(array);
		return ref == null ? null : ref.contents;		
	}
	
	/**
	 * Only call if the array wasn't in the cache already.
	 * @param array
	 * @param stateRef
	 */
	protected void cacheArrayStateRef(final Object array, final RuntimeMonitor.Ref<State> stateRef) {
		final int slot = System.identityHashCode(array) % ARRAY_CACHE_SIZE;
		arrayCache[slot] = array;
		arrayStateRefCache[slot] = stateRef;
	}
	
	// -- Lock state caching ---------------------------------------------------------
	
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
//	private int[] expand(final int[] array, int n) {
//		final int[] newArray = new int[n];
//		System.arraycopy(array, 0, newArray, 0, array.length);
//		return newArray;
//	}
	
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
