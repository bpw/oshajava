package oshaj.runtime;

import java.lang.reflect.Array;

import oshaj.sourceinfo.IntSet;
import oshaj.sourceinfo.MethodTable;
import acme.util.Util;
import acme.util.identityhash.ConcurrentIdentityHashMap;

/**
 * TODO Possible optimizations:
 *  
 * 1. store a stack of method sets instead of method ids (i.e. do the array load 
 *    in the enter and exit hooks instead of in each read, write, and acquire hook.
 *    Hopefully this is less often, but who knows.)
 *    
 * 2. Don't call the enter and exit hooks for non-inlined methods that:
 *    - do not contain field accesses (except private reads);
 *    - do not contain any synchronization (including being a synchronized method);
 *    - do not contain any method calls to inlined methods.
 *    
 *    The last one (no inlined method calls) is thwarted a bit by the current
 *    dynamic spec-reading scheme.  Moving to static spec-reading would allow us
 *    to take advantage of that, but for now we have to assume any method that
 *    does not already have an id could be inlined.  Hence:
 *    
 * 3. Do a static pass to build the spec, at least on the classes that are
 *    immediately accessible.  (We'll always need dynamic spec reading, etc. to
 *    handle reflection and dynamic class loading, but for most apps we'll be able
 *    to get most of what's interesting up front.)
 *    
 * 4. Use volatile instead of synchronized for state accesses (see optSharedRead and
 *    optSharedWrite, below). Probably a bad idea, b/c of race potential etc., but
 *    read up on that JMM... It also means that ICEs are not guaranteed to report
 *    *blame* correctly. (They are still validly illegal communication...)
 *    
 * 5. Store the currentThread in a local in each method. This way, Thread.currentThread()
 *    is called only once per method invocation. Then pass it as an argument to each
 *    hook that needs it.
 *    
 * 6. Inline the writerThread == currentThread check for reads. (i.e. do it outside the
 *    hook and only call the hook if needed.)
 *    
 * 7. Cache array and its array of states in ThreadState. Good for when running down an
 *    array; bad for when copying between/jumping around.
 *    
 * 8. Cache lock and lock state in ThreadState.
 * 
 * 9. When code is stable, customize and diversify array read/writer hooks.
 * 
 * 10. Shadow fields no longer need to be volatile now that State.writerThread is volatile
 *     and is always read last, written first, even in the constructor.  The double-checked 
 *     locking cannot expose uninitialized innards of a State because of this invariant.
 *     This approach increases the possibility of lost updates around conflicting "initial"
 *     writes, since the write of the State to the shadow field is no longer volatile (and
 *     may be delayed a bit longer/communicated a bit later as a result).  However, the only
 *     way to ensure no lost updates is to have a shadow lock as well that is acquired to
 *     initialize the shadow field (the shadow field must be volatile).  Same problem applies,
 *     though, since now you have to initialize the lock. (Get inside the constructors somehow?
 *     If you can do that, then just init the shadow field there and make it final... which
 *     leads to another thought.)
 *     
 * 11. If you can get inside constructors (even if not uniquely), just make the shadow fields
 *     non-final, but init them in the constructors.  If there are this() calls, no big deal.
 *     You'll just create an extra State or two and reassign.  To insure that things outside
 *     the constructor don't get null when they read the shadow field write to writerThread
 *     again after writing the State to the shadow field. For example:
 *       final State = new State(...);
 *       shadowField = state;
 *       state.writerThread = ...;
 *     CAVEAT: if this escapes in a constructor, you could be screwed and another thread might
 *     get a null from the shadow field. However, they're still guaranteed to see a fully
 *     initialized State if they do see one.
 *     You won't be able to get all constructors, I'm guessing, so for the ones you can't get, 
 *     keep the #10 style lazy initialization.
 *     
 * 12. Choose array granularity on some level other than "yes or no for all."  Asin coarse for
 *     some, fine for others.
 *     
 * 13. RuntimeMonitor.flush - a voltile static field available for where it's needed to sync?
 * 
 * 14. Create all possible States. Give each an ID. DUH!!!!!!! 
 * 
 * 15. Store thread id in a local at the beginning of each method and do the "same thread?"
 *     check in the bytecode stream before calling the hook.
 *    
 *    
 * TODO Things to fix or add.
 * 
 * + Copy (partial) of java.*
 * 
 * + Copy of acme.* (using our copy of java.*)
 * 
 * + Copy of asm.* (using our copy of java.*)
 * 
 * + Arrays
 * 
 * + command line options
 * 
 * + Graph recording (as annotation inference? i.e. insert annotations in the bytecode?)
 * 
 * + Static spec reading
 * 
 * + Separate tool/pass to take a spec in some other format and the bytecode of an
 *   un@annotated program and insert the proper annotations in the bytecode.
 *   
 * + Separate tool/pass to take annotated bytecode and build/dump a MethodRegistry.
 * 
 * + measure performance difference of how readerSet is loaded in hooks.
 * 
 * + @Default annotation on class tells default annotation to use on unannotated methods. 
 * 
 * + way to annotate clinit
 * 
 * + see javax.annotation.processing and apt if you want source-level...
 * 
 * + How we check for null when dereferencing fields and array shadows? Unless the null pointer
 *   exception comes from in the RuntimeMonitor, it will look like it came from user code
 *   (which it would have anyway if it was not instrumented).  In fact, we're functionally
 *   OK now, but could improve performance by changing order of things...
 *   1. All shadow field gets/puts are done outside the RuntimeMonitor, so they're OK.
 *   2. Array shadow lookups in reads will cause an unneeded hook,  but we're about to throw a
 *      NullPointerException anyway, so it's OK not to optimize. :-)  We do a null check for
 *      array writes only in the lazy initialization case (where it would muck up a reflection call to
 *      get the length of the array to allocate a shadow of the same length).
 *    
 * @author bpw
 */
public class RuntimeMonitor {

	private static final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>(); //{
//		@Override protected ThreadState initialValue() { return newThread(); }
//	};

	protected static final int MAX_THREADS = 32;

	// TODO must be resize if too many threads.
	// TODO each entry must be resized if MethodTable.policyTable gets resized.
	// TODO GC when a thread exits.
	private static int lastMethodTableSize = 0;
	private static final ThreadState[] threadTable = new ThreadState[MAX_THREADS];
	private static int maxThreadId = 0;

	// TODO fix WeakConcurrentIdentityHashMap and replace with that..
	// we need a concurrent hash map b/c access for multiple locks at once is not
	// protected by the app locks...
	protected static final ConcurrentIdentityHashMap<Object,LockState> lockStates = new ConcurrentIdentityHashMap<Object,LockState>();
	protected static final ConcurrentIdentityHashMap<Object,State[]> arrayStates = new ConcurrentIdentityHashMap<Object,State[]>();
	protected static final ConcurrentIdentityHashMap<Object,State> coarseArrayStates = new ConcurrentIdentityHashMap<Object,State>();

	// called lazily - from threadState's lazy initializer, so whenever this thread's
	// first action is.
	private static synchronized ThreadState newThread() {
		final ThreadState ts = new ThreadState(Thread.currentThread(), MethodTable.capacity());
		Util.assertTrue(ts.id < MAX_THREADS, "MAX_THREADS exceeded.");
		maxThreadId = ts.id;
		threadTable[ts.id] = ts;
		return ts;
	}
	
	public static synchronized void loadNewMethods() {
		final int first = lastMethodTableSize;
		lastMethodTableSize = MethodTable.size();
		for (int t = 0; t <= maxThreadId; t++) {
			final ThreadState ts = threadTable[t];
			if (ts != null) {
				ts.loadNewMethods(first, lastMethodTableSize);
			}
		}
	}
	
	/*******************************************************************/

	//	public static void newArray(int length, Object array) {
	//		final State[] states = new State[length];
	//		for (int i = 0; i < length; i++) {
	//			// initially, accept anything.
	//			states[i] = new State(null, -1, UniversalIntSet.set);
	//		}
	//		arrayStates.put(array, states);
	//	}

	//	public static void newMultiArray(final Object array, final int dims) {
	//		int length = Array.getLength(array);
	//		newArray(length, array);
	//		if (dims > 0) {
	//			for (int i = 0; i < length; i++) {
	//				newMultiArray(Array.get(array, i), dims - 1);
	//			}
	//		}
	//	}

	/**
	 * Checks a read by a shared reading method, i.e. a method that has in-edges
	 * and can read a write by the same thread or a write in one of the
	 * expectedReaders methods.
	 * 
	 * This method must be synchronized to prevent bad interleavings with
	 * sharedWrite or privateWrite. A cleverer solution using volatiles could 
	 * work too.
	 * 
	 * @param state
	 * @param readerMethod
	 */
	public static void read(final State write) {
		final ThreadState reader = threadState.get();
		if (write.writerThread != reader) {
			final IntSet readerSet = write.readerSet;
			if (readerSet == null || ! readerSet.contains(reader.currentMethod)) {
				throw new IllegalSharingException(write.writerThread, MethodTable.lookup(write.writerMethod), 
						reader, MethodTable.lookup(reader.currentMethod));
			}
		}
	}

	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void arrayRead(final Object array, final int index) {
		//		final ThreadState thread = threadState.get();
		//		final State state;
		//		if (array == thread.cachedArray) {
		//			state = thread.cachedArrayStates[index];
		//		} else {
		//			final State[] states = arrayStates.get(array);
		//			state = states[index];
		//			thread.cachedArray = array;
		//			thread.cachedArrayStates = states;
		//		}
		final State[] states;
		try {
			states = arrayStates.get(array);
		} catch (NullPointerException e) {
			throw fudgeTrace(e);
		}
		if (states != null) {
			final State write = states[index];
			if (write != null) {
				read(write);
			}
		}
	}
	// OK if array == null. Slower, but the program is about to throw a NullPointerException anyway.
	public static void coarseArrayRead(final Object array) {
		final State write;
		try {
			write = coarseArrayStates.get(array);
		} catch (NullPointerException e) {
			throw fudgeTrace(e);
		}
		if (write != null) {
			read(write);
		}
	}

	/**
	 * Updates the state to reflect a write by a method with no out-edges.
	 * 
	 * @param state
	 * @param writerMethod
	 */
	public static State write() {
		return threadState.get().currentState;
	}
	
	public static void arrayWrite(final Object array, int index) {
		State[] states = arrayStates.get(array);
		if (states == null) {
			// if array == null, we don't want to do anything more.
			// the user code will throw the NullPointerException.
			// by returning here, we also prevent the null key from
			// getting into arrayStates and causing false commmunication
			// ("collisions...")
			// Since this is the init case, we can afford to pay. ;-)
			if (array == null) {
				return;
			}
			states = new State[Array.getLength(array)];
			State[] old = arrayStates.putIfAbsent(array, states);
			if (old != null) {
				states = old;
			}
		}
		states[index] = threadState.get().currentState;
	}

	// DO NOT CALL ON PUBLIC WRITES (i.e. when currentstate is null)
	// concurrent hash map doesn't handle nulls, plus it's faster to
	// just skip it in the first place anyway.
	public static void coarseArrayWrite(final Object array) {
		coarseArrayStates.put(array, threadState.get().currentState);
	}


	/**
	 * Lock release hook.  Since things are well-scoped in Java, we'll just do all the work
	 * in acquire.  Only need to hit the reentrancy counter here.
	 * 
	 * INVARIANT: The method instrumentor relies on the fact that release() does NOT throw
	 * ANY EXCEPTIONS.  We ensure the exit and release hooks are called on method exit
	 * as needed by surrounding the body after the enter and acquire hooks with a try {}
	 * catch (Throwable t) {}, where the catch block calls release and exit...
	 * 
	 * @param lock
	 */
	public static void release(final Object lock) {
		try {
			final LockState state = lockStates.get(lock);
			if (state.depth <= 0) Util.fail("Bad lock scoping");
			state.depth--;
		} catch (Exception t) {
			Util.fail(t);
		}
	}

	/**
	 * Lock acquire hook.
	 * 
	 * TODO cache locks by thread.
	 * 
	 * @param nextMethods
	 * @param holderMethod
	 * @param lock
	 */
	public static void acquire(final Object lock) {
		try {
			LockState state = lockStates.get(lock);
			if (state == null) {
				state = new LockState(threadState.get().currentState);
				state.depth = 1;
				state = lockStates.putIfAbsent(lock, state);
				Util.assertTrue(state == null);
			} else if (state.depth < 0) {
				Util.fail("Bad lock scoping.");
			} else if (state.depth == 0) {
				// First (non-reentrant) acquire by this thread.
				// NOTE: this is atomic, because we hold lock and no other thread can call
				// the acquire or release hooks until they hold the lock.
				final ThreadState holder = threadState.get();
				final State holderState = holder.currentState; 
				final State lastHolderState = state.lastHolder;
				if (lastHolderState != holderState) {
					state.lastHolder = holderState;
					state.depth++;
					if (lastHolderState.writerThread != holder) {
						final IntSet readerSet = holderState.readerSet;
						if (readerSet == null || ! readerSet.contains(holderState.writerMethod)) {
							throw new IllegalSynchronizationException(
									lastHolderState.writerThread, MethodTable.lookup(lastHolderState.writerMethod), 
									holder, MethodTable.lookup(holder.currentMethod)
									// TODO refactor MethodTable to something like StateTable
									// lookup to something like getMethodName(State...)
							);
						}
					}
				} else {
					state.depth++;
				}
			} else {
				// if we're already reentrant, just go one deeper.
				state.depth++;
			}
		} catch (IllegalCommunicationException e) {
			throw e;
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	public static void enter(final int mid) {
		try {
			//			Util.logf("enter %d", mid);
			threadState.get().enter(mid);
		} catch (NullPointerException e) {
			final ThreadState ts = newThread();
			threadState.set(ts);
			ts.enter(mid);
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	public static void exit() {
		try {
			//			Util.log("exit");
			if (!threadState.get().exit()) threadState.set(null);
		} catch (Throwable t) {
			Util.fail(t);
		}
	}

	private static <T extends Throwable> T fudgeTrace(T t) {
		return t;
//		StackTraceElement[] stack = t.getStackTrace();
//		int i = 0;
//		while (i < stack.length && (stack[i].getClassName().startsWith(RuntimeMonitor.class.getPackage().getName())
//				|| stack[i].getClassName().startsWith("acme."))) {
//			i++;
//		}
//		if (i > 0) {
//			StackTraceElement[] fudgedStack = new StackTraceElement[stack.length - i];
//			System.arraycopy(stack, i, fudgedStack, 0, stack.length - i);
//			t.setStackTrace(fudgedStack);
//		}
//		return t;
	}

}
